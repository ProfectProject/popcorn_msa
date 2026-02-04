package com.popcorn.payment.event

import com.popcorn.common.event.BaseEvent
import java.util.*

/**
 * 결제 도메인 전용 이벤트 기본 클래스 (Kotlin 버전)
 *
 * common-lib의 BaseEvent를 상속하여 결제 도메인에 특화된 기능을 제공합니다.
 *
 * 주요 기능:
 * - aggregateType을 "Payment"로 고정
 * - paymentId 접근자 제공 (aggregateId의 별명)
 * - 결제 도메인별 유틸리티 메서드
 * - Kotlin interop 최적화
 */
abstract class BasePaymentEvent(
    paymentId: UUID,
    eventType: String,
    userId: Long? = null,
    metadata: Map<String, Any>? = null
) : BaseEvent(paymentId, "Payment", eventType, userId, metadata) {

    /**
     * 결제 ID (aggregateId의 별명)
     */
    val paymentId: UUID
        get() = aggregateId

    /**
     * 결제 이벤트인지 확인
     * @return 항상 true (Payment 도메인 전용)
     */
    fun isPaymentEvent(): Boolean = "Payment" == aggregateType

    /**
     * 결제 이벤트용 로그 메시지 생성
     */
    fun getPaymentEventDescription(): String {
        return "💳 [PAYMENT] $eventType - paymentId: $paymentId, userId: $userId"
    }

    /**
     * 결제 관련 메타데이터 추가를 위한 헬퍼 메서드
     */
    protected fun createPaymentMetadata(
        additionalData: Map<String, Any>? = null
    ): Map<String, Any> {
        val baseMetadata = mutableMapOf<String, Any>(
            "domain" to "payment",
            "aggregateType" to "Payment"
        )
        additionalData?.let { baseMetadata.putAll(it) }
        return baseMetadata
    }
}