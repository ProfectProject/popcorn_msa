package com.popcorn.coupon.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.popcorn.coupon.event.external.UserRegisteredEvent
import com.popcorn.coupon.event.external.UserFirstOrderCompletedEvent
import com.popcorn.coupon.event.external.UserBirthdayEvent
import com.popcorn.coupon.service.core.CouponCommandService
import com.popcorn.coupon.service.core.CouponQueryService
import com.popcorn.coupon.domain.entity.TargetType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class UserEventListener(
    private val couponCommandService: CouponCommandService,
    private val couponQueryService: CouponQueryService,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 사용자 회원가입 이벤트 처리
     * - 신규 사용자 환영 쿠폰 자동 발급
     */
    @KafkaListener(
        topics = ["user-events"],
        groupId = "coupon-service-user-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserRegisteredEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        ack: Acknowledgment
    ) {
        try {
            logger.info { "📥 사용자 가입 이벤트 수신: topic=$topic, partition=$partition, offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "USER_REGISTERED") {
                val event = objectMapper.readValue<UserRegisteredEvent>(eventData["data"] as String? ?: message)
                processUserRegisteredEvent(event)
            }

            ack.acknowledge()
            logger.debug { "✅ 사용자 가입 이벤트 처리 완료: offset=$offset" }

        } catch (e: Exception) {
            logger.error(e) { "❌ 사용자 가입 이벤트 처리 실패: offset=$offset, message=$message" }
            ack.acknowledge()
        }
    }

    /**
     * 사용자 첫 주문 완료 이벤트 처리
     * - 첫 구매 완료 쿠폰 발급
     */
    @KafkaListener(
        topics = ["user-events"],
        groupId = "coupon-service-user-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserFirstOrderCompletedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        ack: Acknowledgment
    ) {
        try {
            logger.info { "📥 사용자 첫 주문 완료 이벤트 수신: offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "USER_FIRST_ORDER_COMPLETED") {
                val event = objectMapper.readValue<UserFirstOrderCompletedEvent>(eventData["data"] as String? ?: message)
                processUserFirstOrderCompletedEvent(event)
            }

            ack.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "❌ 사용자 첫 주문 완료 이벤트 처리 실패: offset=$offset, message=$message" }
            ack.acknowledge()
        }
    }

    /**
     * 사용자 생일 이벤트 처리
     * - 생일 축하 쿠폰 발급
     */
    @KafkaListener(
        topics = ["user-events"],
        groupId = "coupon-service-user-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserBirthdayEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        ack: Acknowledgment
    ) {
        try {
            logger.info { "📥 사용자 생일 이벤트 수신: offset=$offset" }

            val eventData = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventData["eventType"] as? String

            if (eventType == "USER_BIRTHDAY") {
                val event = objectMapper.readValue<UserBirthdayEvent>(eventData["data"] as String? ?: message)
                processUserBirthdayEvent(event)
            }

            ack.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "❌ 사용자 생일 이벤트 처리 실패: offset=$offset, message=$message" }
            ack.acknowledge()
        }
    }

    /**
     * 사용자 가입 이벤트 처리 로직
     */
    private fun processUserRegisteredEvent(event: UserRegisteredEvent) = runBlocking {
        logger.info { "🎉 신규 사용자 가입 처리: userId=${event.userId}, email=${event.email}" }

        try {
            // 신규 사용자용 환영 쿠폰 조회
            val welcomeCoupons = couponQueryService.getActiveCouponsByTargetType(listOf(TargetType.NEW_USERS))

            if (welcomeCoupons.isNotEmpty()) {
                for (coupon in welcomeCoupons) {
                    try {
                        val userCoupon = couponCommandService.issueCouponToUser(
                            userId = event.userId,
                            couponId = coupon.id!!,
                            expiredAt = LocalDateTime.now().plusDays(30) // 30일 후 만료
                        )
                        logger.info { "✅ 환영 쿠폰 발급 완료: userId=${event.userId}, couponName=${coupon.name}, userCouponId=${userCoupon.id}" }
                    } catch (e: Exception) {
                        logger.error(e) { "❌ 환영 쿠폰 발급 실패: userId=${event.userId}, couponId=${coupon.id}" }
                    }
                }
            } else {
                logger.info { "ℹ️ 발급할 환영 쿠폰이 없습니다: userId=${event.userId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 신규 사용자 쿠폰 처리 실패: userId=${event.userId}" }
        }
    }

    /**
     * 첫 주문 완료 이벤트 처리 로직
     */
    private fun processUserFirstOrderCompletedEvent(event: UserFirstOrderCompletedEvent) = runBlocking {
        logger.info { "🛒 첫 주문 완료 처리: userId=${event.userId}, orderId=${event.orderId}" }

        try {
            // 첫 구매 완료 쿠폰 조회 (FIRST_PURCHASE 타입)
            val firstPurchaseCoupons = couponQueryService.getActiveCouponsByTargetType(listOf(TargetType.FIRST_PURCHASE))

            if (firstPurchaseCoupons.isNotEmpty()) {
                for (coupon in firstPurchaseCoupons) {
                    try {
                        val userCoupon = couponCommandService.issueCouponToUser(
                            userId = event.userId,
                            couponId = coupon.id!!,
                            expiredAt = LocalDateTime.now().plusDays(60) // 60일 후 만료
                        )
                        logger.info { "✅ 첫 구매 쿠폰 발급 완료: userId=${event.userId}, couponName=${coupon.name}, userCouponId=${userCoupon.id}" }
                    } catch (e: Exception) {
                        logger.error(e) { "❌ 첫 구매 쿠폰 발급 실패: userId=${event.userId}, couponId=${coupon.id}" }
                    }
                }
            } else {
                logger.info { "ℹ️ 발급할 첫 구매 쿠폰이 없습니다: userId=${event.userId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 첫 주문 완료 쿠폰 처리 실패: userId=${event.userId}" }
        }
    }

    /**
     * 생일 이벤트 처리 로직
     */
    private fun processUserBirthdayEvent(event: UserBirthdayEvent) = runBlocking {
        logger.info { "🎂 생일 축하 처리: userId=${event.userId}" }

        try {
            // 생일 축하 쿠폰 조회 (BIRTHDAY 타입)
            val birthdayCoupons = couponQueryService.getActiveCouponsByTargetType(listOf(TargetType.BIRTHDAY))

            if (birthdayCoupons.isNotEmpty()) {
                for (coupon in birthdayCoupons) {
                    try {
                        val userCoupon = couponCommandService.issueCouponToUser(
                            userId = event.userId,
                            couponId = coupon.id!!,
                            expiredAt = LocalDateTime.now().plusDays(30) // 30일 후 만료
                        )
                        logger.info { "✅ 생일 쿠폰 발급 완료: userId=${event.userId}, couponName=${coupon.name}, userCouponId=${userCoupon.id}" }
                    } catch (e: Exception) {
                        logger.error(e) { "❌ 생일 쿠폰 발급 실패: userId=${event.userId}, couponId=${coupon.id}" }
                    }
                }
            } else {
                logger.info { "ℹ️ 발급할 생일 쿠폰이 없습니다: userId=${event.userId}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "❌ 생일 쿠폰 처리 실패: userId=${event.userId}" }
        }
    }
}