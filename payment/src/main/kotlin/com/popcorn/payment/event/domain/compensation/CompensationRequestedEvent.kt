package com.popcorn.payment.event.domain.compensation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.util.*

/**
 * 보상 트랜잭션 요청 이벤트
 *
 * 결제 처리 과정에서 실패가 발생했을 때 보상 처리를 요청하는 이벤트입니다.
 * Order 서비스가 이 이벤트를 수신하여 예약 취소 등의 보상 작업을 수행합니다.
 */
data class CompensationRequestedEvent(
    val compensationId: UUID,
    val originalPaymentId: UUID,
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val compensationType: String, // "VALIDATION_FAILURE", "PAYMENT_FAILURE", "SYSTEM_ERROR"
    val failureReason: String,
    val failureStage: String, // "PRE_PAYMENT", "PAYMENT_APPROVAL", "POST_PAYMENT"
    val amount: Int,
    val compensationActions: List<String>, // ["CANCEL_RESERVATIONS", "REFUND_PAYMENT", "UPDATE_ORDER_STATUS"]
    val priority: String = "HIGH", // "HIGH", "MEDIUM", "LOW"
    private val eventUserId: Long? = null,
    private val eventMetadata: Map<String, Any>? = null
) : BasePaymentEvent(
    paymentId = compensationId,
    eventType = EventConstants.EventTypes.Compensation.COMPENSATION_REQUESTED,
    userId = eventUserId
) {

    override fun getEventPayload(): MutableMap<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "compensationId" to compensationId,
            "originalPaymentId" to originalPaymentId.toString(),
            "orderId" to orderId.toString(),
            "orderNo" to orderNo,
            "customerId" to customerId,
            "compensationType" to compensationType,
            "failureReason" to failureReason,
            "failureStage" to failureStage,
            "amount" to amount,
            "compensationActions" to compensationActions,
            "priority" to priority,
            EventConstants.MetadataKeys.TARGET_SERVICE to "order-service"
        )
        eventMetadata?.let { payload.putAll(it) }
        return payload
    }

    /**
     * 검증 실패로 인한 보상인지 확인
     */
    fun isValidationFailureCompensation(): Boolean = compensationType == "VALIDATION_FAILURE"

    /**
     * 결제 실패로 인한 보상인지 확인
     */
    fun isPaymentFailureCompensation(): Boolean = compensationType == "PAYMENT_FAILURE"

    /**
     * 시스템 오류로 인한 보상인지 확인
     */
    fun isSystemErrorCompensation(): Boolean = compensationType == "SYSTEM_ERROR"

    /**
     * 예약 취소가 필요한지 확인
     */
    fun requiresReservationCancellation(): Boolean = compensationActions.contains("CANCEL_RESERVATIONS")

    /**
     * 결제 환불이 필요한지 확인
     */
    fun requiresRefund(): Boolean = compensationActions.contains("REFUND_PAYMENT")

    /**
     * 주문 상태 업데이트가 필요한지 확인
     */
    fun requiresOrderStatusUpdate(): Boolean = compensationActions.contains("UPDATE_ORDER_STATUS")

    /**
     * 높은 우선순위인지 확인
     */
    fun isHighPriority(): Boolean = priority == "HIGH"

    /**
     * 보상 요청 이벤트용 로그 메시지
     */
    fun getCompensationDescription(): String {
        return "🔄 [COMPENSATION] $compensationType compensation requested - orderId: $orderId, actions: $compensationActions"
    }

    companion object {
        /**
         * 검증 실패 보상 이벤트 생성
         */
        fun forValidationFailure(
            originalPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            amount: Int,
            eventUserId: Long? = null
        ): CompensationRequestedEvent {
            return CompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = "VALIDATION_FAILURE",
                failureReason = failureReason,
                failureStage = "PRE_PAYMENT",
                amount = amount,
                compensationActions = listOf(
                    "CANCEL_RESERVATIONS",
                    "UPDATE_ORDER_STATUS"
                ),
                priority = "HIGH",
                eventUserId = eventUserId
            )
        }

        /**
         * 결제 실패 보상 이벤트 생성
         */
        fun forPaymentFailure(
            originalPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            amount: Int,
            eventUserId: Long? = null
        ): CompensationRequestedEvent {
            return CompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = "PAYMENT_FAILURE",
                failureReason = failureReason,
                failureStage = "PAYMENT_APPROVAL",
                amount = amount,
                compensationActions = listOf(
                    "CANCEL_RESERVATIONS",
                    "REFUND_PAYMENT",
                    "UPDATE_ORDER_STATUS"
                ),
                priority = "HIGH",
                eventUserId = eventUserId
            )
        }

        /**
         * 시스템 오류 보상 이벤트 생성
         */
        fun forSystemError(
            originalPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            failureReason: String,
            failureStage: String,
            amount: Int,
            eventUserId: Long? = null
        ): CompensationRequestedEvent {
            return CompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = "SYSTEM_ERROR",
                failureReason = failureReason,
                failureStage = failureStage,
                amount = amount,
                compensationActions = listOf(
                    "CANCEL_RESERVATIONS",
                    "REFUND_PAYMENT",
                    "UPDATE_ORDER_STATUS"
                ),
                priority = "HIGH",
                eventUserId = eventUserId
            )
        }
    }
}
