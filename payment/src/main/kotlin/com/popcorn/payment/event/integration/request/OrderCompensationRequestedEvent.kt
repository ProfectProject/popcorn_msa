package com.popcorn.payment.event.integration.request

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * Order 서비스에 보상 트랜잭션을 요청하는 이벤트
 *
 * Payment 서비스에서 검증 실패나 결제 실패가 발생했을 때
 * Order 서비스에게 예약 취소 및 주문 상태 업데이트를 요청하는 이벤트입니다.
 */
data class OrderCompensationRequestedEvent(
    val compensationId: UUID,
    val originalPaymentId: UUID?,
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val compensationReason: String,
    val compensationType: String, // "VALIDATION_FAILURE", "PAYMENT_FAILURE"
    val failedAt: LocalDateTime,
    val requestedActions: List<CompensationAction>,
    val priority: String = "HIGH",
    val correlationId: String? = null,
    private val eventUserId: Long? = null,
    private val eventMetadata: Map<String, Any>? = null
) : BasePaymentEvent(
    paymentId = compensationId,
    eventType = EventConstants.EventTypes.ORDER_COMPENSATION_REQUESTED,
    userId = eventUserId
) {

    override fun getEventPayload(): MutableMap<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "compensationId" to compensationId,
            "originalPaymentId" to (originalPaymentId?.toString() ?: "N/A"),
            "orderId" to orderId.toString(),
            "orderNo" to orderNo,
            "customerId" to customerId,
            "compensationReason" to compensationReason,
            "compensationType" to compensationType,
            "failedAt" to failedAt.toString(),
            "requestedActions" to requestedActions.map { it.actionType },
            "priority" to priority,
            "correlationId" to (correlationId ?: UUID.randomUUID().toString()),
            "targetService" to "order-service",
            "sourceService" to "payment-service"
        )
        eventMetadata?.let { payload.putAll(it) }
        return payload
    }

    /**
     * 보상 액션 정의
     */
    data class CompensationAction(
        val actionType: String, // "CANCEL_RESERVATION", "UPDATE_ORDER_STATUS", "RELEASE_STOCK"
        val targetResource: String?, // 대상 리소스 ID (예: sessionId, goodsId)
        val parameters: Map<String, Any>? = null
    )

    /**
     * 예약 취소 액션이 포함되어 있는지 확인
     */
    fun hasReservationCancellationAction(): Boolean {
        return requestedActions.any { it.actionType == "CANCEL_RESERVATION" }
    }

    /**
     * 주문 상태 업데이트 액션이 포함되어 있는지 확인
     */
    fun hasOrderStatusUpdateAction(): Boolean {
        return requestedActions.any { it.actionType == "UPDATE_ORDER_STATUS" }
    }

    /**
     * 재고 해제 액션이 포함되어 있는지 확인
     */
    fun hasStockReleaseAction(): Boolean {
        return requestedActions.any { it.actionType == "RELEASE_STOCK" }
    }

    /**
     * 높은 우선순위인지 확인
     */
    fun isHighPriority(): Boolean = priority == "HIGH"

    /**
     * Order 보상 요청 이벤트용 로그 메시지
     */
    fun getOrderCompensationDescription(): String {
        val actionTypes = requestedActions.map { it.actionType }.joinToString(", ")
        return "📨 [ORDER-COMPENSATION] Compensation requested to Order service - orderId: $orderId, actions: [$actionTypes]"
    }

    companion object {
        /**
         * 검증 실패로 인한 Order 보상 요청 이벤트 생성
         */
        fun forValidationFailure(
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            validationFailureReason: String,
            originalPaymentId: UUID? = null,
            eventUserId: Long? = null
        ): OrderCompensationRequestedEvent {
            return OrderCompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationReason = "Payment validation failed: $validationFailureReason",
                compensationType = "VALIDATION_FAILURE",
                failedAt = LocalDateTime.now(),
                requestedActions = listOf(
                    CompensationAction(
                        actionType = "CANCEL_RESERVATION",
                        targetResource = orderId.toString(),
                        parameters = mapOf("reason" to "validation_failure")
                    ),
                    CompensationAction(
                        actionType = "UPDATE_ORDER_STATUS",
                        targetResource = orderId.toString(),
                        parameters = mapOf(
                            "newStatus" to "CANCELLED",
                            "reason" to validationFailureReason
                        )
                    )
                ),
                priority = "HIGH",
                eventUserId = eventUserId
            )
        }

        /**
         * 결제 실패로 인한 Order 보상 요청 이벤트 생성
         */
        fun forPaymentFailure(
            originalPaymentId: UUID,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            paymentFailureReason: String,
            eventUserId: Long? = null
        ): OrderCompensationRequestedEvent {
            return OrderCompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationReason = "Payment processing failed: $paymentFailureReason",
                compensationType = "PAYMENT_FAILURE",
                failedAt = LocalDateTime.now(),
                requestedActions = listOf(
                    CompensationAction(
                        actionType = "CANCEL_RESERVATION",
                        targetResource = orderId.toString(),
                        parameters = mapOf("reason" to "payment_failure")
                    ),
                    CompensationAction(
                        actionType = "RELEASE_STOCK",
                        targetResource = orderId.toString(),
                        parameters = mapOf("reason" to "payment_failure")
                    ),
                    CompensationAction(
                        actionType = "UPDATE_ORDER_STATUS",
                        targetResource = orderId.toString(),
                        parameters = mapOf(
                            "newStatus" to "PAYMENT_FAILED",
                            "reason" to paymentFailureReason
                        )
                    )
                ),
                priority = "HIGH",
                eventUserId = eventUserId
            )
        }

        /**
         * 주문별 맞춤 보상 요청 이벤트 생성
         */
        fun forCustomCompensation(
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationReason: String,
            compensationType: String,
            actions: List<CompensationAction>,
            originalPaymentId: UUID? = null,
            priority: String = "HIGH",
            eventUserId: Long? = null
        ): OrderCompensationRequestedEvent {
            return OrderCompensationRequestedEvent(
                compensationId = UUID.randomUUID(),
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationReason = compensationReason,
                compensationType = compensationType,
                failedAt = LocalDateTime.now(),
                requestedActions = actions,
                priority = priority,
                eventUserId = eventUserId
            )
        }
    }
}