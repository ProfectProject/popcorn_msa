package com.popcorn.payment.event.domain.compensation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 보상 트랜잭션 실패 이벤트
 *
 * Order 서비스에서 보상 처리에 실패했을 때 Payment 서비스로 전송하는 이벤트입니다.
 */
data class CompensationFailedEvent(
    val compensationId: UUID,
    val originalPaymentId: UUID?,
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val compensationType: String,
    val failureReason: String,
    val failedAt: LocalDateTime,
    val attemptedActions: List<String>,
    val failedActions: List<String>,
    val partiallyCompletedActions: List<String> = emptyList(),
    val requiresManualIntervention: Boolean = true,
    val retryable: Boolean = false,
    private val eventUserId: Long? = null,
    private val eventMetadata: Map<String, Any>? = null
) : BasePaymentEvent(
    paymentId = compensationId,
    eventType = EventConstants.EventTypes.COMPENSATION_FAILED,
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
            "failureReason" to failureReason,
            "failedAt" to failedAt.toString(),
            "attemptedActions" to attemptedActions,
            "failedActions" to failedActions,
            "partiallyCompletedActions" to partiallyCompletedActions,
            "requiresManualIntervention" to requiresManualIntervention,
            "retryable" to retryable,
            "severity" to "HIGH",
            "sourceService" to "order-service",
            "targetService" to "payment-service"
        ).also { payload ->
            eventMetadata?.let { payload.putAll(it) }
        }
    }

    /**
     * 완전히 실패했는지 확인 (부분 성공도 없음)
     */
    fun isCompleteFailure(): Boolean = partiallyCompletedActions.isEmpty()

    /**
     * 부분적으로라도 성공한 액션이 있는지 확인
     */
    fun hasPartialSuccess(): Boolean = partiallyCompletedActions.isNotEmpty()

    /**
     * 재시도 가능한지 확인
     */
    fun isRetryable(): Boolean = retryable

    /**
     * 수동 개입이 필요한지 확인
     */
    fun requiresManualIntervention(): Boolean = requiresManualIntervention

    /**
     * 예약 취소가 실패했는지 확인
     */
    fun hasReservationCancellationFailed(): Boolean = failedActions.contains("CANCEL_RESERVATION")

    /**
     * 재고 해제가 실패했는지 확인
     */
    fun hasStockReleaseFailed(): Boolean = failedActions.contains("RELEASE_STOCK")

    /**
     * 주문 상태 업데이트가 실패했는지 확인
     */
    fun hasOrderStatusUpdateFailed(): Boolean = failedActions.contains("UPDATE_ORDER_STATUS")

    /**
     * 보상 실패 이벤트용 로그 메시지
     */
    fun getCompensationFailedDescription(): String {
        val failedActionsString = failedActions.joinToString(", ")
        val completedActionsString = if (partiallyCompletedActions.isNotEmpty()) {
            " (Partial success: ${partiallyCompletedActions.joinToString(", ")})"
        } else ""
        return "❌ [COMPENSATION-FAILED] $compensationType compensation failed - orderId: $orderId, failed: [$failedActionsString]$completedActionsString"
    }

    /**
     * 심각도 수준 반환
     */
    fun getSeverityLevel(): String {
        return when {
            isCompleteFailure() && requiresManualIntervention -> "CRITICAL"
            hasPartialSuccess() -> "HIGH"
            isRetryable() -> "MEDIUM"
            else -> "HIGH"
        }
    }

    companion object {
        /**
         * 완전 실패 보상 실패 이벤트 생성
         */
        fun completeFailure(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            failureReason: String,
            attemptedActions: List<String>,
            eventUserId: Long? = null
        ): CompensationFailedEvent {
            return CompensationFailedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                failureReason = failureReason,
                failedAt = LocalDateTime.now(),
                attemptedActions = attemptedActions,
                failedActions = attemptedActions,
                partiallyCompletedActions = emptyList(),
                requiresManualIntervention = true,
                retryable = false,
                eventUserId = eventUserId
            )
        }

        /**
         * 부분 실패 보상 실패 이벤트 생성
         */
        fun partialFailure(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            failureReason: String,
            attemptedActions: List<String>,
            failedActions: List<String>,
            completedActions: List<String>,
            retryable: Boolean = true,
            eventUserId: Long? = null
        ): CompensationFailedEvent {
            return CompensationFailedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                failureReason = failureReason,
                failedAt = LocalDateTime.now(),
                attemptedActions = attemptedActions,
                failedActions = failedActions,
                partiallyCompletedActions = completedActions,
                requiresManualIntervention = !retryable,
                retryable = retryable,
                eventUserId = eventUserId
            )
        }

        /**
         * 재시도 가능한 보상 실패 이벤트 생성
         */
        fun retryableFailure(
            compensationId: UUID,
            originalPaymentId: UUID?,
            orderId: UUID,
            orderNo: String,
            customerId: Long,
            compensationType: String,
            failureReason: String,
            failedActions: List<String>,
            eventUserId: Long? = null
        ): CompensationFailedEvent {
            return CompensationFailedEvent(
                compensationId = compensationId,
                originalPaymentId = originalPaymentId,
                orderId = orderId,
                orderNo = orderNo,
                customerId = customerId,
                compensationType = compensationType,
                failureReason = failureReason,
                failedAt = LocalDateTime.now(),
                attemptedActions = failedActions,
                failedActions = failedActions,
                partiallyCompletedActions = emptyList(),
                requiresManualIntervention = false,
                retryable = true,
                eventUserId = eventUserId
            )
        }
    }
}