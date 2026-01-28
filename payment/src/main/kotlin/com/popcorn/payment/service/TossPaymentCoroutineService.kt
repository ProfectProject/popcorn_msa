package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.client.TossPaymentsCoroutineClient
import com.popcorn.payment.config.CoroutineTransactionManager
import com.popcorn.payment.dto.TossPaymentCancelRequest
import com.popcorn.payment.dto.TossPaymentConfirmRequest
import com.popcorn.payment.event.PaymentEventPublisherImpl
import com.popcorn.payment.exception.PaymentException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val transactionManager: CoroutineTransactionManager,
    private val paymentCommandService: PaymentCommandCoroutineService,
    private val orderQueryService: OrderQueryCoroutineService,
    private val objectMapper: ObjectMapper,
    private val paymentEventPublisher: PaymentEventPublisherImpl,
    private val stringRedisTemplate: StringRedisTemplate
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

                // 2. 토스페이먼츠 결제 승인 API 호출
                log.info("토스 결제 승인 API 호출: paymentKey={}", paymentKey)
                val tossResponse = tossClient.confirm(
                    TossPaymentConfirmRequest(
                        paymentKey = paymentKey,
                        orderId = orderId,
                        amount = amount
                    )
                )

                // 3. 응답 검증 및 파싱
                validateAmount(tossResponse.totalAmount, amount)
                val approvedAt = parseApprovedAt(tossResponse.approvedAt)
                val rawPayload = serializeResponse(tossResponse)

                // 4. Payment 서비스 내부 결제 기록 생성/업데이트
                log.info("결제 기록 생성: orderId={}, amount={}", orderId, amount)
                val paymentResult = transactionManager.executeInTransactionSuspend {
                    val createdPayment = paymentCommandService.createPaymentBlocking(
                        orderId = UUID.fromString(orderId),
                        paymentMethod = "CARD",
                        amount = amount,
                        paymentKey = paymentKey,
                        rawPayload = rawPayload
                    )

                    paymentCommandService.updatePaymentStatusBlocking(
                        paymentId = createdPayment.paymentId,
                        status = "PAID",
                        approvedAt = approvedAt,
                        rawPayload = rawPayload
                    )

                    createdPayment
                }

                // 5. 결제 승인 이벤트 발행 (Order 서비스가 구독하여 주문 상태 업데이트)
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

                // 6. 결과 반환 (Order 상태는 이벤트를 통해 비동기로 업데이트됨)
                val result = TossPaymentConfirmResult(
                    paymentId = paymentResult.paymentId,
                    paymentStatus = "PAID",
                    orderStatus = "PAYMENT_COMPLETED", // Order 서비스에서 이벤트 구독 후 실제 상태로 업데이트
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
            status = "CANCELLED",
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
