package com.popcorn.coupon.controller

import com.popcorn.coupon.domain.entity.UserCouponStatus
import com.popcorn.coupon.dto.request.InternalCouponApplyRequest
import com.popcorn.coupon.dto.request.InternalCouponCancelRequest
import com.popcorn.coupon.dto.request.InternalCouponValidateRequest
import com.popcorn.coupon.dto.response.InternalCouponApplyResponse
import com.popcorn.coupon.dto.response.InternalCouponCancelResponse
import com.popcorn.coupon.dto.response.InternalCouponValidateResponse
import com.popcorn.coupon.exception.CouponException
import com.popcorn.coupon.service.core.CouponCommandService
import com.popcorn.coupon.service.core.CouponQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/internal/coupons")
@Tag(name = "Internal Coupons", description = "서비스 간 내부 쿠폰 API")
class InternalCouponController(
    private val couponQueryService: CouponQueryService,
    private val couponCommandService: CouponCommandService
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(summary = "쿠폰 유효성 검증", description = "내부 서비스 호출용 쿠폰 유효성 검증 API")
    fun validateCoupon(
        @Valid @RequestBody request: InternalCouponValidateRequest
    ): InternalCouponValidateResponse = runBlocking {
        try {
            val userCoupon = couponQueryService.getUserCouponByCode(request.couponCode)
                ?: return@runBlocking InternalCouponValidateResponse(valid = false, errorMessage = "쿠폰을 찾을 수 없습니다")

            if (userCoupon.userId != request.userId) {
                return@runBlocking InternalCouponValidateResponse(valid = false, errorMessage = "사용자 쿠폰이 아닙니다")
            }
            if (userCoupon.status != UserCouponStatus.ISSUED) {
                return@runBlocking InternalCouponValidateResponse(valid = false, errorMessage = "사용 가능한 상태가 아닙니다")
            }
            val expiredAt = userCoupon.expiredAt
            if (expiredAt != null && expiredAt.isBefore(java.time.LocalDateTime.now())) {
                return@runBlocking InternalCouponValidateResponse(valid = false, errorMessage = "만료된 쿠폰입니다")
            }

            val coupon = couponQueryService.getCouponById(userCoupon.couponId)
            if (coupon.minOrderAmount > request.orderAmount) {
                return@runBlocking InternalCouponValidateResponse(valid = false, errorMessage = "최소 주문 금액 미충족")
            }

            val discountAmount = calculateDiscountAmount(coupon, request.orderAmount)
            InternalCouponValidateResponse(valid = true, discountAmount = discountAmount)
        } catch (e: Exception) {
            logger.error(e) { "내부 쿠폰 검증 실패: couponCode=${request.couponCode}" }
            InternalCouponValidateResponse(valid = false, errorMessage = e.message ?: "검증 실패")
        }
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(summary = "쿠폰 사용 적용", description = "내부 서비스 호출용 쿠폰 사용 예약 API")
    fun applyCoupon(
        @Valid @RequestBody request: InternalCouponApplyRequest
    ): InternalCouponApplyResponse = runBlocking {
        try {
            val userCoupon = couponQueryService.getUserCouponByCode(request.couponCode)
                ?: return@runBlocking InternalCouponApplyResponse(applied = false, errorMessage = "쿠폰을 찾을 수 없습니다")
            if (userCoupon.userId != request.userId) {
                return@runBlocking InternalCouponApplyResponse(applied = false, errorMessage = "사용자 쿠폰이 아닙니다")
            }

            val updated = couponCommandService.useCoupon(
                userId = request.userId,
                userCouponId = userCoupon.id!!,
                orderId = request.orderId,
                orderAmount = request.orderAmount
            )
            val coupon = couponQueryService.getCouponById(updated.couponId)
            val discountAmount = calculateDiscountAmount(coupon, request.orderAmount)

            InternalCouponApplyResponse(
                applied = true,
                discountAmount = discountAmount,
                userCouponId = updated.id
            )
        } catch (e: Exception) {
            logger.error(e) { "내부 쿠폰 적용 실패: couponCode=${request.couponCode}, orderId=${request.orderId}" }
            InternalCouponApplyResponse(applied = false, errorMessage = e.message ?: "적용 실패")
        }
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
    @Operation(summary = "쿠폰 사용 취소", description = "내부 서비스 호출용 쿠폰 사용 취소 API")
    fun cancelCoupon(
        @Valid @RequestBody request: InternalCouponCancelRequest
    ): InternalCouponCancelResponse = runBlocking {
        try {
            couponCommandService.cancelCouponUsage(
                userCouponId = request.userCouponId,
                orderId = request.orderId
            )
            InternalCouponCancelResponse(cancelled = true)
        } catch (e: Exception) {
            logger.error(e) { "내부 쿠폰 취소 실패: userCouponId=${request.userCouponId}, orderId=${request.orderId}" }
            InternalCouponCancelResponse(cancelled = false, errorMessage = e.message ?: "취소 실패")
        }
    }

    private fun calculateDiscountAmount(coupon: com.popcorn.coupon.domain.entity.Coupon, orderAmount: BigDecimal): BigDecimal {
        return try {
            coupon.calculateDiscountAmount(orderAmount)
        } catch (e: IllegalArgumentException) {
            throw CouponException(e.message ?: "할인 계산 실패")
        }
    }
}
