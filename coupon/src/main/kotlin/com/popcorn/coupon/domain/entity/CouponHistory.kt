package com.popcorn.coupon.domain.entity

import com.fasterxml.jackson.databind.JsonNode
import com.popcorn.coupon.domain.entity.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

@Entity
@Table(name = "coupon_history")
data class CouponHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 기본 정보
    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Column(name = "user_coupon_id", nullable = false)
    val userCouponId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    // 주문 정보
    @Column(name = "order_id")
    val orderId: Long? = null,

    @Column(name = "order_amount", precision = 10, scale = 2)
    val orderAmount: BigDecimal? = null,

    // 액션 정보
    @Column(name = "action", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val action: CouponAction,

    @Column(name = "discount_amount", precision = 10, scale = 2)
    val discountAmount: BigDecimal? = null,

    @Column(name = "reason", length = 500)
    val reason: String? = null,

    @Column(name = "cancel_reason", length = 500)
    val cancelReason: String? = null,

    @Column(name = "cancel_amount", precision = 10, scale = 2)
    val cancelAmount: BigDecimal? = null,

    // 컨텍스트 정보
    @Column(name = "context", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    @ColumnTransformer(write = "?::json")
    val context: JsonNode? = null

) : BaseEntity() {
    companion object {
        fun issued(couponId: Long, userCouponId: Long, userId: Long, context: JsonNode? = null): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                action = CouponAction.ISSUED,
                context = context
            )
        }

        fun reserved(
            couponId: Long,
            userCouponId: Long,
            userId: Long,
            orderId: Long,
            orderAmount: BigDecimal,
            context: JsonNode? = null
        ): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                orderId = orderId,
                orderAmount = orderAmount,
                action = CouponAction.RESERVED,
                context = context
            )
        }

        fun used(
            couponId: Long,
            userCouponId: Long,
            userId: Long,
            orderId: Long,
            discountAmount: BigDecimal,
            context: JsonNode? = null
        ): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                orderId = orderId,
                action = CouponAction.USED,
                discountAmount = discountAmount,
                context = context
            )
        }

        fun cancelled(
            couponId: Long,
            userCouponId: Long,
            userId: Long,
            orderId: Long? = null,
            reason: String? = null,
            cancelReason: String? = null,
            cancelAmount: BigDecimal? = null,
            context: JsonNode? = null
        ): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                orderId = orderId,
                action = CouponAction.CANCELLED,
                reason = reason,
                cancelReason = cancelReason,
                cancelAmount = cancelAmount,
                context = context
            )
        }

        fun expired(couponId: Long, userCouponId: Long, userId: Long, context: JsonNode? = null): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                action = CouponAction.EXPIRED,
                context = context
            )
        }

        fun restored(
            couponId: Long,
            userCouponId: Long,
            userId: Long,
            orderId: Long? = null,
            reason: String? = null,
            context: JsonNode? = null
        ): CouponHistory {
            return CouponHistory(
                couponId = couponId,
                userCouponId = userCouponId,
                userId = userId,
                orderId = orderId,
                action = CouponAction.RESTORED,
                reason = reason,
                context = context
            )
        }
    }
}

enum class CouponAction {
    ISSUED,     // 발급
    RESERVED,   // 예약
    USED,       // 사용
    CANCELLED,  // 취소
    EXPIRED,    // 만료
    RESTORED    // 복구
}
