package com.popcorn.coupon.domain.entity

import com.fasterxml.jackson.databind.JsonNode
import com.popcorn.coupon.domain.entity.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    indexes = [
        Index(name = "idx_user_coupons_user_id", columnList = "user_id"),
        Index(name = "idx_user_coupons_coupon_id", columnList = "coupon_id"),
        Index(name = "idx_user_coupons_code", columnList = "coupon_code"),
        Index(name = "idx_user_coupons_status", columnList = "status"),
        Index(name = "idx_user_coupons_user_status", columnList = "user_id, status"),
        Index(name = "idx_user_coupons_order_id", columnList = "order_id")
    ]
)
data class UserCoupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 기본 정보
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Column(name = "coupon_code", nullable = false, unique = true, length = 50)
    val couponCode: String,

    // 상태 관리 (개선된 상태 머신)
    @Column(name = "status", nullable = false, columnDefinition = "coupons.user_coupon_status")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    val status: UserCouponStatus = UserCouponStatus.ISSUED,

    // 시간 정보 (상태 전이 추적)
    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "reserved_at")
    val reservedAt: LocalDateTime? = null,

    @Column(name = "reserved_until")
    val reservedUntil: LocalDateTime? = null,

    @Column(name = "used_at")
    val usedAt: LocalDateTime? = null,

    @Column(name = "expired_at")
    val expiredAt: LocalDateTime? = null,

    // 사용 정보
    @Column(name = "order_id")
    val orderId: Long? = null,

    @Column(name = "discount_applied", precision = 10, scale = 2)
    val discountApplied: BigDecimal? = null,

    // 메타데이터
    @Column(name = "metadata", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    @ColumnTransformer(write = "?::json")
    val metadata: JsonNode? = null

) : BaseEntity() {

    /**
     * 사용 가능 여부 확인
     */
    fun isAvailable(): Boolean {
        if (status != UserCouponStatus.ISSUED) return false
        return expiredAt?.let { LocalDateTime.now().isBefore(it) } ?: true
    }

    /**
     * 쿠폰 예약 (결제 시작)
     */
    fun reserve(orderId: Long): UserCoupon {
        require(status == UserCouponStatus.ISSUED) { "쿠폰이 발급 상태가 아닙니다: $status" }

        return copy(
            status = UserCouponStatus.RESERVED,
            reservedAt = LocalDateTime.now(),
            reservedUntil = LocalDateTime.now().plusMinutes(10), // 10분 예약 시간
            orderId = orderId
        )
    }

    /**
     * 쿠폰 사용 확정 (결제 완료)
     */
    fun confirm(discountAmount: BigDecimal): UserCoupon {
        require(status == UserCouponStatus.RESERVED) { "쿠폰이 예약 상태가 아닙니다: $status" }

        return copy(
            status = UserCouponStatus.USED,
            usedAt = LocalDateTime.now(),
            discountApplied = discountAmount
        )
    }

    /**
     * 쿠폰 복구 (결제 실패)
     */
    fun restore(): UserCoupon {
        require(status == UserCouponStatus.RESERVED) { "쿠폰이 예약 상태가 아닙니다: $status" }

        return copy(
            status = UserCouponStatus.ISSUED,
            reservedAt = null,
            reservedUntil = null,
            orderId = null
        )
    }

    /**
     * 쿠폰 만료 처리
     */
    fun expire(): UserCoupon {
        return copy(
            status = UserCouponStatus.EXPIRED,
            expiredAt = LocalDateTime.now()
        )
    }

}

enum class UserCouponStatus {
    ISSUED,     // 발급됨 (사용 가능)
    RESERVED,   // 예약됨 (결제 진행 중)
    USED,       // 사용됨 (사용 완료)
    EXPIRED     // 만료됨
}
