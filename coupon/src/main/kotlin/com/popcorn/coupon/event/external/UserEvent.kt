package com.popcorn.coupon.event.external

import java.time.LocalDateTime

/**
 * 사용자 회원가입 이벤트
 */
data class UserRegisteredEvent(
    val userId: Long,
    val email: String,
    val username: String,
    val registeredAt: LocalDateTime,
    val referralCode: String? = null,
    val timestamp: String
)

/**
 * 사용자 첫 주문 완료 이벤트
 */
data class UserFirstOrderCompletedEvent(
    val userId: Long,
    val orderId: Long,
    val completedAt: LocalDateTime,
    val timestamp: String
)

/**
 * 사용자 생일 이벤트
 */
data class UserBirthdayEvent(
    val userId: Long,
    val birthday: LocalDateTime,
    val timestamp: String
)