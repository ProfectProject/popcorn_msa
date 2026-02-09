package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.client.TossPaymentsCoroutineClient
import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.dto.TossPaymentCancelRequest
import com.popcorn.payment.dto.TossPaymentConfirmRequest
import com.popcorn.payment.dto.TossPaymentConfirmResult
import com.popcorn.payment.dto.TossPaymentCancelResult
import com.popcorn.payment.dto.PaymentCreateResult
import com.popcorn.payment.event.publisher.BasePaymentEventPublisherImpl
import com.popcorn.payment.exception.PaymentException
import com.popcorn.payment.util.PaymentTokenUtil
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val httpOrderQueryService: HttpOrderQueryService,
    private val paymentTokenUtil: PaymentTokenUtil,
    private val paymentCacheService: PaymentCacheService  // 🚀 캐싱 서비스 추가
) {

    @Value("\${frontend.base-url:\${FRONTEND_BASE_URL:http://localhost:3000}}")
    private lateinit var frontendBaseUrl: String

    private val log = LoggerFactory.getLogger(TossPaymentCoroutineService::class.java)

    /**
     * 결제 URL 생성 (Order 서비스와 동일한 방식 - 토큰 기반)
     */
    suspend fun createPaymentRequest(
        orderId: String,
        amount: Int,
        orderName: String,
        customerKey: String
    ): PaymentCreateResult {
        log.info("💳 결제 URL 생성 요청: orderId={}, amount={}, orderName={}", orderId, amount, orderName)

        return try {
            // 토큰 기반 결제 URL 생성 (Order 서비스와 동일한 방식)
            val paymentUrl = generateTokenBasedPaymentUrl(orderId, orderName, amount, customerKey)

            PaymentCreateResult(
                paymentUrl = paymentUrl,
                orderId = orderId,
                amount = amount,
                expiresAt = java.time.LocalDateTime.now().plusMinutes(30)
            )
        } catch (e: Exception) {
            log.error("❌ 결제 URL 생성 실패: orderId={}, error={}", orderId, e.message, e)
            throw e
        }
    }

    /**
     * 토큰 기반 결제 URL 생성 (Order 서비스와 동일한 방식)
     */
    private fun generateTokenBasedPaymentUrl(
        orderId: String,
        orderName: String,
        amount: Int,
        customerKey: String
    ): String {
        try {
            // Order 번호 생성 (orderId가 이미 있다면 그대로 사용)
            val orderNo = "PAY-${System.currentTimeMillis()}"

            // JWT 토큰 생성 (Order 서비스와 동일한 방식)
            val paymentToken = paymentTokenUtil.generatePaymentToken(
                orderId = UUID.fromString(orderId),
                orderNo = orderNo,
                amount = amount,
                orderName = orderName,
                customerKey = customerKey,
                paymentMethod = "TOSS_PAYMENT"
            )

            // 프론트엔드 URL + 토큰으로 결제 URL 생성 (Order 서비스와 동일)
            val paymentUrl = "$frontendBaseUrl/auto-payment?token=$paymentToken"

            log.info("🚀 토큰 기반 결제 URL 생성 완료 - orderId: {}, 토큰 길이: {}자", orderId, paymentToken.length)
            return paymentUrl

        } catch (e: Exception) {
            log.error("❌ 토큰 기반 결제 URL 생성 실패 - orderId: {}, error: {}", orderId, e.message, e)
            throw RuntimeException("결제 URL 생성에 실패했습니다", e)
        }
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
            val startTime = System.currentTimeMillis()
            log.info("⚡ 토스 결제 승인 요청 시작: orderId={}, paymentKey={}, amount={}", orderId, paymentKey, amount)

            // 🎯 캐시 우선 확인 (초고속 응답)
            val cachedResult = paymentCacheService.getCachedPaymentResult(paymentKey)
            if (cachedResult != null) {
                val cacheTime = System.currentTimeMillis() - startTime
                log.info("🚀 캐시에서 결제 결과 반환 - paymentKey={}, 처리시간={}ms", paymentKey, cacheTime)
                return@coroutineScope cachedResult
            }

            val lockKey = buildConfirmLockKey(paymentKey)

            try {
                if (!tryAcquireConfirmLock(lockKey)) {
                    log.warn("🚫 결제 승인 중복 차단 - paymentKey={}, orderId={}", paymentKey, orderId)
                    throw PaymentException.duplicatePaymentAttempt()
                }

                // 1. 멱등성 체크 - 이미 처리된 결제인지 확인
                val existingPayment = checkIdempotency(paymentKey)
                if (existingPayment != null) {
                    log.info("💾 이미 처리된 결제 발견 - 캐시에 저장 후 반환: paymentId={}", existingPayment.paymentId)
                    // 캐시에 저장하여 다음 요청 시 더 빠른 응답
                    paymentCacheService.cachePaymentResult(paymentKey, existingPayment)
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

                // 🚀 성능 최적화: 결과를 캐시에 저장 (다음 요청 시 초고속 응답)
                paymentCacheService.cachePaymentResult(paymentKey, result)

                val totalTime = System.currentTimeMillis() - startTime
                log.info("🏆 토스 결제 승인 완료: paymentId={}, amount={}원, 총 처리시간={}ms",
                    result.paymentId, result.amount, totalTime)

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
     * 결제 전 검증 수행 (HTTP API 기반 - 타임아웃 문제 해결)
     * - HTTP API 직접 호출로 Redis Stream 타임아웃 문제 해결
     * - 사용자 주소 검증 (HybridValidationService 사용)
     * - 주문 상태 검증
     * - 성능 최적화: HTTP API 직접 조회로 안정성 향상
     *
     * 예상 성능 개선: 15초 타임아웃 → 2-5초 안정적 응답
     */
    private suspend fun performPrePaymentValidation(orderId: String, amount: Int) {
        try {
            log.debug("🔍 결제 전 검증 수행 (HTTP API 기반) - orderId: {}", orderId)

            // 1. HTTP API를 통한 직접 주문 정보 조회 (Redis Stream 타임아웃 문제 해결)
            val orderDetail = httpOrderQueryService.getOrderForPayment(UUID.fromString(orderId))

            // 🔍 Order 정보 직접 조회 결과 로깅
            log.info("🔍 [HTTP] Order 정보 조회 결과 - orderId: {}", orderId)
            if (orderDetail != null) {
                log.info("  ✅ 조회 성공")
                log.info("  - orderNo: {}", orderDetail.orderNo)
                log.info("  - customerId: {}", orderDetail.customerId)
                log.info("  - orderStatus: {}", orderDetail.status)
                log.info("  - totalAmount: {}", orderDetail.totalAmount)
            } else {
                log.error("  ❌ 조회 실패 - 주문 정보가 없거나 결제 불가 상태")
                throw PaymentException.validationFailed("주문 정보를 찾을 수 없거나 결제할 수 없는 상태입니다.")
            }

            // 2. 주문 상태 검증 - REQUESTED 상태도 결제 허용 (초기 주문 생성 후 즉시 결제 가능)
            if (orderDetail.status != "REQUESTED" && orderDetail.status != "RESERVED" && orderDetail.status != "PAYMENT_PENDING") {
                throw PaymentException.validationFailed("결제 가능한 주문 상태가 아닙니다. 현재 상태: ${orderDetail.status}")
            }


            // 🔍 HTTP API 호출 결과 검증 및 가격 비교 (mixed 주문 주소 검증 + Store API 가격 검증)
            val priceValidation = hybridValidationService.validateBasicPaymentRequest(
                orderId = UUID.fromString(orderId),
                userId = orderDetail.customerId,
                expectedAmount = amount,
                actualOrderAmount = orderDetail.totalAmount,
                hasGoods = orderDetail.hasGoods,  // ✅ mixed 주문에서 굿즈 있으면 주소 검증!
                orderDetail = orderDetail  // ✅ Store API 가격 검증용 lineItem 정보!
            )

            if (!priceValidation.isValid) {
                log.warn("❌ 기본 가격 검증 실패 - orderId: {}, expectedAmount: {}, actualAmount: {}",
                    orderId, amount, orderDetail.totalAmount)
                throw PaymentException.validationFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
            }

            log.info("✅ 결제 전 검증 완료 (HTTP API 호출) - orderId: {}, 빠른 성능 보장", orderId)

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

            // 3. Order 직접 DB 조회로 상세 검증 - 🚀 이벤트 기반 → 직접 쿼리로 변경
            val orderDetail = httpOrderQueryService.getOrderForPayment(order.id)

            if (orderDetail == null) {
                log.error("❌ 주문 정보 없음 또는 결제 불가 상태 - orderId: {}", orderId)
                throw PaymentException.validationFailed("주문 정보를 찾을 수 없거나 결제할 수 없는 상태입니다.")
            }

            // 🚀 기본 가격 검증 + mixed 주문 주소 검증 + Store API 가격 검증
            val validationResult = hybridValidationService.validateBasicPaymentRequest(
                orderId = UUID.fromString(orderId),
                userId = orderDetail.customerId,
                expectedAmount = amount,
                actualOrderAmount = orderDetail.totalAmount,
                hasGoods = orderDetail.hasGoods,  // ✅ mixed 주문에서 굿즈 있으면 주소 검증!
                orderDetail = orderDetail  // ✅ Store API 가격 검증용 lineItem 정보!
            )

            if (!validationResult.isValid) {
                throw PaymentException.validationFailed(
                    "결제 전 검증에 실패했습니다. (예상: ${validationResult.expectedAmount}원, 실제: ${validationResult.actualAmount}원)"
                )
            }

            log.info("✅ HTTP 기반 결제 전 검증 완료 - orderId: {}, 주소검증: {}, 가격검증: {}",
                     orderId, validationResult.addressValid, validationResult.priceValid)

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
