package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.client.TossPaymentsCoroutineClient
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.dto.TossPaymentCancelRequest
import com.popcorn.payment.dto.TossPaymentConfirmRequest
import com.popcorn.payment.event.publisher.BasePaymentEventPublisherImpl
import com.popcorn.payment.exception.PaymentException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.coroutines.Continuation


/**
 * 토스페이먼츠 결제 처리 서비스
 */
@Service
class TossPaymentCoroutineService(
    private val tossClient: TossPaymentsCoroutineClient,
    private val paymentCommandService: PaymentCommandCoroutineService,
    private val orderQueryService: OrderQueryCoroutineService,
    private val objectMapper: ObjectMapper,
    private val paymentEventPublisher: BasePaymentEventPublisherImpl,
    private val stringRedisTemplate: StringRedisTemplate,
    private val hybridValidationService: HybridValidationService,
    private val compensationService: CompensationService,
    private val paymentOrderInfoService: PaymentOrderInfoService
) {

    private val log = LoggerFactory.getLogger(TossPaymentCoroutineService::class.java)

    /**
     * 토스페이먼츠 결제 생성 (결제 URL 발급)
     */
    suspend fun createPaymentRequest(
        orderId: String,
        amount: Int,
        orderName: String,
        customerKey: String
    ): PaymentCreateResult {
        log.info("토스페이먼츠 결제 생성 요청: orderId={}, amount={}, orderName={}", orderId, amount, orderName)

        return try {
            // 토스페이먼츠 결제 위젯 URL 생성
            // 실제로는 토스페이먼츠 결제 생성 API를 호출해야 함
            val paymentUrl = generateTossPaymentWidgetUrl(orderId, amount, orderName, customerKey)

            PaymentCreateResult(
                paymentUrl = paymentUrl,
                orderId = orderId,
                amount = amount,
                expiresAt = java.time.LocalDateTime.now().plusMinutes(30)
            )
        } catch (e: Exception) {
            log.error("토스페이먼츠 결제 생성 실패: orderId={}, error={}", orderId, e.message, e)
            throw e
        }
    }

    /**
     * 토스페이먼츠 결제 위젯 URL 생성
     */
    private fun generateTossPaymentWidgetUrl(
        orderId: String,
        amount: Int,
        orderName: String,
        customerKey: String
    ): String {
        // 토스페이먼츠 결제 위젯 연동 방식
        // 실제로는 토스페이먼츠 SDK 또는 API를 통해 결제 URL을 받아야 함

        // 현재는 토스페이먼츠 결제 위젯 URL 형식으로 생성
        val baseUrl = "https://js.tosspayments.com/v1/payment"
        val params = "orderId=$orderId&amount=$amount&orderName=${java.net.URLEncoder.encode(orderName, "UTF-8")}&customerKey=$customerKey"

        return "$baseUrl?$params"
    }

    /**
     * 토스 결제 승인 처리 (이벤트 기반)
     */
    @CircuitBreaker(name = "tossPaymentApprove", fallbackMethod = "confirmPaymentFallback")
    suspend fun confirmPayment(
        paymentKey: String,
        orderId: String,
        amount: Int
    ): TossPaymentConfirmResult = coroutineScope {

            log.info("토스 결제 승인 요청 시작: orderId={}, paymentKey={}, amount={}", orderId, paymentKey, amount)
            val lockKey = buildConfirmLockKey(paymentKey)

            try {
                if (!tryAcquireConfirmLock(lockKey)) {
                    log.warn("🚫 결제 승인 중복 차단 - paymentKey={}, orderId={}", paymentKey, orderId)
                    throw PaymentException.duplicatePaymentAttempt()
                }

                // 1. 멱등성 체크 - 이미 처리된 결제인지 확인
                val existingPayment = checkIdempotency(paymentKey)
                if (existingPayment != null) {
                    log.info("이미 처리된 결제: paymentId={}", existingPayment.paymentId)
                    return@coroutineScope existingPayment
                }

                // 2. 결제 전 검증 수행 (Order service에서 이관된 로직)
                log.info("🔍 결제 전 검증 시작 - orderId: {}", orderId)
                try {
                    performPrePaymentValidation(orderId, amount)
                } catch (e: PaymentException) {
                    log.error("❌ 결제 전 검증 실패 - orderId: {}, error: {}", orderId, e.message, e)

                    // 🔄 검증 실패 시 보상 트랜잭션 실행
                    try {
                        handleValidationFailureCompensation(orderId, amount, e.message ?: "Unknown validation error")
                    } catch (compensationError: Exception) {
                        log.error("💥 검증 실패 보상 처리 실패 - orderId: {}, error: {}", orderId, compensationError.message)
                    }

                    throw e
                } catch (e: Exception) {
                    log.error("❌ 결제 전 검증 중 예외 발생 - orderId: {}, error: {}", orderId, e.message, e)

                    // 🔄 일반 예외도 보상 트랜잭션 처리
                    try {
                        handleValidationFailureCompensation(orderId, amount, "결제 전 검증 중 예외 발생: ${e.message}")
                    } catch (compensationError: Exception) {
                        log.error("💥 검증 예외 보상 처리 실패 - orderId: {}, error: {}", orderId, compensationError.message)
                    }

                    throw PaymentException.validationFailed("결제 전 검증에 실패했습니다: ${e.message}")
                }

                // 3. 토스페이먼츠 결제 승인 API 호출
                log.info("토스 결제 승인 API 호출: paymentKey={}", paymentKey)
                val tossResponse = tossClient.confirm(
                    TossPaymentConfirmRequest(
                        paymentKey = paymentKey,
                        orderId = orderId,
                        amount = amount
                    )
                )

                // 4. 응답 검증 및 파싱
                validateAmount(tossResponse.totalAmount, amount)
                val approvedAt = parseApprovedAt(tossResponse.approvedAt)
                val rawPayload = serializeResponse(tossResponse)

                // 5. Payment 서비스 내부 결제 기록 생성/업데이트
                log.info("결제 기록 생성: orderId={}, amount={}", orderId, amount)
                val createdPayment = paymentCommandService.createPaymentBlocking(
                    orderId = UUID.fromString(orderId),
                    paymentMethod = "CARD",
                    amount = amount,
                    paymentKey = paymentKey,
                    rawPayload = rawPayload
                )

                val paymentResult = paymentCommandService.updatePaymentStatusBlocking(
                    paymentId = createdPayment.paymentId,
                    status = "PAID",
                    approvedAt = approvedAt,
                    rawPayload = rawPayload
                )

                // 6. 결제 승인 이벤트 발행 (Order 서비스가 구독하여 주문 상태 업데이트)
                try {
                    // PaymentApprovedEvent 발행
                    paymentEventPublisher.publishPaymentApproved(
                        paymentId = paymentResult.paymentId,
                        orderId = UUID.fromString(orderId),
                        orderNo = orderId, // orderNo는 orderId와 동일하게 처리 (임시)
                        amount = amount,
                        paymentMethod = "CARD",
                        paymentKey = paymentKey,
                        approvedAt = approvedAt,
                        customerId = 1L // 기본값 (추후 결제 생성 시점에 저장된 값 사용)
                    )

                    // PaymentCompletedEvent 발행 (Order 서비스 호환용)
                    paymentEventPublisher.publishPaymentCompleted(
                        paymentId = paymentResult.paymentId,
                        orderId = UUID.fromString(orderId),
                        paymentKey = paymentKey,
                        amount = amount,
                        paymentMethod = "CARD",
                        pgResponse = rawPayload
                    )

                    log.info("✅ 결제 이벤트 발행 완료: orderId={}, amount={}원", orderId, amount)
                } catch (e: Exception) {
                    log.error("❌ 결제 이벤트 발행 실패 - 결제는 성공 처리됨: error={}", e.message, e)
                }

                // 7. 결과 반환 (Order 상태는 이벤트를 통해 비동기로 업데이트됨)
                val result = TossPaymentConfirmResult(
                    paymentId = paymentResult.paymentId,
                    paymentStatus = "PAID",
                    orderStatus = EventConstants.EventTypes.PaymentDomain.PAYMENT_SUCCESS, // Order 서비스에서 이벤트 구독 후 실제 상태로 업데이트
                    orderId = UUID.fromString(orderId),
                    orderNo = orderId,
                    amount = amount,
                    approvedAt = approvedAt
                )

                log.info("✅ 토스 결제 승인 완료: paymentId={}, amount={}원",
                    result.paymentId, result.amount)

                result

            } catch (e: Exception) {
                log.error("❌ 결제 승인 실패: paymentKey={}, orderId={}, error={}", paymentKey, orderId, e.message, e)

                // 🔄 결제 승인 실패 시 보상 트랜잭션 처리
                try {
                    // 이미 생성된 결제 정보가 있는지 확인
                    val existingPayments = paymentCommandService.findByPaymentKey(paymentKey)
                    val paymentId = existingPayments.firstOrNull()?.paymentId

                    if (paymentId != null) {
                        // 결제 ID가 있는 경우 결제 실패 보상 처리
                        handlePaymentFailureCompensation(
                            paymentId = paymentId,
                            orderId = orderId,
                            amount = amount,
                            failureReason = e.message ?: "Payment approval failed",
                            failureStage = "APPROVAL"
                        )
                    } else {
                        // 결제 ID가 없는 경우 검증 실패로 처리
                        handleValidationFailureCompensation(
                            orderId = orderId,
                            amount = amount,
                            failureReason = "Payment approval failed: ${e.message}"
                        )
                    }
                } catch (compensationError: Exception) {
                    log.error("💥 결제 승인 실패 보상 처리 실패 - orderId: {}, error: {}",
                             orderId, compensationError.message)
                }

                throw e
            } finally {
                releaseConfirmLock(lockKey)
            }
    }

    private fun buildConfirmLockKey(paymentKey: String): String {
        return "payment:confirm:lock:$paymentKey"
    }

    private fun tryAcquireConfirmLock(lockKey: String): Boolean {
        return try {
            stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(5)) == true
        } catch (e: Exception) {
            log.warn("결제 승인 락 획득 실패 - key={}, error={}", lockKey, e.message)
            true // Redis 문제 시 결제 흐름은 진행
        }
    }

    private fun releaseConfirmLock(lockKey: String) {
        try {
            stringRedisTemplate.delete(lockKey)
        } catch (e: Exception) {
            log.warn("결제 승인 락 해제 실패 - key={}, error={}", lockKey, e.message)
        }
    }

    /**
     * 토스 결제 취소 처리 (코루틴 버전)
     *
     * @param orderId 주문 ID
     * @param cancelReason 취소 사유
     * @return 결제 취소 결과
     */
    @CircuitBreaker(name = "tossPaymentCancel", fallbackMethod = "cancelPaymentFallback")
    suspend fun cancelPayment(
        orderId: UUID,
        cancelReason: String
    ): TossPaymentCancelResult {

        log.info("🔄 토스 결제 취소 요청: orderId={}, cancelReason={}", orderId, cancelReason)

        // Step 1: 주문 및 결제 정보 조회
        val order = orderQueryService.getOrder(orderId)
        log.debug("주문 확인 완료: orderId={}, orderNo={}, status={}", order.id, order.orderNo, order.status)
        val payment = paymentCommandService.getLatestPaymentByOrderId(orderId)

        if (payment.status != "PAID") {
            throw PaymentException.invalidStatusTransition("결제 완료 상태가 아닙니다: ${payment.status}")
        }

        val paymentKey = payment.paymentKey
            ?: extractPaymentKeyFromRawPayload(payment.rawPayload)
            ?: throw PaymentException.invalidRequest("결제 키를 찾을 수 없습니다")

        // Step 2: 토스 결제 취소 API 호출
        val cancelResponse = tossClient.cancel(
            paymentKey = paymentKey,
            request = TossPaymentCancelRequest(
                cancelReason = cancelReason
            )
        )

        // Step 3: 결제 상태 업데이트
        paymentCommandService.updatePaymentStatus(
            paymentId = payment.paymentId,
            status = EventConstants.EventStatus.CANCELLED,
            rawPayload = serializeResponse(cancelResponse)
        )

        val result = TossPaymentCancelResult(
            paymentId = payment.paymentId,
            orderId = orderId,
            cancelAmount = cancelResponse.totalAmount,
            status = cancelResponse.status,
            cancelReason = cancelReason
        )

        log.info("✅ 토스 결제 취소 완료: paymentId={}, cancelAmount={}원",
            result.paymentId, result.cancelAmount)

        return result
    }

    @Suppress("unused")
    suspend fun confirmPaymentFallback(
        paymentKey: String,
        orderId: String,
        amount: Int,
        throwable: Throwable
    ): TossPaymentConfirmResult {
        log.error("🚨 토스 결제 승인 CircuitBreaker OPEN - orderId={}, error={}", orderId, throwable.message, throwable)
        throw PaymentException.externalApiError("토스 결제 승인 실패(서킷 브레이커): ${throwable.message}")
    }

    @Suppress("unused")
    fun confirmPaymentFallback(
        paymentKey: String,
        orderId: String,
        amount: Int,
        continuation: Continuation<*>,
        throwable: Throwable
    ): Any {
        log.error("🚨 토스 결제 승인 CircuitBreaker OPEN - orderId={}, error={}", orderId, throwable.message, throwable)
        throw PaymentException.externalApiError("토스 결제 승인 실패(서킷 브레이커): ${throwable.message}")
    }

    @Suppress("unused")
    suspend fun cancelPaymentFallback(
        orderId: UUID,
        cancelReason: String,
        throwable: Throwable
    ): TossPaymentCancelResult {
        log.error("🚨 토스 결제 취소 CircuitBreaker OPEN - orderId={}, error={}", orderId, throwable.message, throwable)
        throw PaymentException.externalApiError("토스 결제 취소 실패(서킷 브레이커): ${throwable.message}")
    }

    @Suppress("unused")
    fun cancelPaymentFallback(
        orderId: UUID,
        cancelReason: String,
        continuation: Continuation<*>,
        throwable: Throwable
    ): Any {
        log.error("🚨 토스 결제 취소 CircuitBreaker OPEN - orderId={}, error={}", orderId, throwable.message, throwable)
        throw PaymentException.externalApiError("토스 결제 취소 실패(서킷 브레이커): ${throwable.message}")
    }

    /**
     * 멱등성 체크 - paymentKey 기반 중복 결제 확인 (이벤트 기반)
     *
     * @param paymentKey 토스 결제 키
     * @return 기존 결제 정보 또는 null
     */
    private suspend fun checkIdempotency(
        paymentKey: String
    ): TossPaymentConfirmResult? {

        // PaymentKey로 기존 결제 조회 (Payment 서비스 내부 데이터만 사용)
        val existingPayments = paymentCommandService.findByPaymentKey(paymentKey)

        return if (existingPayments.isNotEmpty()) {
            val existingPayment = existingPayments.first()

            TossPaymentConfirmResult(
                paymentId = existingPayment.paymentId,
                paymentStatus = existingPayment.status,
                orderStatus = "COMPLETED", // 이벤트 기반이므로 Order 상태는 추정값
                orderId = existingPayment.orderId ?: UUID.randomUUID(),
                orderNo = existingPayment.orderId?.toString() ?: "unknown",
                amount = existingPayment.amount,
                approvedAt = existingPayment.approvedAt ?: LocalDateTime.now()
            )
        } else {
            null
        }
    }

    private fun validateAmount(tossAmount: Int, requestAmount: Int) {
        if (tossAmount != requestAmount) {
            throw PaymentException.amountMismatch("결제 금액 불일치: 요청=$requestAmount, 토스=$tossAmount")
        }
    }

    private fun parseApprovedAt(approvedAt: String?): LocalDateTime {
        return if (approvedAt.isNullOrBlank()) {
            LocalDateTime.now()
        } else {
            try {
                OffsetDateTime.parse(approvedAt).toLocalDateTime()
            } catch (e: Exception) {
                log.warn("승인 시간 파싱 실패, 현재 시간 사용: approvedAt={}", approvedAt)
                LocalDateTime.now()
            }
        }
    }

    private fun serializeResponse(response: Any): String {
        return try {
            objectMapper.writeValueAsString(response)
        } catch (e: Exception) {
            log.error("응답 직렬화 실패", e)
            throw PaymentException.invalidRequest("응답 직렬화 실패")
        }
    }

    private fun extractPaymentKeyFromRawPayload(rawPayload: String?): String? {
        if (rawPayload.isNullOrBlank()) return null

        return try {
            val node = objectMapper.readTree(rawPayload)
            node["paymentKey"]?.asText()
        } catch (e: Exception) {
            log.warn("결제 키 추출 실패: rawPayload={}", rawPayload, e)
            null
        }
    }

    /**
     * 결제 전 검증 수행 (Hybrid Validation 적용)
     * - 사용자 주소 검증 (HybridValidationService 사용)
     * - 주문 상태 검증
     * - 성능 최적화: DB 직접 조회 우선, HTTP fallback
     *
     * 예상 성능 개선: 200-500ms → 10-50ms
     */
    private suspend fun performPrePaymentValidation(orderId: String, amount: Int) {
        try {
            log.debug("🔍 결제 전 검증 수행 (Hybrid 방식) - orderId: {}", orderId)

            // 1. 주문 정보 조회
            val order = orderQueryService.getOrder(UUID.fromString(orderId))

            // 2. 주문 상태 검증 - REQUESTED 상태도 결제 허용 (초기 주문 생성 후 즉시 결제 가능)
            if (order.status != "REQUESTED" && order.status != "RESERVED" && order.status != "PAYMENT_PENDING") {
                throw PaymentException.validationFailed("결제 가능한 주문 상태가 아닙니다. 현재 상태: ${order.status}")
            }

            // 3. Order 이벤트 기반 상세 검증 (주소 + 가격) - 🚀 타이밍 이슈 해결
            val orderInfoFuture = paymentOrderInfoService.requestOrderInfo(order.id, timeoutMs = 5000)

            // 🚀 CompletableFuture를 코루틴 방식으로 안전하게 처리
            val orderInfo = try {
                orderInfoFuture.await()
            } catch (e: Exception) {
                log.warn("⚠️ Order 정보 응답 대기 중 예외 발생 - orderId: {}, error: {}", orderId, e.message)
                null
            }

            // 🔍 Order 정보 응답 상세 로깅
            log.info("🔍 [DEBUG] Order 정보 응답 분석 - orderId: {}", orderId)
            log.info("  - success: {}", orderInfo?.success)
            log.info("  - errorMessage: {}", orderInfo?.errorMessage)
            log.info("  - actualLines count: {}", orderInfo?.actualLines?.size ?: 0)
            log.info("  - customerId: {}", orderInfo?.customerId)
            log.info("  - orderStatus: {}", orderInfo?.orderStatus)
            log.info("  - totalAmount: {}", orderInfo?.totalAmount)

            if (orderInfo?.actualLines != null && orderInfo.actualLines.isNotEmpty()) {
                orderInfo.actualLines.forEachIndexed { index, line ->
                    log.info("  - line[{}]: itemType={}, qty={}, unitPrice={}, linePrice={}, scheduleId={}, goodsId={}",
                        index, line.itemType, line.qty, line.unitPrice, line.linePrice, line.scheduleId, line.goodsId)
                }
            }

            if (orderInfo?.success != true) {
                log.warn("❌ Order 정보 응답 실패 - orderId: {}, success: {}, errorMessage: {}",
                    orderId, orderInfo?.success, orderInfo?.errorMessage)
                throw PaymentException.validationFailed("주문 정보 응답이 실패했습니다: ${orderInfo?.errorMessage ?: "알 수 없는 오류"}")
            }

            if (orderInfo.actualLines.isNullOrEmpty()) {
                log.warn("❌ Order 라인 아이템 누락 - orderId: {}, actualLines: {}", orderId, orderInfo.actualLines)
                throw PaymentException.validationFailed("주문 라인 아이템 정보가 없습니다. 주문 데이터를 확인해주세요.")
            }

            // 🚀 actualLines는 이미 EventLineItem 타입이므로 직접 사용
            val validationResult = hybridValidationService.validatePaymentRequestFromOrderEvent(
                userId = order.customerId,
                lines = orderInfo.actualLines,
                expectedAmount = amount
            )

            if (!validationResult.isValid) {
                throw PaymentException.validationFailed("결제 전 검증에 실패했습니다.")
            }

            log.info("✅ 결제 전 검증 완료 (Hybrid 방식) - orderId: {}, 성능 개선됨", orderId)

        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            log.error("❌ 결제 전 검증 중 예외 발생 - orderId: {}, error: {}", orderId, e.message, e)
            throw PaymentException.validationFailed("결제 전 검증에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 향상된 결제 전 검증 (추후 확장용)
     * Order Item 정보가 있는 경우 더 정교한 가격 검증 수행
     *
     * 현재는 기본 검증을 사용하지만, 향후 Order 이벤트에 상품 정보가 포함되면
     * 이 메서드를 사용하여 더 상세한 검증을 수행할 수 있음
     */
    private suspend fun performEnhancedPrePaymentValidation(
        orderId: String,
        amount: Int
    ) {
        try {
            log.debug("🔍 향상된 결제 전 검증 수행 (Hybrid 방식) - orderId: {}", orderId)

            // 1. 주문 정보 조회
            val order = orderQueryService.getOrder(UUID.fromString(orderId))

            // 2. 주문 상태 검증 - REQUESTED 상태도 결제 허용 (초기 주문 생성 후 즉시 결제 가능)
            if (order.status != "REQUESTED" && order.status != "RESERVED" && order.status != "PAYMENT_PENDING") {
                throw PaymentException.validationFailed("결제 가능한 주문 상태가 아닙니다. 현재 상태: ${order.status}")
            }

            // 3. Order 이벤트 기반 상세 검증 (주소 + 가격) - DB 직접 조회만 사용
            val orderInfoFuture = paymentOrderInfoService.requestOrderInfo(order.id)
            val orderInfo = orderInfoFuture.get()

            if (orderInfo?.success != true || orderInfo.actualLines.isNullOrEmpty()) {
                throw PaymentException.validationFailed("주문 정보 응답이 없습니다. 다시 시도해주세요.")
            }

            // 🚀 actualLines는 이미 EventLineItem 타입이므로 직접 사용
            val validationResult = hybridValidationService.validatePaymentRequestFromOrderEvent(
                userId = order.customerId,
                lines = orderInfo.actualLines,
                expectedAmount = amount
            )

            if (!validationResult.isValid) {
                throw PaymentException.validationFailed(
                    "결제 전 검증에 실패했습니다. (예상: ${validationResult.expectedAmount}원, 실제: ${validationResult.totalCalculatedPrice}원)"
                )
            }

            log.info("✅ 향상된 결제 전 검증 완료 - orderId: {}, 처리시간: {}ms",
                     orderId, validationResult.processingTimeMs)

        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            log.error("❌ 향상된 결제 전 검증 중 예외 발생 - orderId: {}, error: {}", orderId, e.message, e)
            throw PaymentException.validationFailed("결제 전 검증에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 현재 요청의 Authorization 헤더 추출
     * TODO: 실제 구현에서는 Spring Security Context나 요청 컨텍스트에서 추출
     */
    private fun getCurrentAuthorizationHeader(): String? {
        // 현재는 임시로 null 반환, 실제로는 RequestContextHolder 등을 사용하여 추출
        return null
    }

    /**
     * 현재 요청의 X-Passport 헤더 추출
     * TODO: 실제 구현에서는 Spring Security Context나 요청 컨텍스트에서 추출
     */
    private fun getCurrentPassportHeader(): String? {
        // 현재는 임시로 null 반환, 실제로는 RequestContextHolder 등을 사용하여 추출
        return null
    }

    /**
     * 검증 실패에 대한 보상 트랜잭션 처리
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param failureReason 실패 사유
     */
    private suspend fun handleValidationFailureCompensation(
        orderId: String,
        amount: Int,
        failureReason: String
    ) {
        try {
            log.info("🔄 검증 실패 보상 트랜잭션 시작 - orderId: {}, reason: {}", orderId, failureReason)

            // 검증 실패 타입 분석
            val validationType = when {
                failureReason.contains("주소") || failureReason.contains("배송지") -> "ADDRESS"
                failureReason.contains("가격") || failureReason.contains("금액") -> "PRICE"
                failureReason.contains("상태") || failureReason.contains("status") -> "ORDER_STATUS"
                else -> "GENERAL"
            }

            // 보상 서비스를 통한 처리
            compensationService.handleValidationFailureCompensation(
                orderId = UUID.fromString(orderId),
                validationType = validationType,
                failureReason = failureReason,
                amount = amount,
                paymentId = null, // 결제가 시작되기 전 실패
                userId = getCurrentUserId()
            )

            log.info("✅ 검증 실패 보상 트랜잭션 완료 - orderId: {}", orderId)

        } catch (e: Exception) {
            // 보상 트랜잭션도 실패한 경우 - 매우 심각한 상황
            log.error("💥 검증 실패 보상 트랜잭션 실패 - orderId: {}, error: {}", orderId, e.message, e)

            // 실패 기록 및 알림 (별도 처리)
            recordCriticalCompensationFailure(orderId, "VALIDATION", failureReason, e)
        }
    }

    /**
     * 결제 처리 실패에 대한 보상 트랜잭션 처리
     *
     * @param paymentId 결제 ID
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param failureReason 실패 사유
     * @param failureStage 실패 단계
     */
    private suspend fun handlePaymentFailureCompensation(
        paymentId: UUID,
        orderId: String,
        amount: Int,
        failureReason: String,
        failureStage: String = "APPROVAL"
    ) {
        try {
            log.info("🔄 결제 실패 보상 트랜잭션 시작 - paymentId: {}, orderId: {}, stage: {}",
                     paymentId, orderId, failureStage)

            compensationService.handlePaymentFailureCompensation(
                paymentId = paymentId,
                orderId = UUID.fromString(orderId),
                failureReason = failureReason,
                failureStage = failureStage,
                amount = amount,
                userId = getCurrentUserId()
            )

            log.info("✅ 결제 실패 보상 트랜잭션 완료 - paymentId: {}, orderId: {}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("💥 결제 실패 보상 트랜잭션 실패 - paymentId: {}, orderId: {}, error: {}",
                     paymentId, orderId, e.message, e)

            recordCriticalCompensationFailure(orderId, "PAYMENT", failureReason, e)
        }
    }

    /**
     * 현재 사용자 ID 추출
     * TODO: 실제 구현에서는 Spring Security Context에서 추출
     */
    private fun getCurrentUserId(): Long? {
        // 현재는 임시로 null 반환, 실제로는 SecurityContextHolder.getContext() 등을 사용
        return null
    }

    /**
     * 심각한 보상 실패 기록
     */
    private fun recordCriticalCompensationFailure(
        orderId: String,
        failureType: String,
        originalFailureReason: String,
        compensationError: Exception
    ) {
        try {
            val failureRecord = mapOf(
                "orderId" to orderId,
                "failureType" to failureType,
                "originalFailureReason" to originalFailureReason,
                "compensationError" to compensationError.message,
                EventConstants.MetadataKeys.TIMESTAMP to java.time.LocalDateTime.now().toString(),
                "severity" to "CRITICAL"
            )

            val recordKey = "compensation:critical_failure:$orderId"
            stringRedisTemplate.opsForHash<String, String>()
                .putAll(recordKey, failureRecord.mapValues { it.value.toString() })
            stringRedisTemplate.expire(recordKey, java.time.Duration.ofDays(30))

            log.error("🚨 심각한 보상 실패 기록 저장 - orderId: {}, 수동 개입 필요", orderId)

            // TODO: 관리자 긴급 알림 발송
            // sendCriticalAlert(orderId, failureType, originalFailureReason, compensationError)

        } catch (recordError: Exception) {
            log.error("💀 보상 실패 기록도 실패 - orderId: {}, error: {}", orderId, recordError.message)
        }
    }

}

/**
 * 토스 결제 승인 결과 DTO
 */
data class TossPaymentConfirmResult(
    val paymentId: UUID,
    val paymentStatus: String,
    val orderStatus: String,
    val orderId: UUID,
    val orderNo: String,
    val amount: Int,
    val approvedAt: LocalDateTime
)

/**
 * 토스 결제 취소 결과 DTO
 */
data class TossPaymentCancelResult(
    val paymentId: UUID,
    val orderId: UUID,
    val cancelAmount: Int,
    val status: String,
    val cancelReason: String
)

/**
 * 토스 결제 생성 결과 DTO
 */
data class PaymentCreateResult(
    val paymentUrl: String,
    val orderId: String,
    val amount: Int,
    val expiresAt: java.time.LocalDateTime
)
