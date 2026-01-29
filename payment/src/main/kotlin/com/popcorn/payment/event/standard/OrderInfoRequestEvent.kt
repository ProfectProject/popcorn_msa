package com.popcorn.payment.event.standard

import java.time.LocalDateTime
import java.util.UUID

/**
 * Order 정보 요청 이벤트 (Payment → Order)
 * Payment 서비스에서 Order 정보가 필요할 때 발행하는 이벤트
 */
class OrderInfoRequestEvent(
    /**
     * 요청 ID (응답 매칭용)
     */
    var requestId: String? = null,

    /**
     * 요청하는 주문 ID
     */
    var requestedOrderId: UUID? = null,

    /**
     * 응답 받을 서비스 (payment-service)
     */
    var responseService: String? = null,

    /**
     * 요청 시간
     */
    var requestedAt: LocalDateTime? = null

) : StandardBaseEvent() {

    companion object {
        /**
         * Order 정보 요청 이벤트 생성
         */
        fun create(orderId: UUID): OrderInfoRequestEvent {
            val event = OrderInfoRequestEvent(
                requestId = UUID.randomUUID().toString(),
                requestedOrderId = orderId,
                responseService = "payment-service",
                requestedAt = LocalDateTime.now()
            ).apply {
                this.eventType = StandardEventType.ORDER_INFO_REQUEST // kebab-case 형식 사용
                this.producer = "payment-service"
                this.orderId = orderId
            }

            event.setDefaults()
            return event
        }
    }

    /**
     * Redis Stream 발행용 Map 변환
     */
    override fun toStreamMap(): Map<String, String> {
        val map = super.toStreamMap().toMutableMap()

        requestId?.let { map["requestId"] = it }
        requestedOrderId?.let { map["requestedOrderId"] = it.toString() }
        responseService?.let { map["responseService"] = it }
        requestedAt?.let { map["requestedAt"] = it.toString() }

        return map
    }
}