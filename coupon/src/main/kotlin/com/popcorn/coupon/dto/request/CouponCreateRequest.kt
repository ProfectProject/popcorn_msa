package com.popcorn.coupon.dto.request

import com.fasterxml.jackson.annotation.JsonAlias
import com.popcorn.coupon.domain.entity.DiscountType
import com.popcorn.coupon.domain.entity.TargetType
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 쿠폰 생성 요청 DTO
 */
data class CouponCreateRequest(
    @JsonAlias("name")
    @field:NotBlank(message = "쿠폰명은 필수입니다")
    @field:Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다")
    val name: String,

    @JsonAlias("description")
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String?,

    @JsonAlias("discountType")
    @field:NotNull(message = "할인 타입은 필수입니다")
    val discountType: DiscountType,

    @JsonAlias("discountAmount")
    @field:DecimalMin(value = "0.01", message = "할인 금액은 0.01 이상이어야 합니다")
    val discountAmount: BigDecimal?,

    @JsonAlias("discountPercentage")
    @field:Min(value = 1, message = "할인 비율은 1% 이상이어야 합니다")
    @field:Max(value = 100, message = "할인 비율은 100% 이하여야 합니다")
    val discountPercentage: Int?,

    @JsonAlias("minOrderAmount")
    @field:DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다")
    val minOrderAmount: BigDecimal?,

    @JsonAlias("maxDiscountAmount")
    @field:DecimalMin(value = "0.01", message = "최대 할인 금액은 0.01 이상이어야 합니다")
    val maxDiscountAmount: BigDecimal?,

    @JsonAlias("totalQuantity")
    @field:Min(value = 1, message = "총 수량은 1개 이상이어야 합니다")
    val totalQuantity: Int?,

    @JsonAlias("validFrom")
    @field:NotNull(message = "유효 시작일은 필수입니다")
    val validFrom: LocalDateTime,

    @JsonAlias("validUntil")
    @field:NotNull(message = "유효 종료일은 필수입니다")
    val validUntil: LocalDateTime,

    @JsonAlias("targetType")
    @field:NotNull(message = "대상 타입은 필수입니다")
    val targetType: TargetType
)

/**
 * 쿠폰 상태 업데이트 요청 DTO
 */
data class CouponStatusUpdateRequest(
    @field:NotBlank(message = "상태는 필수입니다")
    val status: String
)

/**
 * 사용자 쿠폰 발급 요청 DTO
 */
data class CouponIssueRequest(
    @field:NotNull(message = "쿠폰 ID는 필수입니다")
    val couponId: Long,

    @field:NotNull(message = "사용자 ID는 필수입니다")
    val userId: Long,

    val expiredAt: LocalDateTime?
)

/**
 * 사용자 쿠폰 다운로드 요청 DTO
 */
data class CouponDownloadRequest(
    @field:NotNull(message = "쿠폰 ID는 필수입니다")
    val couponId: Long,

    val couponCode: String? = null
)

/**
 * 쿠폰 사용 요청 DTO
 */
data class CouponUsageRequest(
    @field:NotNull(message = "사용자 쿠폰 ID는 필수입니다")
    val userCouponId: Long,

    @field:NotNull(message = "주문 ID는 필수입니다")
    val orderId: Long,

    @field:NotNull(message = "주문 금액은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "주문 금액은 0.01 이상이어야 합니다")
    val orderAmount: BigDecimal
)

/**
 * 쿠폰 사용 확정 요청 DTO
 */
data class CouponUsageConfirmRequest(
    @field:NotNull(message = "주문 ID는 필수입니다")
    val orderId: Long,

    @field:NotNull(message = "실제 할인 금액은 필수입니다")
    @field:DecimalMin(value = "0", message = "할인 금액은 0 이상이어야 합니다")
    val actualDiscountAmount: BigDecimal
)

/**
 * 쿠폰 사용 취소 요청 DTO
 */
data class CouponUsageCancelRequest(
    @field:NotNull(message = "주문 ID는 필수입니다")
    val orderId: Long
)

/**
 * 내부 쿠폰 검증 요청 DTO
 */
data class InternalCouponValidateRequest(
    @field:NotBlank(message = "쿠폰 코드는 필수입니다")
    val couponCode: String,

    @field:NotNull(message = "사용자 ID는 필수입니다")
    val userId: Long,

    @field:NotNull(message = "주문 금액은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "주문 금액은 0.01 이상이어야 합니다")
    val orderAmount: BigDecimal
)

/**
 * 내부 쿠폰 적용 요청 DTO
 */
data class InternalCouponApplyRequest(
    @field:NotBlank(message = "쿠폰 코드는 필수입니다")
    val couponCode: String,

    @field:NotNull(message = "사용자 ID는 필수입니다")
    val userId: Long,

    @field:NotNull(message = "주문 ID는 필수입니다")
    val orderId: Long,

    @field:NotNull(message = "주문 금액은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "주문 금액은 0.01 이상이어야 합니다")
    val orderAmount: BigDecimal
)

/**
 * 내부 쿠폰 취소 요청 DTO
 */
data class InternalCouponCancelRequest(
    @field:NotNull(message = "사용자 쿠폰 ID는 필수입니다")
    val userCouponId: Long,

    @field:NotNull(message = "주문 ID는 필수입니다")
    val orderId: Long,

    val reason: String? = null,
    val cancelReason: String? = null,
    val cancelAmount: BigDecimal? = null
)

/**
 * 쿠폰 수정 요청 DTO
 */
data class CouponUpdateRequest(
    @JsonAlias("name")
    @field:Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다")
    val name: String?,

    @JsonAlias("description")
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String?,

    @JsonAlias("discountAmount")
    @field:DecimalMin(value = "0.01", message = "할인 금액은 0.01 이상이어야 합니다")
    val discountAmount: BigDecimal?,

    @JsonAlias("discountPercentage")
    @field:Min(value = 1, message = "할인 비율은 1% 이상이어야 합니다")
    @field:Max(value = 100, message = "할인 비율은 100% 이하여야 합니다")
    val discountPercentage: Int?,

    @JsonAlias("minOrderAmount")
    @field:DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다")
    val minOrderAmount: BigDecimal?,

    @JsonAlias("maxDiscountAmount")
    @field:DecimalMin(value = "0.01", message = "최대 할인 금액은 0.01 이상이어야 합니다")
    val maxDiscountAmount: BigDecimal?,

    @JsonAlias("totalQuantity")
    @field:Min(value = 1, message = "총 수량은 1개 이상이어야 합니다")
    val totalQuantity: Int?,

    @JsonAlias("validFrom")
    val validFrom: LocalDateTime?,

    @JsonAlias("validUntil")
    val validUntil: LocalDateTime?,

    @JsonAlias("targetType")
    val targetType: TargetType?
)
