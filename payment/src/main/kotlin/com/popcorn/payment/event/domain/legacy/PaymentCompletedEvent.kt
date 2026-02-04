package com.popcorn.payment.event.domain.legacy

import java.time.LocalDateTime
import java.util.*

/**
 * 결제 완료 이벤트 (Order 서비스 호환용)
 *
 * [이벤트 발행 시점]
 * - Payment 모듈에서 실제 결제가 완료된 시점
 * - 토스페이먼츠, 네이버페이 등 PG사로부터 결제 성공 응답 수신 시
 *
 * [이벤트 수신자]
 * - Order 모듈: 주문 상태를 PAID로 변경 및 재고 차감 요청
 * - Notification 모듈: 결제 완료 알림 발송
 * - Analytics 모듈: 매출 집계 및 분석
 *
 * [주의사항]
 * 이 이벤트는 실제 결제가 완료된 후에만 발행되어야 하며,
 * 결제 요청이나 결제 대기 상태에서는 발행하지 않습니다.
 */
data class PaymentCompletedEvent(
    /** 이벤트 ID (추적용) */
    val eventId: String,

    /** 주문 ID */
    val orderId: UUID,

    /** 결제 ID */
    val paymentId: UUID,

    /** 결제 키 (PG사 결제 키) */
    val paymentKey: String?,

    /** 결제 금액 */
    val amount: Int,

    /** 결제 수단 */
    val paymentMethod: String,

    /** 결제 완료 시간 */
    val completedAt: LocalDateTime,

    /** 이벤트 발생 시간 */
    val eventTime: LocalDateTime = LocalDateTime.now(),

    /** PG사 응답 정보 (원본 응답) */
    val pgResponse: String? = null
) {
    companion object {
        /**
         * Payment 모듈에서 발행할 이벤트 생성 팩토리 메서드
         */
        fun create(
            orderId: UUID,
            paymentId: UUID,
            paymentKey: String?,
            amount: Int,
            paymentMethod: String
        ): PaymentCompletedEvent {
            return PaymentCompletedEvent(
                eventId = UUID.randomUUID().toString(),
                orderId = orderId,
                paymentId = paymentId,
                paymentKey = paymentKey,
                amount = amount,
                paymentMethod = paymentMethod,
                completedAt = LocalDateTime.now(),
                eventTime = LocalDateTime.now()
            )
        }

        /**
         * PG사 응답 정보를 포함한 이벤트 생성
         */
        fun createWithPgResponse(
            orderId: UUID,
            paymentId: UUID,
            paymentKey: String?,
            amount: Int,
            paymentMethod: String,
            pgResponse: String
        ): PaymentCompletedEvent {
            return create(orderId, paymentId, paymentKey, amount, paymentMethod)
                .copy(pgResponse = pgResponse)
        }
    }
}