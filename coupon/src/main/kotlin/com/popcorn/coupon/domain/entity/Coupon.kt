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
@Table(name = "coupons")
data class Coupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 기본 정보
    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    // 할인 정보
    @Column(name = "discount_type", nullable = false, columnDefinition = "coupons.discount_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    val discountType: DiscountType,

    @Column(name = "discount_amount", precision = 10, scale = 2)
    val discountAmount: BigDecimal? = null,

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    val discountPercentage: BigDecimal? = null,

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    val minOrderAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    val maxDiscountAmount: BigDecimal? = null,

    // 수량 관리
    @Column(name = "total_quantity")
    val totalQuantity: Int? = null,

    @Column(name = "issued_quantity", nullable = false)
    val issuedQuantity: Int = 0,

    @Column(name = "per_user_limit", nullable = false)
    val perUserLimit: Int = 1,

    // 유효기간
    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDateTime,

    @Column(name = "valid_until", nullable = false)
    val validUntil: LocalDateTime,

    // 상태 및 타입
    @Column(name = "status", nullable = false, columnDefinition = "coupons.coupon_status")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    val status: CouponStatus = CouponStatus.ACTIVE,

    @Column(name = "target_type", nullable = false, columnDefinition = "coupons.target_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    val targetType: TargetType = TargetType.ALL_USERS,

    // 추가 조건 (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @ColumnTransformer(write = "?::json")
    @Column(name = "conditions", columnDefinition = "JSON")
    val conditions: JsonNode? = null,

    // 메타데이터
    @Column(name = "created_by")
    val createdBy: Long? = null,

) : BaseEntity() {

    /**
     * 발급 가능 여부 확인
     */
    fun canIssue(): Boolean {
        if (status != CouponStatus.ACTIVE) return false
        if (LocalDateTime.now().isBefore(validFrom)) return false
        if (LocalDateTime.now().isAfter(validUntil)) return false
        return totalQuantity?.let { issuedQuantity < it } ?: true
    }

    /**
     * 남은 재고 수량
     */
    fun remainingQuantity(): Int? {
        return totalQuantity?.let { it - issuedQuantity }
    }

    /**
     * 할인 금액 계산
     */
    fun calculateDiscountAmount(orderAmount: BigDecimal): BigDecimal {
        if (orderAmount < minOrderAmount) {
            throw IllegalArgumentException("최소 주문 금액($minOrderAmount)을 만족하지 않습니다.")
        }

        return when (discountType) {
            DiscountType.AMOUNT -> discountAmount ?: throw IllegalStateException("정액 할인 쿠폰에 할인 금액이 설정되지 않았습니다.")
            DiscountType.PERCENTAGE -> {
                val percentage = discountPercentage ?: throw IllegalStateException("정률 할인 쿠폰에 할인 비율이 설정되지 않았습니다.")
                val calculatedDiscount = orderAmount * percentage / BigDecimal(100)
                maxDiscountAmount?.let { maxAmount ->
                    if (calculatedDiscount > maxAmount) maxAmount else calculatedDiscount
                } ?: calculatedDiscount
            }
        }
    }

    /**
     * 쿠폰 만료 처리
     */
    fun expire(): Coupon {
        return copy(status = CouponStatus.EXPIRED)
    }

    /**
     * 재고 증가
     */
    fun increaseIssuedQuantity(): Coupon {
        return copy(issuedQuantity = issuedQuantity + 1)
    }
}

enum class DiscountType {
    AMOUNT,      // 정액 할인
    PERCENTAGE   // 정률 할인
}

enum class CouponStatus {
    DRAFT,       // 초안
    ACTIVE,      // 활성
    INACTIVE,    // 비활성
    EXPIRED      // 만료
}

enum class TargetType {
    ALL_USERS,       // 전체 사용자
    NEW_USERS,       // 신규 사용자
    VIP_USERS,       // VIP 사용자
    BIRTHDAY,        // 생일 쿠폰
    FIRST_PURCHASE   // 첫 구매 쿠폰
}
