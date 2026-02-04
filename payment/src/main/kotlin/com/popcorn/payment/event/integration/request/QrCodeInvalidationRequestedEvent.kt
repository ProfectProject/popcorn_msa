package com.popcorn.payment.event.integration.request

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * QR 코드 무효화 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Payment 서비스에서 결제가 취소되거나 실패한 경우
 * - QR 코드를 무효화해야 하는 상황 발생 시
 *
 * [이벤트 수신자]
 * - Order 서비스: QR 코드 상태 무효화 처리
 * - CheckIns 서비스: 해당 QR 코드로 입장 불가 처리
 *
 * [목적]
 * - 이벤트 기반 아키텍처로 QR 코드 무효화 처리
 * - 서비스 간 직접 호출 제거
 */
data class QrCodeInvalidationRequestedEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 무효화 사유 */
    val reason: String,

    /** 요청 시간 */
    val requestedAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 결제 ID (취소된 결제의 ID) */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = EventConstants.EventTypes.QR_INVALIDATION_REQUESTED
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "reason" to reason,
        "requestedAt" to requestedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "eventId" to eventId
    )

    companion object {
        /**
         * 팩토리 메서드: QR 코드 무효화 요청
         */
        fun create(
            paymentId: UUID,
            orderId: UUID,
            reason: String
        ): QrCodeInvalidationRequestedEvent {
            return QrCodeInvalidationRequestedEvent(
                orderId = orderId,
                reason = reason,
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                eventId = UUID.randomUUID().toString(),
                relatedPaymentId = paymentId
            )
        }
    }
}