package com.popcorn.payment.event

import java.time.LocalDateTime
import java.util.*

/**
 * QR 코드 생성 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Payment 서비스에서 결제 승인이 완료된 후
 * - Order 서비스가 QR 코드를 생성해야 할 때
 *
 * [이벤트 수신자]
 * - Order 서비스: QR 코드 생성 및 CheckIns 서비스 호출
 *
 * [목적]
 * - 이벤트 기반 아키텍처로 서비스 간 결합도 감소
 * - Payment 서비스에서 Order/CheckIns 서비스 직접 호출 제거
 * - 서비스별 책임 분리 (Payment는 결제만, Order는 주문 및 QR 관리)
 */
data class QrCodeGenerationRequestedEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 주문 번호 */
    val orderNo: String,

    /** 고객 ID */
    val customerId: Long?,

    /** 요청 시간 */
    val requestedAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 결제 ID */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = "qr-generation-requested",
    userId = customerId
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "customerId" to (customerId?.toString() ?: ""),
        "requestedAt" to requestedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "eventId" to eventId
    )

    companion object {
        /**
         * 팩토리 메서드: Payment 서비스에서 QR 생성 요청 시 사용
         */
        fun create(
            paymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long?
        ): QrCodeGenerationRequestedEvent {
            return QrCodeGenerationRequestedEvent(
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                eventId = UUID.randomUUID().toString(),
                relatedPaymentId = paymentId
            )
        }
    }
}