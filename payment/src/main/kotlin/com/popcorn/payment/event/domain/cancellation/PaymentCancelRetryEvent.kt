package com.popcorn.payment.event.domain.cancellation

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 취소 재시도 이벤트
 *
 * [이벤트 발행 시점]
 * - 결제 취소가 실패했을 때 재시도가 필요한 경우
 * - 재시도 큐에서 처리할 이벤트로 발행
 *
 * [이벤트 수신자]
 * - Payment 서비스 자체: 지연된 재시도 처리
 * - 외부 스케줄러: 배치 기반 재시도 처리
 *
 * [목적]
 * - 일시적 오류에 대한 자동 복구
 * - 지수 백오프를 통한 안정적 재시도
 * - 시스템 부하 분산
 */
data class PaymentCancelRetryEvent(
    /** 주문 ID */
    val orderId: UUID,

    /** 주문 번호 */
    val orderNo: String,

    /** 취소 사유 (원본) */
    val cancelReason: String,

    /** 재시도 횟수 */
    val retryCount: Int,

    /** 지연 시간 (초) */
    val delaySeconds: Int,

    /** 다음 재시도 예정 시간 */
    val nextRetryAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val occurredAt: LocalDateTime = LocalDateTime.now(),

    /** 이벤트 ID (추적용) */
    val eventId: String = UUID.randomUUID().toString(),

    /** 결제 ID */
    val relatedPaymentId: UUID
) : BasePaymentEvent(
    paymentId = relatedPaymentId,
    eventType = EventConstants.EventTypes.PaymentRetry.PAYMENT_CANCEL_RETRY
) {

    override fun getEventPayload(): Map<String, Any> = mapOf(
        "paymentId" to paymentId,
        "orderId" to orderId,
        "orderNo" to orderNo,
        "cancelReason" to cancelReason,
        "retryCount" to retryCount,
        "delaySeconds" to delaySeconds,
        "nextRetryAt" to nextRetryAt.toString(),
        "occurredAt" to occurredAt.toString(),
        EventConstants.MetadataKeys.EVENT_ID to eventId
    )

    companion object {
        /**
         * 재시도 이벤트 생성
         */
        fun create(
            paymentId: UUID,
            orderId: UUID,
            cancelReason: String,
            retryCount: Int,
            delaySeconds: Int,
            orderNo: String = orderId.toString()
        ): PaymentCancelRetryEvent {
            return PaymentCancelRetryEvent(
                orderId = orderId,
                orderNo = orderNo,
                cancelReason = cancelReason,
                retryCount = retryCount,
                delaySeconds = delaySeconds,
                nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds.toLong()),
                occurredAt = LocalDateTime.now(),
                relatedPaymentId = paymentId
            )
        }
    }
}
