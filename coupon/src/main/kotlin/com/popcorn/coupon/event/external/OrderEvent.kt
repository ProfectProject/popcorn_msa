package com.popcorn.coupon.event.external

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 생성 이벤트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderCreatedEvent(
    @JsonAlias("orderId", "order_id")
    val orderId: String? = null,
    @JsonAlias("userId", "user_id")
    val userId: Long? = null,
    @JsonAlias("totalAmount", "total_amount")
    val totalAmount: BigDecimal? = null,
    @JsonAlias("status")
    val status: String? = null,
    @JsonAlias("couponId", "coupon_id")
    val couponId: Long? = null,
    @JsonAlias("userCouponId", "user_coupon_id")
    val userCouponId: Long? = null,
    @JsonAlias("expectedDiscountAmount", "expected_discount_amount")
    val expectedDiscountAmount: BigDecimal? = null,
    @JsonAlias("createdAt", "created_at")
    val createdAt: LocalDateTime? = null,
    @JsonAlias("timestamp")
    val timestamp: String? = null
)

/**
 * 주문 취소 이벤트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderCancelledEvent(
    @JsonAlias("orderId", "order_id")
    val orderId: String? = null,
    @JsonAlias("userId", "user_id")
    val userId: Long? = null,
    @JsonAlias("status")
    val status: String? = null,
    @JsonAlias("userCouponId", "user_coupon_id")
    val userCouponId: Long? = null,
    @JsonAlias("reason")
    val reason: String? = null,
    @JsonAlias("cancelledAt", "cancelled_at")
    val cancelledAt: LocalDateTime? = null,
    @JsonAlias("timestamp")
    val timestamp: String? = null
)

/**
 * 주문 완료 이벤트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderCompletedEvent(
    @JsonAlias("orderId", "order_id")
    val orderId: String? = null,
    @JsonAlias("userId", "user_id")
    val userId: Long? = null,
    @JsonAlias("totalAmount", "total_amount")
    val totalAmount: BigDecimal? = null,
    @JsonAlias("finalAmount", "final_amount")
    val finalAmount: BigDecimal? = null,
    @JsonAlias("status")
    val status: String? = null,
    @JsonAlias("userCouponId", "user_coupon_id")
    val userCouponId: Long? = null,
    @JsonAlias("actualDiscountAmount", "actual_discount_amount")
    val actualDiscountAmount: BigDecimal? = null,
    @JsonAlias("completedAt", "completed_at")
    val completedAt: LocalDateTime? = null,
    @JsonAlias("timestamp")
    val timestamp: String? = null
)

/**
 * 재고 차감 성공 이벤트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StockDeductionSuccessEvent(
    @JsonAlias("orderId", "order_id")
    val orderId: String? = null,
    @JsonAlias("userId", "user_id")
    val userId: Long? = null,
    @JsonAlias("userCouponId", "user_coupon_id")
    val userCouponId: Long? = null,
    @JsonAlias("timestamp")
    val timestamp: String? = null
)
