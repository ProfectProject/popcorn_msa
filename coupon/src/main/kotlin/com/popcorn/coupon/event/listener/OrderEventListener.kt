package com.popcorn.coupon.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.popcorn.coupon.event.external.OrderCancelledEvent
import com.popcorn.coupon.event.external.OrderCompletedEvent
import com.popcorn.coupon.event.external.OrderCreatedEvent
import com.popcorn.coupon.service.core.CouponCommandService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OrderEventListener(
    private val couponCommandService: CouponCommandService,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주문 생성 이벤트 처리
     * - 쿠폰이 포함된 주문인 경우 쿠폰 예약 처리
     */
    @KafkaListener(
        topics = ["order-events"],
        groupId = "coupon-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleOrderCreatedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "📥 주문 생성 이벤트 수신: topic=$topic, partition=$partition, offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "ORDER_CREATED") {
                val event = objectMapper.readValue<OrderCreatedEvent>(eventData["data"] as String? ?: message)
                processOrderCreatedEvent(event)
            }

            logger.debug { "✅ 주문 이벤트 처리 완료: offset=$offset" }

        } catch (e: Exception) {
            logger.error(e) { "❌ 주문 이벤트 처리 실패: offset=$offset, message=$message" }
            // DLQ로 전송하거나 재시도 로직 구현 가능
        }
    }

    /**
     * 주문 취소 이벤트 처리
     * - 쿠폰 예약 해제 처리
     */
    @KafkaListener(
        topics = ["order-events"],
        groupId = "coupon-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleOrderCancelledEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "📥 주문 취소 이벤트 수신: offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "ORDER_CANCELLED") {
                val event = objectMapper.readValue<OrderCancelledEvent>(eventData["data"] as String? ?: message)
                processOrderCancelledEvent(event)
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 주문 취소 이벤트 처리 실패: offset=$offset, message=$message" }
        }
    }

    /**
     * 주문 생성 이벤트 처리 로직
     */
    private fun processOrderCreatedEvent(event: OrderCreatedEvent) = runBlocking {
        logger.info { "🎟️ 주문 생성 이벤트 처리: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }

        try {
            // 쿠폰이 포함된 주문인 경우 쿠폰 사용 처리
            if (event.userCouponId != null && event.expectedDiscountAmount != null) {
                couponCommandService.useCoupon(
                    userId = event.userId,
                    userCouponId = event.userCouponId,
                    orderId = event.orderId,
                    orderAmount = event.totalAmount
                )
                logger.info { "✅ 주문 쿠폰 예약 완료: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }
            } else {
                logger.debug { "ℹ️ 쿠폰이 없는 주문: orderId=${event.orderId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 주문 생성 쿠폰 처리 실패: orderId=${event.orderId}" }
            // 주문 서비스에 쿠폰 사용 실패 알림 이벤트 발행 가능
        }
    }

    /**
     * 주문 취소 이벤트 처리 로직
     */
    private fun processOrderCancelledEvent(event: OrderCancelledEvent) = runBlocking {
        logger.info { "🔄 주문 취소 이벤트 처리: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }

        try {
            // 쿠폰이 사용된 주문인 경우 쿠폰 예약 해제
            if (event.userCouponId != null) {
                couponCommandService.cancelCouponUsage(
                    userCouponId = event.userCouponId,
                    orderId = event.orderId
                )
                logger.info { "✅ 주문 취소 쿠폰 해제 완료: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 주문 취소 쿠폰 처리 실패: orderId=${event.orderId}" }
        }
    }
}
