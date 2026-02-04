package com.popcorn.payment.event

/**
 * Payment 이벤트 발행자 인터페이스
 *
 * BasePaymentEvent를 발행하는 표준 인터페이스입니다.
 */
interface BasePaymentEventPublisher {

    /**
     * 단일 이벤트 발행
     */
    suspend fun publish(event: BasePaymentEvent)

    /**
     * 다중 이벤트 발행
     */
    suspend fun publishAll(events: List<BasePaymentEvent>)

    /**
     * 비동기 이벤트 발행 (Fire and Forget)
     */
    fun publishAsync(event: BasePaymentEvent)
}