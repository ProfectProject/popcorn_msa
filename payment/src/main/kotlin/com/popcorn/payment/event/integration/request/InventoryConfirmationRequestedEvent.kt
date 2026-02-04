package com.popcorn.payment.event.integration.request

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 재고 처리 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Payment 서비스에서 결제 승인 완료 후 재고 차감 확정 필요 시
 * - Payment 서비스에서 결제 실패/취소 후 재고 복구 필요 시
 *
 * [이벤트 수신자]
 * - Store 서비스: 실제 재고 차감 확정 또는 복구 처리
 *
 * [목적]
 * - 이벤트 기반 아키텍처로 재고 관리
 * - Payment 서비스에서 Store 서비스 직접 호출 제거
 * - 재고 예약 → 확정/복구 플로우 관리
 */
data class InventoryConfirmationRequestedEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 처리 유형 (CONFIRM: 차감 확정, RESTORE: 복구) */
    val actionType: String,

    /** 요청 사유 */
    val reason: String,

    /** 요청 시간 */
    val requestedAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 관련 결제 ID */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = EventConstants.EventTypes.INVENTORY_CONFIRMATION_REQUESTED
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "actionType" to actionType,
        "reason" to reason,
        "requestedAt" to requestedAt.toString(),
        "occurredAt" to occurredAt.toString(),
        "eventId" to eventId
    )

    companion object {
        /**
         * 재고 차감 확정 이벤트 생성
         */
        fun createConfirm(
            paymentId: UUID,
            orderId: UUID
        ): InventoryConfirmationRequestedEvent {
            return InventoryConfirmationRequestedEvent(
                orderId = orderId,
                actionType = "CONFIRM",
                reason = "결제 승인 완료",
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }

        /**
         * 재고 복구 이벤트 생성
         */
        fun createRestore(
            paymentId: UUID,
            orderId: UUID,
            reason: String = "결제 실패/취소"
        ): InventoryConfirmationRequestedEvent {
            return InventoryConfirmationRequestedEvent(
                orderId = orderId,
                actionType = "RESTORE",
                reason = reason,
                requestedAt = LocalDateTime.now(),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }
    }
}