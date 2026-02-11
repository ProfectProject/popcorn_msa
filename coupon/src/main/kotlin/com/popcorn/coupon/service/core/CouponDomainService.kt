package com.popcorn.coupon.service.core

import com.popcorn.coupon.domain.entity.Coupon
import com.popcorn.coupon.domain.entity.CouponStatus
import com.popcorn.coupon.domain.entity.TargetType
import com.popcorn.coupon.domain.repository.CouponRepository
import com.popcorn.coupon.exception.CouponException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional
class CouponDomainService(
    private val couponRepository: CouponRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 쿠폰 생성
     */
    suspend fun createCoupon(coupon: Coupon): Coupon = withContext(Dispatchers.IO) {
        logger.info { "🎫 쿠폰 생성 시작: ${coupon.name}" }

        try {
            // 비즈니스 규칙 검증
            validateCouponCreation(coupon)

            val savedCoupon = couponRepository.save(coupon)
            logger.info { "✅ 쿠폰 생성 완료: id=${savedCoupon.id}, name=${savedCoupon.name}" }
            savedCoupon
        } catch (e: Exception) {
            logger.error(e) { "❌ 쿠폰 생성 실패: ${coupon.name}" }
            throw CouponException("쿠폰 생성에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 쿠폰 상태 변경
     */
    suspend fun updateCouponStatus(couponId: Long, status: CouponStatus): Coupon = withContext(Dispatchers.IO) {
        logger.info { "🔄 쿠폰 상태 변경: couponId=$couponId, status=$status" }

        val coupon = getCouponById(couponId)
        validateStatusTransition(coupon.status, status)

        couponRepository.updateStatus(couponId, status)
        val updatedCoupon = coupon.copy(status = status)

        logger.info { "✅ 쿠폰 상태 변경 완료: couponId=$couponId, ${coupon.status} -> $status" }
        updatedCoupon
    }

    /**
     * 쿠폰 발급 수량 증가
     */
    suspend fun incrementIssuedQuantity(couponId: Long, increment: Int = 1): Coupon = withContext(Dispatchers.IO) {
        logger.info { "📈 쿠폰 발급 수량 증가: couponId=$couponId, increment=$increment" }

        val coupon = getCouponById(couponId)

        // 재고 확인
        coupon.totalQuantity?.let { totalQty ->
            if (coupon.issuedQuantity + increment > totalQty) {
                throw CouponException("쿠폰 재고가 부족합니다. 남은 재고: ${totalQty - coupon.issuedQuantity}")
            }
        }

        couponRepository.updateIssuedQuantity(couponId, increment)
        val updatedCoupon = coupon.copy(issuedQuantity = coupon.issuedQuantity + increment)

        logger.info { "✅ 쿠폰 발급 수량 증가 완료: couponId=$couponId, ${coupon.issuedQuantity} -> ${updatedCoupon.issuedQuantity}" }
        updatedCoupon
    }

    /**
     * 활성 쿠폰 조회
     */
    suspend fun getActiveCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findActiveCoupons()
    }

    /**
     * 대상 타입별 활성 쿠폰 조회
     */
    suspend fun getActiveCouponsByTargetType(targetTypes: List<TargetType>): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findActiveCouponsByTargetType(targetTypes)
    }

    /**
     * 발급 가능한 쿠폰 조회
     */
    suspend fun getAvailableCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findAvailableCoupons()
    }

    /**
     * 신규 사용자용 환영 쿠폰 조회
     */
    suspend fun getWelcomeCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findWelcomeCoupons()
    }

    /**
     * 만료된 쿠폰 처리 (배치용)
     */
    suspend fun processExpiredCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        logger.info { "🕐 만료된 쿠폰 처리 시작" }

        val expiredCoupons = couponRepository.findExpiredCoupons()
        logger.info { "📋 만료된 쿠폰 발견: ${expiredCoupons.size}개" }

        expiredCoupons.forEach { coupon ->
            try {
                couponRepository.updateStatus(coupon.id!!, CouponStatus.EXPIRED)
                logger.debug { "✅ 쿠폰 만료 처리 완료: couponId=${coupon.id}" }
            } catch (e: Exception) {
                logger.error(e) { "❌ 쿠폰 만료 처리 실패: couponId=${coupon.id}" }
            }
        }

        expiredCoupons
    }

    /**
     * ID로 쿠폰 조회
     */
    suspend fun getCouponById(couponId: Long): Coupon = withContext(Dispatchers.IO) {
        couponRepository.findById(couponId).orElseThrow {
            CouponException("쿠폰을 찾을 수 없습니다: couponId=$couponId")
        }
    }

    /**
     * 쿠폰 생성 검증
     */
    private fun validateCouponCreation(coupon: Coupon) {
        // 유효기간 검증
        if (coupon.validFrom.isAfter(coupon.validUntil)) {
            throw CouponException("시작일은 종료일보다 앞서야 합니다")
        }

        // 할인 금액/비율 검증
        coupon.discountAmount?.let { discountAmt ->
            if (discountAmt <= BigDecimal.ZERO) {
                throw CouponException("할인 금액은 0보다 커야 합니다")
            }
        }

        coupon.discountPercentage?.let { discountPct ->
            if (discountPct <= BigDecimal.ZERO || discountPct > BigDecimal.valueOf(100)) {
                throw CouponException("할인 비율은 0~100 사이여야 합니다")
            }
        }

        // 수량 검증
        coupon.totalQuantity?.let { totalQty ->
            if (totalQty <= 0) {
                throw CouponException("총 수량은 0보다 커야 합니다")
            }
        }

        // 최소 주문 금액 검증
        if (coupon.minOrderAmount != null && coupon.minOrderAmount < BigDecimal.ZERO) {
            throw CouponException("최소 주문 금액은 0 이상이어야 합니다")
        }
    }

    /**
     * 상태 전이 검증
     */
    private fun validateStatusTransition(from: CouponStatus, to: CouponStatus) {
        val allowedTransitions = mapOf(
            CouponStatus.DRAFT to listOf(CouponStatus.ACTIVE, CouponStatus.INACTIVE),
            CouponStatus.ACTIVE to listOf(CouponStatus.INACTIVE, CouponStatus.EXPIRED),
            CouponStatus.INACTIVE to listOf(CouponStatus.ACTIVE),
            CouponStatus.EXPIRED to emptyList()
        )

        if (to !in (allowedTransitions[from] ?: emptyList())) {
            throw CouponException("잘못된 상태 전이입니다: $from -> $to")
        }
    }
}