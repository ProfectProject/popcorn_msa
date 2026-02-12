package com.popcorn.coupon.event.external

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 생성 이벤트
 */
data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val status: String,
    val couponId: Long? = null,
    val userCouponId: Long? = null,
    val expectedDiscountAmount: BigDecimal? = null,
    val createdAt: LocalDateTime,
    val timestamp: String
)

/**
 * 주문 취소 이벤트
 */
data class OrderCancelledEvent(
    val orderId: Long,
    val userId: Long,
    val status: String,
    val userCouponId: Long? = null,
    val reason: String? = null,
    val cancelledAt: LocalDateTime,
    val timestamp: String
)

/**
 * 주문 완료 이벤트
 */
data class OrderCompletedEvent(
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val finalAmount: BigDecimal,
    val status: String,
    val userCouponId: Long? = null,
    val actualDiscountAmount: BigDecimal? = null,
    val completedAt: LocalDateTime,
    val timestamp: String
)

/**
 * 재고 차감 성공 이벤트
 */
data class StockDeductionSuccessEvent(
    val orderId: Long,
    val userId: Long,
    val userCouponId: Long? = null,
    val timestamp: String
)