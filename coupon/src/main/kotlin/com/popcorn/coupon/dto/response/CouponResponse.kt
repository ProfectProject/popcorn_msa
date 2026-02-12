package com.popcorn.coupon.dto.response

import com.popcorn.coupon.domain.entity.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 쿠폰 상세 응답 DTO
 */
data class CouponDetailResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountAmount: BigDecimal?,
    val discountPercentage: BigDecimal?,
    val minOrderAmount: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val totalQuantity: Int?,
    val issuedQuantity: Int,
    val remainingQuantity: Int?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
    val targetType: TargetType,
    val status: CouponStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(coupon: Coupon): CouponDetailResponse {
            return CouponDetailResponse(
                id = coupon.id!!,
                name = coupon.name,
                description = coupon.description,
                discountType = coupon.discountType,
                discountAmount = coupon.discountAmount,
                discountPercentage = coupon.discountPercentage,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                totalQuantity = coupon.totalQuantity,
                issuedQuantity = coupon.issuedQuantity,
                remainingQuantity = coupon.totalQuantity?.let { it - coupon.issuedQuantity },
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil,
                targetType = coupon.targetType,
                status = coupon.status,
                createdAt = coupon.createdAt!!,
                updatedAt = coupon.updatedAt
            )
        }
    }
}

/**
 * 쿠폰 목록 조회 응답 DTO
 */
data class CouponSummaryResponse(
    val id: Long,
    val name: String,
    val discountType: DiscountType,
    val discountAmount: BigDecimal?,
    val discountPercentage: BigDecimal?,
    val totalQuantity: Int?,
    val issuedQuantity: Int,
    val remainingQuantity: Int?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
    val targetType: TargetType,
    val status: CouponStatus,
    val isExpired: Boolean,
    val isAvailable: Boolean
) {
    companion object {
        fun from(coupon: Coupon): CouponSummaryResponse {
            val now = LocalDateTime.now()
            val isExpired = coupon.validUntil.isBefore(now)
            val isAvailable = !isExpired &&
                            coupon.status == CouponStatus.ACTIVE &&
                            coupon.validFrom.isBefore(now) &&
                            (coupon.totalQuantity?.let { coupon.issuedQuantity < it } ?: true)

            return CouponSummaryResponse(
                id = coupon.id!!,
                name = coupon.name,
                discountType = coupon.discountType,
                discountAmount = coupon.discountAmount,
                discountPercentage = coupon.discountPercentage,
                totalQuantity = coupon.totalQuantity,
                issuedQuantity = coupon.issuedQuantity,
                remainingQuantity = coupon.totalQuantity?.let { it - coupon.issuedQuantity },
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil,
                targetType = coupon.targetType,
                status = coupon.status,
                isExpired = isExpired,
                isAvailable = isAvailable
            )
        }
    }
}

/**
 * 사용자 쿠폰 응답 DTO
 */
data class UserCouponResponse(
    val id: Long,
    val couponCode: String,
    val status: UserCouponStatus,
    val orderId: Long?,
    val usedAt: LocalDateTime?,
    val expiredAt: LocalDateTime?,
    val reservedUntil: LocalDateTime?,
    val discountApplied: BigDecimal?,
    val createdAt: LocalDateTime,

    // 쿠폰 정보 포함
    val coupon: CouponSummaryInUserCoupon
) {
    companion object {
        fun from(userCoupon: UserCoupon, coupon: Coupon): UserCouponResponse {
            return UserCouponResponse(
                id = userCoupon.id!!,
                couponCode = userCoupon.couponCode,
                status = userCoupon.status,
                orderId = userCoupon.orderId,
                usedAt = userCoupon.usedAt,
                expiredAt = userCoupon.expiredAt,
                reservedUntil = userCoupon.reservedUntil,
                discountApplied = userCoupon.discountApplied,
                createdAt = userCoupon.createdAt!!,
                coupon = CouponSummaryInUserCoupon.from(coupon)
            )
        }
    }
}

/**
 * 사용자 쿠폰 내 쿠폰 정보 요약 DTO
 */
data class CouponSummaryInUserCoupon(
    val id: Long,
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountAmount: BigDecimal?,
    val discountPercentage: BigDecimal?,
    val minOrderAmount: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
    val isExpired: Boolean,
    val isUsable: Boolean
) {
    companion object {
        fun from(coupon: Coupon): CouponSummaryInUserCoupon {
            val now = LocalDateTime.now()
            val isExpired = coupon.validUntil.isBefore(now)
            val isUsable = !isExpired &&
                         coupon.status == CouponStatus.ACTIVE &&
                         coupon.validFrom.isBefore(now)

            return CouponSummaryInUserCoupon(
                id = coupon.id!!,
                name = coupon.name,
                description = coupon.description,
                discountType = coupon.discountType,
                discountAmount = coupon.discountAmount,
                discountPercentage = coupon.discountPercentage,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil,
                isExpired = isExpired,
                isUsable = isUsable
            )
        }
    }
}

/**
 * 사용자 쿠폰 목록 응답 DTO (페이징)
 */
data class UserCouponListResponse(
    val content: List<UserCouponResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * 적용 가능한 쿠폰 응답 DTO
 */
data class ApplicableCouponResponse(
    val userCouponId: Long,
    val couponCode: String,
    val couponName: String,
    val discountType: DiscountType,
    val discountAmount: BigDecimal,
    val finalAmount: BigDecimal,
    val discountRate: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val description: String?
)

/**
 * 쿠폰 사용 결과 응답 DTO
 */
data class CouponUsageResponse(
    val userCouponId: Long,
    val couponCode: String,
    val orderId: Long,
    val discountAmount: BigDecimal,
    val status: UserCouponStatus,
    val message: String
)

/**
 * 쿠폰 통계 응답 DTO
 */
data class CouponStatisticsResponse(
    val totalCoupons: Long,
    val activeCoupons: Long,
    val issuedCoupons: Long,
    val usedCoupons: Long,
    val expiredCoupons: Long,
    val totalDiscountAmount: BigDecimal
)

/**
 * 사용자 쿠폰 통계 응답 DTO
 */
data class UserCouponStatisticsResponse(
    val totalCoupons: Long,
    val availableCoupons: Long,
    val usedCoupons: Long,
    val expiredCoupons: Long,
    val reservedCoupons: Long,
    val totalSavedAmount: BigDecimal
)

data class CouponHistoryItemResponse(
    val id: Long,
    val userCouponId: Long,
    val action: CouponAction,
    val orderId: Long?,
    val discountAmount: BigDecimal?,
    val reason: String?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(history: CouponHistory): CouponHistoryItemResponse {
            return CouponHistoryItemResponse(
                id = history.id!!,
                userCouponId = history.userCouponId,
                action = history.action,
                orderId = history.orderId,
                discountAmount = history.discountAmount,
                reason = history.reason,
                createdAt = history.createdAt!!
            )
        }
    }
}

data class CouponHistoryListResponse(
    val content: List<CouponHistoryItemResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

data class InternalCouponValidateResponse(
    val valid: Boolean,
    val discountAmount: BigDecimal? = null,
    val errorMessage: String? = null
)

data class InternalCouponApplyResponse(
    val applied: Boolean,
    val discountAmount: BigDecimal? = null,
    val userCouponId: Long? = null,
    val errorMessage: String? = null
)

data class InternalCouponCancelResponse(
    val cancelled: Boolean,
    val errorMessage: String? = null
)
