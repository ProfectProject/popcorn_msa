package com.popcorn.coupon.event.external

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 결제 완료 이벤트
 */
data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val status: String,
    val paymentMethod: String,
    val userCouponId: Long? = null,
    val discountAmount: BigDecimal? = null,
    val completedAt: LocalDateTime,
    val timestamp: String
)

/**
 * 결제 실패 이벤트
 */
data class PaymentFailedEvent(
    val paymentId: Long?,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val status: String,
    val failureReason: String,
    val userCouponId: Long? = null,
    val failedAt: LocalDateTime,
    val timestamp: String
)

/**
 * 결제 취소 이벤트
 */
data class PaymentCancelledEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val status: String,
    val cancelReason: String,
    val userCouponId: Long? = null,
    val discountAmount: BigDecimal? = null,
    val cancelledAt: LocalDateTime,
    val timestamp: String
)