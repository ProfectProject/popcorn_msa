package com.popcorn.payment.event.domain.compensation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 보상 트랜잭션 완료 이벤트
 *
 * Order 서비스에서 보상 처리가 완료되었을 때 Payment 서비스로 전송하는 이벤트입니다.
 */
data class CompensationCompletedEvent(
    val compensationId: UUID,
    val originalPaymentId: UUID?,
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val compensationType: String,
    val completedActions: List<String>,
    val completedAt: LocalDateTime,
    val result: String, // "SUCCESS", "PARTIAL_SUCCESS", "FAILED"
    val notes: String? = null,
    private val eventUserId: Long? = null,
    private val eventMetadata: Map<String, Any>? = null
) : BasePaymentEvent(
    paymentId = compensationId,
    eventType = EventConstants.EventTypes.Compensation.COMPENSATION_COMPLETED,
    userId = eventUserId
) {

    override fun getEventPayload(): MutableMap<String, Any> {
        return mutableMapOf<String, Any>(
            "compensationId" to compensationId.toString(),
            "paymentId" to (originalPaymentId?.toString() ?: "N/A"),
            "orderId" to orderId.toString(),
            "orderNo" to orderNo,
            "customerId" to customerId,
            "compensationType" to compensationType,
            "completedActions" to completedActions,
            "completedAt" to completedAt.toString(),
            "result" to result,
            "notes" to (notes ?: ""),
            EventConstants.MetadataKeys.SOURCE_SERVICE to "order-service",
            EventConstants.MetadataKeys.TARGET_SERVICE to "payment-service"
        ).also { payload ->
            eventMetadata?.let { payload.putAll(it) }
        }
    }

    /**
     * 보상 처리가 성공했는지 확인
     */
    fun isSuccessful(): Boolean = result == EventConstants.EventStatus.SUCCESS

    /**
     * 부분 성공인지 확인
     */
    fun isPartialSuccess(): Boolean = result == "PARTIAL_SUCCESS"

    /**
     * 보상 처리가 실패했는지 확인
     */
    fun isFailed(): Boolean = result == EventConstants.EventStatus.FAILED

    /**
     * 예약 취소가 완료되었는지 확인
     */
    fun hasReservationCancelled(): Boolean = completedActions.contains("RESERVATION_CANCELLED")

    /**
     * 재고 해제가 완료되었는지 확인
     */
    fun hasStockReleased(): Boolean = completedActions.contains("STOCK_RELEASED")

    /**
     * 주문 상태가 업데이트되었는지 확인
     */
    fun hasOrderStatusUpdated(): Boolean = completedActions.contains("ORDER_STATUS_UPDATED")

    /**
     * 보상 완료 이벤트용 로그 메시지
     */
    fun getCompensationCompletedDescription(): String {
        val actionsString = completedActions.joinToString(", ")
        return "✅ [COMPENSATION-COMPLETED] $compensationType compensation completed - orderId: $orderId, result: $result, actions: [$actionsString]"
    }

    companion object {
        /**
         * 성공한 보상 완료 이벤트 생성
         */
        fun success(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            completedActions: List<String>,
            notes: String? = null,
            eventUserId: Long? = null
        ): CompensationCompletedEvent {
            return CompensationCompletedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                completedActions = completedActions,
                completedAt = LocalDateTime.now(),
                result = EventConstants.EventStatus.SUCCESS,
                notes = notes,
                eventUserId = eventUserId
            )
        }

        /**
         * 부분 성공한 보상 완료 이벤트 생성
         */
        fun partialSuccess(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            completedActions: List<String>,
            notes: String? = null,
            eventUserId: Long? = null
        ): CompensationCompletedEvent {
            return CompensationCompletedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                completedActions = completedActions,
                completedAt = LocalDateTime.now(),
                result = "PARTIAL_SUCCESS",
                notes = notes,
                eventUserId = eventUserId
            )
        }

        /**
         * 실패한 보상 완료 이벤트 생성
         */
        fun failed(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            completedActions: List<String>,
            failureReason: String,
            eventUserId: Long? = null
        ): CompensationCompletedEvent {
            return CompensationCompletedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                completedActions = completedActions,
                completedAt = LocalDateTime.now(),
                result = EventConstants.EventStatus.FAILED,
                notes = "Compensation failed: $failureReason",
                eventUserId = eventUserId
            )
        }
    }
}
