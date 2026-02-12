package com.popcorn.coupon.service.event

import com.popcorn.coupon.domain.entity.Coupon
import com.popcorn.coupon.domain.entity.CouponOutboxEvent
import com.popcorn.coupon.domain.entity.UserCoupon
import com.popcorn.coupon.domain.repository.CouponOutboxEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import com.fasterxml.jackson.databind.ObjectMapper

@Service
@Transactional
class CouponEventPublisher(
    private val couponOutboxEventRepository: CouponOutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 쿠폰 생성 이벤트 발행
     */
    suspend fun publishCouponCreatedEvent(coupon: Coupon, adminId: Long) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "couponId" to coupon.id,
            "name" to coupon.name,
            "discountType" to coupon.discountType.name,
            "discountAmount" to coupon.discountAmount,
            "discountPercentage" to coupon.discountPercentage,
            "validFrom" to coupon.validFrom.toString(),
            "validUntil" to coupon.validUntil.toString(),
            "targetType" to coupon.targetType.name,
            "status" to coupon.status.name,
            "adminId" to adminId,
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_CREATED",
            aggregateId = coupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 생성 이벤트 발행: couponId=${coupon.id}" }
    }

    /**
     * 쿠폰 활성화 이벤트 발행
     */
    suspend fun publishCouponActivatedEvent(coupon: Coupon, adminId: Long) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "couponId" to coupon.id,
            "name" to coupon.name,
            "status" to coupon.status.name,
            "adminId" to adminId,
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_ACTIVATED",
            aggregateId = coupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 활성화 이벤트 발행: couponId=${coupon.id}" }
    }

    /**
     * 쿠폰 비활성화 이벤트 발행
     */
    suspend fun publishCouponDeactivatedEvent(coupon: Coupon, adminId: Long) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "couponId" to coupon.id,
            "name" to coupon.name,
            "status" to coupon.status.name,
            "adminId" to adminId,
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_DEACTIVATED",
            aggregateId = coupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 비활성화 이벤트 발행: couponId=${coupon.id}" }
    }

    /**
     * 쿠폰 발급 이벤트 발행
     */
    suspend fun publishCouponIssuedEvent(userCoupon: UserCoupon, coupon: Coupon) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponCode" to userCoupon.couponCode,
            "couponName" to coupon.name,
            "discountType" to coupon.discountType.name,
            "discountAmount" to coupon.discountAmount,
            "discountPercentage" to coupon.discountPercentage,
            "expiredAt" to userCoupon.expiredAt?.toString(),
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_ISSUED",
            aggregateId = userCoupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 발급 이벤트 발행: userCouponId=${userCoupon.id}" }
    }

    /**
     * 쿠폰 예약 이벤트 발행
     */
    suspend fun publishCouponReservedEvent(
        userCoupon: UserCoupon,
        coupon: Coupon,
        orderId: Long,
        discountAmount: BigDecimal
    ) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponCode" to userCoupon.couponCode,
            "orderId" to orderId,
            "discountAmount" to discountAmount,
            "reservedUntil" to userCoupon.reservedUntil?.toString(),
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_RESERVED",
            aggregateId = userCoupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 예약 이벤트 발행: userCouponId=${userCoupon.id}, orderId=$orderId" }
    }

    /**
     * 쿠폰 사용 확정 이벤트 발행
     */
    suspend fun publishCouponUsedEvent(
        userCoupon: UserCoupon,
        coupon: Coupon,
        orderId: Long,
        discountAmount: BigDecimal
    ) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponCode" to userCoupon.couponCode,
            "orderId" to orderId,
            "discountAmount" to discountAmount,
            "usedAt" to userCoupon.usedAt?.toString(),
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_USED",
            aggregateId = userCoupon.id.toString(),
            payload = eventData
        )

        // 분석용 이벤트도 별도 발행
        publishCouponAnalyticsEvent("COUPON_USAGE", userCoupon, coupon, orderId, discountAmount)

        logger.info { "📢 쿠폰 사용 확정 이벤트 발행: userCouponId=${userCoupon.id}, orderId=$orderId" }
    }

    /**
     * 쿠폰 사용 취소 이벤트 발행
     */
    suspend fun publishCouponReleasedEvent(
        userCoupon: UserCoupon,
        coupon: Coupon,
        orderId: Long
    ) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponCode" to userCoupon.couponCode,
            "orderId" to orderId,
            "status" to userCoupon.status.name,
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_RELEASED",
            aggregateId = userCoupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 사용 취소 이벤트 발행: userCouponId=${userCoupon.id}, orderId=$orderId" }
    }

    /**
     * 쿠폰 만료 이벤트 발행
     */
    suspend fun publishCouponExpiredEvent(userCoupon: UserCoupon, coupon: Coupon) = withContext(Dispatchers.IO) {
        val eventData = mapOf(
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponCode" to userCoupon.couponCode,
            "expiredAt" to userCoupon.expiredAt?.toString(),
            "timestamp" to LocalDateTime.now().toString()
        )

        publishOutboxEvent(
            eventType = "COUPON_EXPIRED",
            aggregateId = userCoupon.id.toString(),
            payload = eventData
        )

        logger.info { "📢 쿠폰 만료 이벤트 발행: userCouponId=${userCoupon.id}" }
    }

    /**
     * 쿠폰 분석 이벤트 발행 (별도 토픽)
     */
    private suspend fun publishCouponAnalyticsEvent(
        eventType: String,
        userCoupon: UserCoupon,
        coupon: Coupon,
        orderId: Long? = null,
        discountAmount: BigDecimal? = null
    ) = withContext(Dispatchers.IO) {
        val analyticsData = mapOf(
            "eventType" to eventType,
            "userCouponId" to userCoupon.id,
            "userId" to userCoupon.userId,
            "couponId" to userCoupon.couponId,
            "couponName" to coupon.name,
            "discountType" to coupon.discountType.name,
            "targetType" to coupon.targetType.name,
            "orderId" to orderId,
            "discountAmount" to discountAmount,
            "timestamp" to LocalDateTime.now().toString(),
            "date" to LocalDateTime.now().toLocalDate().toString(),
            "hour" to LocalDateTime.now().hour
        )

        val outboxEvent = CouponOutboxEvent(
            aggregateType = "COUPON_ANALYTICS",
            aggregateId = "${userCoupon.userId}_${LocalDateTime.now().toLocalDate()}",
            eventType = eventType,
            eventData = objectMapper.valueToTree(analyticsData)
        )

        couponOutboxEventRepository.save(outboxEvent)
        logger.debug { "📊 쿠폰 분석 이벤트 생성: $eventType" }
    }

    /**
     * Outbox 이벤트 발행
     */
    private suspend fun publishOutboxEvent(
        eventType: String,
        aggregateId: String,
        payload: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        val outboxEvent = CouponOutboxEvent(
            aggregateType = "COUPON",
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = objectMapper.valueToTree(payload)
        )

        couponOutboxEventRepository.save(outboxEvent)
        logger.debug { "💾 Outbox 이벤트 저장: $eventType, aggregateId=$aggregateId" }
    }

    /**
     * 미발행 이벤트 수량 조회
     */
    suspend fun getUnpublishedEventCount(): Long = withContext(Dispatchers.IO) {
        couponOutboxEventRepository.countByProcessedAtIsNull()
    }
}