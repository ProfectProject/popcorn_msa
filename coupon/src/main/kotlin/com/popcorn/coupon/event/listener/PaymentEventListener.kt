package com.popcorn.coupon.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.popcorn.coupon.event.external.PaymentCancelledEvent
import com.popcorn.coupon.event.external.PaymentCompletedEvent
import com.popcorn.coupon.event.external.PaymentFailedEvent
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
class PaymentEventListener(
    private val couponCommandService: CouponCommandService,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 결제 완료 이벤트 처리
     * - 쿠폰 사용 확정 처리
     */
    @KafkaListener(
        topics = ["payment-events"],
        groupId = "coupon-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentCompletedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "📥 결제 완료 이벤트 수신: topic=$topic, partition=$partition, offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "PAYMENT_COMPLETED") {
                val event = objectMapper.readValue<PaymentCompletedEvent>(eventData["data"] as String? ?: message)
                processPaymentCompletedEvent(event)
            }

            logger.debug { "✅ 결제 완료 이벤트 처리 완료: offset=$offset" }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 완료 이벤트 처리 실패: offset=$offset, message=$message" }
        }
    }

    /**
     * 결제 실패 이벤트 처리
     * - 쿠폰 예약 해제 처리
     */
    @KafkaListener(
        topics = ["payment-events"],
        groupId = "coupon-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentFailedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "📥 결제 실패 이벤트 수신: offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "PAYMENT_FAILED") {
                val event = objectMapper.readValue<PaymentFailedEvent>(eventData["data"] as String? ?: message)
                processPaymentFailedEvent(event)
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 실패 이벤트 처리 실패: offset=$offset, message=$message" }
        }
    }

    /**
     * 결제 취소 이벤트 처리
     * - 쿠폰 예약 해제 처리
     */
    @KafkaListener(
        topics = ["payment-events"],
        groupId = "coupon-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentCancelledEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "📥 결제 취소 이벤트 수신: offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "PAYMENT_CANCELLED") {
                val event = objectMapper.readValue<PaymentCancelledEvent>(eventData["data"] as String? ?: message)
                processPaymentCancelledEvent(event)
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 취소 이벤트 처리 실패: offset=$offset, message=$message" }
        }
    }

    /**
     * 결제 완료 이벤트 처리 로직
     */
    private fun processPaymentCompletedEvent(event: PaymentCompletedEvent) = runBlocking {
        logger.info { "💳 결제 완료 이벤트 처리: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }

        try {
            // 쿠폰이 사용된 결제인 경우 쿠폰 사용 확정
            if (event.userCouponId != null && event.discountAmount != null) {
                couponCommandService.confirmCouponUsage(
                    userCouponId = event.userCouponId,
                    orderId = event.orderId,
                    actualDiscountAmount = event.discountAmount
                )
                logger.info { "✅ 결제 완료 쿠폰 확정 완료: orderId=${event.orderId}, userCouponId=${event.userCouponId}, discount=${event.discountAmount}" }
            } else {
                logger.debug { "ℹ️ 쿠폰이 없는 결제: orderId=${event.orderId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 완료 쿠폰 처리 실패: orderId=${event.orderId}" }
            // 결제 서비스에 쿠폰 확정 실패 알림 가능
        }
    }

    /**
     * 결제 실패 이벤트 처리 로직
     */
    private fun processPaymentFailedEvent(event: PaymentFailedEvent) = runBlocking {
        logger.info { "💸 결제 실패 이벤트 처리: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }

        try {
            // 쿠폰이 사용된 결제인 경우 쿠폰 예약 해제
            if (event.userCouponId != null) {
                couponCommandService.cancelCouponUsage(
                    userCouponId = event.userCouponId,
                    orderId = event.orderId
                )
                logger.info { "✅ 결제 실패 쿠폰 해제 완료: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 실패 쿠폰 처리 실패: orderId=${event.orderId}" }
        }
    }

    /**
     * 결제 취소 이벤트 처리 로직
     */
    private fun processPaymentCancelledEvent(event: PaymentCancelledEvent) = runBlocking {
        logger.info { "🔄 결제 취소 이벤트 처리: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }

        try {
            // 쿠폰이 사용된 결제인 경우 쿠폰 예약 해제
            if (event.userCouponId != null) {
                couponCommandService.cancelCouponUsage(
                    userCouponId = event.userCouponId,
                    orderId = event.orderId
                )
                logger.info { "✅ 결제 취소 쿠폰 해제 완료: orderId=${event.orderId}, userCouponId=${event.userCouponId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 결제 취소 쿠폰 처리 실패: orderId=${event.orderId}" }
        }
    }
}
