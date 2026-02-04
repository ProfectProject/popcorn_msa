package com.popcorn.payment.event.domain.cancellation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 취소 최종 실패 이벤트
 *
 * [이벤트 발행 시점]
 * - 결제 취소 재시도가 최대 횟수에 도달하여 더 이상 재시도할 수 없는 경우
 * - 시스템적으로 복구 불가능한 결제 취소 실패 상황
 *
 * [이벤트 수신자]
 * - 관리자 알림 시스템: 수동 처리 필요 알림
 * - 모니터링 시스템: 심각한 오류 감지
 * - CS 시스템: 고객 대응 필요 케이스 등록
 *
 * [목적]
 * - 자동 복구 불가능한 상황에 대한 수동 개입 요청
 * - 시스템 안정성을 위한 최종 오류 처리
 * - 고객 서비스 품질 유지를 위한 추가 대응 트리거
 */
data class PaymentCancelFinalFailureEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 주문 번호 */
    val orderNo: String,

    /** 원본 취소 사유 */
    val originalReason: String,

    /** 최종 실패 사유 */
    val finalFailureReason: String,

    /** 총 재시도 횟수 */
    val totalRetryCount: Int,

    /** 고객 ID (CS 대응용) */
    val customerId: Long?,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 결제 ID */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = EventConstants.EventTypes.PAYMENT_CANCEL_FINAL_FAILURE,
    userId = customerId
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "originalReason" to originalReason,
        "finalFailureReason" to finalFailureReason,
        "totalRetryCount" to totalRetryCount,
        "customerId" to (customerId?.toString() ?: ""),
        "occurredAt" to occurredAt.toString(),
        "eventId" to eventId
    )

    companion object {
        /**
         * 최종 실패 이벤트 생성
         */
        fun create(
            paymentId: UUID,
            orderId: UUID,
            originalReason: String,
            finalFailureReason: String,
            totalRetryCount: Int,
            orderNo: String = orderId.toString(),
            customerId: Long? = null
        ): PaymentCancelFinalFailureEvent {
            return PaymentCancelFinalFailureEvent(
                orderId = orderId,
                orderNo = orderNo,
                originalReason = originalReason,
                finalFailureReason = finalFailureReason,
                totalRetryCount = totalRetryCount,
                customerId = customerId,
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }
    }
}