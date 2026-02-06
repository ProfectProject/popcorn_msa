package com.popcorn.payment.service

import com.popcorn.payment.config.TransactionalOperation
import com.popcorn.payment.entity.Payment
import com.popcorn.payment.entity.PaymentMethod
import com.popcorn.payment.entity.PaymentStatus
import com.popcorn.payment.exception.PaymentException
import com.popcorn.payment.repository.PaymentRepository
import com.popcorn.payment.event.base.BasePaymentEventPublisher
import com.popcorn.payment.event.domain.payment.PaymentCreatedEvent
import com.popcorn.payment.event.domain.payment.PaymentApprovedEvent
import com.popcorn.payment.event.domain.payment.EventLineItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 결제 명령 처리 서비스 (코루틴 버전)
 *
 * 🔄 JPA + 코루틴 전략:
 * - 기존 JPA Repository를 그대로 사용
 * - withContext(Dispatchers.IO)로 블로킹 작업 격리
 * - 코루틴 컨텍스트에서 안전한 트랜잭션 관리
 *
 * 💡 왜 withContext(Dispatchers.IO)?
 * - JPA는 JDBC 기반의 블로킹 I/O
 * - 별도 스레드 풀에서 실행하여 메인 이벤트 루프 보호
 * - 메인 코루틴 컨텍스트를 블로킹하지 않음
 */
@Service
class PaymentCommandCoroutineService(
    private val paymentRepository: PaymentRepository,
    private val paymentEventPublisher: BasePaymentEventPublisher,
    private val paymentOrderInfoService: PaymentOrderInfoService
) {

    private val log = LoggerFactory.getLogger(PaymentCommandCoroutineService::class.java)

    /**
     * 새로운 결제 기록 생성
     *
     * 🎯 코루틴 최적화:
     * - DB 작업은 IO 스레드에서 실행
     * - 트랜잭션 경계 명시적 관리
     * - 결제 정보 검증 로직 포함
     *
     * 💾 저장 정보:
     * - 주문 ID 연결
     * - 결제 수단 (CARD, TRANSFER, VIRTUAL_ACCOUNT 등)
     * - 결제 금액
     * - 생성 시간
     * - 승인 시간
     * - 결제 수단 정보
     *
     * 🔒 트랜잭션 관리:
     * - @Transactional이 클래스 레벨에 적용되어 자동 관리
     * - 코루틴에서도 동일한 트랜잭션 컨텍스트 유지
     */
    @Transactional
    suspend fun createPayment(
        orderId: UUID,
        paymentMethod: String,
        amount: Int,
        paymentKey: String? = null,
        rawPayload: String
    ): PaymentCreationResult {
        return createPaymentBlocking(orderId, paymentMethod, amount, paymentKey, rawPayload)
    }

    internal fun createPaymentBlocking(
        orderId: UUID,
        paymentMethod: String,
        amount: Int,
        paymentKey: String? = null,
        rawPayload: String
    ): PaymentCreationResult {
        log.info("💳 새 결제 기록 생성: orderId={}, method={}, amount={}원", orderId, paymentMethod, amount)

        // 입력 값 검증
        validatePaymentCreation(paymentMethod, amount)

        // Payment 엔티티 생성
        val payment = Payment.create(
            orderId = orderId,
            paymentMethod = PaymentMethod.valueOf(paymentMethod),
            amount = amount,
            paymentKey = paymentKey,
            rawPayload = rawPayload
        )

        // 데이터베이스 저장 (멱등성 키 중복 시 기존 결제 반환)
        val savedPayment = try {
            paymentRepository.save(payment)
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            if (paymentKey.isNullOrBlank()) {
                throw e
            }
            paymentRepository
                .findByPaymentKeyAndDeletedAtIsNullOrderByCreatedAtDesc(paymentKey)
                .firstOrNull()
                ?: throw e
        }

        log.info("✅ 결제 기록 생성 완료: paymentId={}, status={}",
            savedPayment.id, savedPayment.status)

        // 🚀 표준 PAYMENT_CREATED 이벤트 발행
        // 이벤트 발행을 별도 코루틴에서 수행
        CoroutineScope(Dispatchers.Default).launch {
            publishPaymentCreatedEvent(savedPayment)
        }

        return PaymentCreationResult(
            paymentId = savedPayment.id,
            status = savedPayment.status.name,
            amount = savedPayment.amount,
            createdAt = savedPayment.createdAt
        )
    }

    /**
     * 결제 상태 업데이트
     *
     * @param paymentId 결제 ID
     * @param status 새로운 상태
     * @param approvedAt 승인 시간 (선택)
     * @param rawPayload 토스 응답 데이터 (선택)
     */
    @Transactional
    suspend fun updatePaymentStatus(
        paymentId: UUID,
        status: String,
        approvedAt: LocalDateTime? = null,
        rawPayload: String? = null
    ): PaymentDetailResult {
        return updatePaymentStatusBlocking(paymentId, status, approvedAt, rawPayload)
    }

    internal fun updatePaymentStatusBlocking(
        paymentId: UUID,
        status: String,
        approvedAt: LocalDateTime? = null,
        rawPayload: String? = null
    ): PaymentDetailResult {
        log.info("🔄 결제 상태 업데이트: paymentId={}, status={}", paymentId, status)

        // 결제 정보 조회
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentException.paymentNotFound() }

        val newStatus = PaymentStatus.valueOf(status)
        val resolvedApprovedAt = when {
            newStatus == PaymentStatus.PAID && payment.approvedAt != null -> payment.approvedAt
            newStatus == PaymentStatus.PAID -> approvedAt ?: LocalDateTime.now()
            else -> approvedAt
        }

        // 상태 업데이트
        payment.updateStatus(newStatus, resolvedApprovedAt)

        // rawPayload 업데이트 (있는 경우)
        if (rawPayload != null) {
            payment.rawPayload = rawPayload
        }

        // 저장
        val savedPayment = paymentRepository.save(payment)

        log.info("✅ 결제 상태 업데이트 완료: paymentId={}, newStatus={}",
            savedPayment.id, savedPayment.status)

        // 🚀 결제 승인 시 표준 PAYMENT_APPROVED 이벤트 발행
        if (savedPayment.status == PaymentStatus.PAID) {
            // 이벤트 발행을 별도 코루틴에서 수행
            CoroutineScope(Dispatchers.Default).launch {
                publishPaymentApprovedEvent(savedPayment)
            }
        }

        return PaymentDetailResult(
            paymentId = savedPayment.id,
            orderId = savedPayment.orderId,
            paymentKey = savedPayment.paymentKey,
            status = savedPayment.status.name,
            amount = savedPayment.amount,
            approvedAt = savedPayment.approvedAt,
            rawPayload = savedPayment.rawPayload
        )
    }

    /**
     * PaymentKey로 기존 결제 조회
     *
     * @param paymentKey 토스 결제 키
     * @return 기존 결제 목록
     */
    @Transactional(readOnly = true)
    suspend fun findByPaymentKey(paymentKey: String): List<PaymentDetailResult> {
        return paymentRepository.findByPaymentKeyAndDeletedAtIsNullOrderByCreatedAtDesc(paymentKey)
            .map { payment ->
                PaymentDetailResult(
                    paymentId = payment.id,
                    orderId = payment.orderId,
                    paymentKey = payment.paymentKey,
                    status = payment.status.name,
                    amount = payment.amount,
                    approvedAt = payment.approvedAt,
                    rawPayload = payment.rawPayload
                )
            }
    }

    /**
     * 주문 ID로 가장 최신 결제 조회
     *
     * @param orderId 주문 ID
     * @return 최신 결제 정보
     */
    @Transactional(readOnly = true)
    suspend fun getLatestPaymentByOrderId(orderId: UUID): PaymentDetailResult {
        val payment = paymentRepository.findFirstByOrderIdAndDeletedAtIsNullOrderByCreatedAtDesc(orderId)
            ?: throw PaymentException.paymentNotFound()

        return PaymentDetailResult(
            paymentId = payment.id,
            orderId = payment.orderId,
            paymentKey = payment.paymentKey,
            status = payment.status.name,
            amount = payment.amount,
            approvedAt = payment.approvedAt,
            rawPayload = payment.rawPayload
        )
    }

    /**
     * 결제 생성 입력값 검증
     */
    private fun validatePaymentCreation(paymentMethod: String, amount: Int) {
        if (amount <= 0) {
            throw PaymentException.invalidRequest("결제 금액은 0보다 커야 합니다: $amount")
        }

        if (paymentMethod.isBlank()) {
            throw PaymentException.invalidRequest("결제 수단이 필요합니다")
        }

        // 추가 검증 로직
        val validMethods = setOf("CARD", "TRANSFER", "VIRTUAL_ACCOUNT", "MOBILE_PHONE")
        if (paymentMethod !in validMethods) {
            throw PaymentException.invalidRequest("지원하지 않는 결제 수단입니다: $paymentMethod")
        }
    }

    /**
     * 🚀 PAYMENT_CREATED 이벤트 발행
     */
    private suspend fun publishPaymentCreatedEvent(payment: Payment) {
        try {
            log.info("🚀 [PAYMENT] PAYMENT_CREATED 이벤트 발행 시작 - paymentId: {}", payment.id)

            val event = PaymentCreatedEvent.create(
                paymentId = payment.id,
                orderId = payment.orderId,
                orderNo = generateTempOrderNo(payment.orderId), // 임시 주문번호 생성
                amount = payment.amount,
                paymentMethod = payment.paymentMethod.name,
                status = payment.status.name,
                createdAt = payment.createdAt,
                customerId = null,  // Order 이벤트로부터 수신하여 보완 예정
                popupId = null, // Order 이벤트로부터 수신하여 보완 예정
                hasReservation = null,
                hasGoods = null,
                lines = null
            )

            paymentEventPublisher.publish(event)

            log.info("✅ [PAYMENT] PAYMENT_CREATED 이벤트 발행 완료 - paymentId: {}, eventId: {}",
                payment.id, event.eventId)

        } catch (e: Exception) {
            log.error("❌ [PAYMENT] PAYMENT_CREATED 이벤트 발행 실패 - paymentId: {}, error: {}",
                payment.id, e.message, e)
            // 이벤트 발행 실패는 결제 처리에 영향을 주지 않음
        }
    }

    /**
     * 🚀 PAYMENT_APPROVED 이벤트 발행 (Order 정보 조회 포함)
     */
    private suspend fun publishPaymentApprovedEvent(payment: Payment) {
        try {
            log.info("🚀 [PAYMENT] PAYMENT_APPROVED 이벤트 발행 시작 - paymentId: {}", payment.id)

            // Order 정보 비동기 조회 시도
            val orderInfoFuture = paymentOrderInfoService.requestOrderInfo(payment.orderId)

            orderInfoFuture.thenAccept { orderInfo ->
                val event = if (orderInfo?.success == true) {
                    log.info("🔄 Order 정보 조회 성공 - orderId: {}, actualOrderNo: {}",
                        payment.orderId, orderInfo.actualOrderNo)

                    PaymentApprovedEvent.create(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                        orderNo = orderInfo.actualOrderNo ?: generateTempOrderNo(payment.orderId), // 실제 주문번호 사용
                        amount = payment.amount,
                        paymentMethod = payment.paymentMethod.name,
                        paymentKey = payment.paymentKey,
                        approvedAt = payment.approvedAt ?: java.time.LocalDateTime.now(),
                        customerId = orderInfo.actualUserId,   // 실제 사용자 ID
                        popupId = orderInfo.actualPopupId,
                        storeId = orderInfo.actualStoreId,
                        hasReservation = orderInfo.actualHasReservation,
                        hasGoods = orderInfo.actualHasGoods,
                        lines = orderInfo.actualLines ?: emptyList()
                    )
                } else {
                    log.warn("⚠️ Order 정보 조회 실패 - 기본값으로 이벤트 발행: orderId={}, orderInfo={}",
                        payment.orderId, orderInfo)

                    PaymentApprovedEvent.create(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                        orderNo = generateTempOrderNo(payment.orderId),
                        amount = payment.amount,
                        paymentMethod = payment.paymentMethod.name,
                        paymentKey = payment.paymentKey,
                        approvedAt = payment.approvedAt ?: java.time.LocalDateTime.now(),
                        customerId = null,
                        popupId = null,
                        storeId = null,
                        hasReservation = null,
                        hasGoods = null,
                        lines = emptyList()
                    )
                }

                CoroutineScope(Dispatchers.Default).launch {
                    paymentEventPublisher.publish(event)
                    log.info("✅ [PAYMENT] PAYMENT_APPROVED 이벤트 발행 완료 - paymentId: {}, eventId: {}",
                        payment.id, event.eventId)
                }
            }.exceptionally { error ->
                log.error("❌ [PAYMENT] Order 정보 조회 중 오류 - paymentId: {}, error: {}",
                    payment.id, error.message, error)
                null
            }

        } catch (e: Exception) {
            log.error("❌ [PAYMENT] PAYMENT_APPROVED 이벤트 발행 실패 - paymentId: {}, error: {}",
                payment.id, e.message, e)
            // 이벤트 발행 실패는 결제 처리에 영향을 주지 않음
        }
    }

    /**
     * 임시 주문번호 생성 (이벤트 기반 아키텍처에서 실제 orderNo는 Order 이벤트로부터 수신)
     */
    private fun generateTempOrderNo(orderId: UUID): String {
        // orderId 앞 8자리로 임시 주문번호 생성
        return "TEMP-${orderId.toString().substring(0, 8).uppercase()}"
    }
}

/**
 * 결제 생성 결과 DTO
 */
data class PaymentCreationResult(
    val paymentId: UUID,
    val status: String,
    val amount: Int,
    val createdAt: LocalDateTime
)

/**
 * 결제 상세 정보 DTO
 */
data class PaymentDetailResult(
    val paymentId: UUID,
    val orderId: UUID? = null,
    val paymentKey: String? = null,
    val status: String,
    val amount: Int,
    val approvedAt: LocalDateTime?,
    val rawPayload: String?
)
