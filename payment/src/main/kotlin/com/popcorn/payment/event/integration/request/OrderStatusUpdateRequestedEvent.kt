package com.popcorn.payment.event.integration.request

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 상태 변경 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Payment 서비스에서 결제 실패, 만료 등으로 주문 상태 변경이 필요한 경우
 *
 * [이벤트 수신자]
 * - Order 서비스: 주문 상태 변경 처리
 *
 * [목적]
 * - 이벤트 기반 아키텍처로 주문 상태 관리
 * - Payment 서비스에서 Order 서비스 직접 호출 제거
 */
data class OrderStatusUpdateRequestedEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 변경할 상태 */
    val newStatus: String,

    /** 상태 변경 사유 */
    val reason: String?,

    /** 요청 시간 */
    val requestedAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 결제 ID (실패/만료된 결제의 ID) */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = EventConstants.EventTypes.Integration.ORDER_STATUS_UPDATE_REQUESTED
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "newStatus" to newStatus,
        "reason" to (reason ?: ""),
        "requestedAt" to requestedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        EventConstants.MetadataKeys.EVENT_ID to eventId
    )

    companion object {
        /**
         * 주문 실패 상태 변경 이벤트 생성
         */
        fun createFailed(
            paymentId: UUID,
            orderId: UUID,
            reason: String
        ): OrderStatusUpdateRequestedEvent {
            return OrderStatusUpdateRequestedEvent(
                orderId = orderId,
                newStatus = EventConstants.EventStatus.FAILED,
                reason = reason,
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }

        /**
         * 주문 만료 상태 변경 이벤트 생성
         */
        fun createExpired(
            paymentId: UUID,
            orderId: UUID
        ): OrderStatusUpdateRequestedEvent {
            return OrderStatusUpdateRequestedEvent(
                orderId = orderId,
                newStatus = EventConstants.EventStatus.EXPIRED,
                reason = "결제 만료",
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }

        /**
         * 일반적인 주문 상태 변경 이벤트 생성
         */
        fun create(
            paymentId: UUID,
            orderId: UUID,
            newStatus: String,
            reason: String? = null
        ): OrderStatusUpdateRequestedEvent {
            return OrderStatusUpdateRequestedEvent(
                orderId = orderId,
                newStatus = newStatus,
                reason = reason,
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }
    }
}
