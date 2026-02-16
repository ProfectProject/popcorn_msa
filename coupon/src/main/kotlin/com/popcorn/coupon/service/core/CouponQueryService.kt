package com.popcorn.coupon.service.core

import com.popcorn.coupon.domain.entity.*
import com.popcorn.coupon.domain.repository.CouponRepository
import com.popcorn.coupon.domain.repository.UserCouponRepository
import com.popcorn.coupon.domain.repository.CouponHistoryRepository
import com.popcorn.coupon.exception.CouponException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CouponQueryService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponHistoryRepository: CouponHistoryRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 쿠폰 상세 조회
     */
    suspend fun getCouponById(couponId: Long): Coupon = withContext(Dispatchers.IO) {
        couponRepository.findById(couponId).orElseThrow {
            CouponException("쿠폰을 찾을 수 없습니다: couponId=$couponId")
        }
    }

    /**
     * 활성 쿠폰 목록 조회
     */
    suspend fun getActiveCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findActiveCoupons()
    }

    /**
     * 관리자용: 전체 쿠폰 목록 조회 (최신 생성 순)
     */
    suspend fun getAllCoupons(): List<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
    }

    /**
     * ⚡ 성능 최적화: 관리자용 전체 쿠폰 목록 페이지네이션 조회
     */
    suspend fun getAllCouponsWithPagination(pageable: Pageable): Page<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findAll(pageable)
    }

    /**
     * ⚡ 성능 최적화: 페이지네이션을 지원하는 활성 쿠폰 조회
     */
    suspend fun getActiveCouponsWithPagination(pageable: Pageable): Page<Coupon> = withContext(Dispatchers.IO) {
        couponRepository.findActiveCouponsWithPagination(pageable = pageable)
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
     * 상태별 쿠폰 개수 조회
     */
    suspend fun getCouponCountByStatus(status: CouponStatus): Long = withContext(Dispatchers.IO) {
        couponRepository.countByStatus(status)
    }

    /**
     * 사용자 쿠폰 상세 조회
     */
    suspend fun getUserCouponById(userCouponId: Long): UserCoupon = withContext(Dispatchers.IO) {
        userCouponRepository.findById(userCouponId).orElseThrow {
            CouponException("사용자 쿠폰을 찾을 수 없습니다: userCouponId=$userCouponId")
        }
    }

    /**
     * 쿠폰 코드로 사용자 쿠폰 조회
     */
    suspend fun getUserCouponByCode(couponCode: String): UserCoupon? = withContext(Dispatchers.IO) {
        userCouponRepository.findByCouponCode(couponCode)
    }

    /**
     * 주문 ID로 사용자 쿠폰 조회
     */
    suspend fun getUserCouponByOrderId(orderId: Long): UserCoupon? = withContext(Dispatchers.IO) {
        userCouponRepository.findByOrderId(orderId)
    }

    /**
     * 사용자별 쿠폰 목록 조회 (페이징)
     */
    suspend fun getUserCoupons(userId: Long, pageable: Pageable): Page<UserCoupon> = withContext(Dispatchers.IO) {
        userCouponRepository.findByUserId(userId, pageable)
    }

    /**
     * 사용자별 상태별 쿠폰 목록 조회 (페이징)
     */
    suspend fun getUserCouponsByStatus(
        userId: Long,
        status: UserCouponStatus,
        pageable: Pageable
    ): Page<UserCoupon> = withContext(Dispatchers.IO) {
        userCouponRepository.findByUserIdAndStatus(userId, status, pageable)
    }

    /**
     * 사용자 사용 가능한 쿠폰 조회
     */
    suspend fun getAvailableUserCoupons(userId: Long): List<UserCoupon> = withContext(Dispatchers.IO) {
        userCouponRepository.findAvailableCouponsByUserId(userId)
    }

    /**
     * 사용자별 특정 쿠폰 발급 개수 조회
     */
    suspend fun getUserCouponIssuedCount(userId: Long, couponId: Long): Long = withContext(Dispatchers.IO) {
        userCouponRepository.countByUserIdAndCouponId(userId, couponId)
    }

    /**
     * 사용자별 상태별 쿠폰 개수 조회
     */
    suspend fun getUserCouponCountByStatus(userId: Long, status: UserCouponStatus): Long = withContext(Dispatchers.IO) {
        userCouponRepository.findByUserIdAndStatus(
            userId,
            status,
            org.springframework.data.domain.PageRequest.of(0, Int.MAX_VALUE)
        ).totalElements
    }

    /**
     * 주문에 적용 가능한 쿠폰 조회 (할인 계산 포함)
     */
    suspend fun getApplicableCoupons(
        userId: Long,
        orderAmount: BigDecimal,
        targetTypes: List<TargetType> = listOf(TargetType.ALL_USERS)
    ): List<ApplicableCouponInfo> = withContext(Dispatchers.IO) {
        logger.info { "🛒 주문 적용 가능한 쿠폰 조회: userId=$userId, orderAmount=$orderAmount" }

        val availableUserCoupons = userCouponRepository.findAvailableCouponsByUserId(userId)
        val applicableCoupons = mutableListOf<ApplicableCouponInfo>()

        for (userCoupon in availableUserCoupons) {
            try {
                val coupon = getCouponById(userCoupon.couponId)

                // 쿠폰 적용 가능 여부 확인
                if (isCouponApplicableToOrder(coupon, orderAmount, targetTypes)) {
                    val discountAmount = calculateDiscountAmount(coupon, orderAmount)
                    val finalAmount = orderAmount.subtract(discountAmount)

                    applicableCoupons.add(
                        ApplicableCouponInfo(
                            userCoupon = userCoupon,
                            coupon = coupon,
                            discountAmount = discountAmount,
                            finalAmount = finalAmount,
                            discountRate = discountAmount.divide(orderAmount, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal(100))
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn { "쿠폰 적용 가능성 확인 중 오류: userCouponId=${userCoupon.id}, error=${e.message}" }
            }
        }

        // 할인 금액 내림차순 정렬
        applicableCoupons.sortedByDescending { it.discountAmount }
    }

    /**
     * 쿠폰 히스토리 조회
     */
    suspend fun getCouponHistory(couponId: Long, pageable: Pageable): Page<CouponHistory> = withContext(Dispatchers.IO) {
        couponHistoryRepository.findByCouponId(couponId, pageable)
    }

    /**
     * 사용자별 쿠폰 히스토리 조회
     */
    suspend fun getUserCouponHistory(userId: Long, pageable: Pageable): Page<CouponHistory> = withContext(Dispatchers.IO) {
        couponHistoryRepository.findByUserId(userId, pageable)
    }

    /**
     * 사용자 쿠폰별 히스토리 조회
     */
    suspend fun getUserCouponHistoryDetails(userCouponId: Long): List<CouponHistory> = withContext(Dispatchers.IO) {
        couponHistoryRepository.findByUserCouponId(userCouponId)
    }

    /**
     * 기간별 액션별 히스토리 통계 조회
     */
    suspend fun getCouponHistoryStatistics(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CouponHistoryStatistics> = withContext(Dispatchers.IO) {
        val rawData = couponHistoryRepository.countByActionGroupByPeriod(startDate, endDate)
        rawData.map { row ->
            val actionValue = row[0]
            CouponHistoryStatistics(
                action = actionValue.toString(),
                count = row[1] as Long
            )
        }
    }

    /**
     * 쿠폰 적용 가능 여부 확인
     */
    private fun isCouponApplicableToOrder(
        coupon: Coupon,
        orderAmount: BigDecimal,
        targetTypes: List<TargetType>
    ): Boolean {
        // 쿠폰 상태 확인
        if (coupon.status != CouponStatus.ACTIVE) {
            return false
        }

        // 유효 기간 확인
        val now = LocalDateTime.now()
        if (coupon.validFrom.isAfter(now) || coupon.validUntil.isBefore(now)) {
            return false
        }

        // 최소 주문 금액 확인
        if (coupon.minOrderAmount != null && orderAmount < coupon.minOrderAmount) {
            return false
        }

        // 대상 타입 확인
        if (coupon.targetType !in targetTypes && coupon.targetType != TargetType.ALL_USERS) {
            return false
        }

        return true
    }

    /**
     * 할인 금액 계산
     */
    private fun calculateDiscountAmount(coupon: Coupon, orderAmount: BigDecimal): BigDecimal {
        return when (coupon.discountType) {
            DiscountType.AMOUNT -> {
                val discount = coupon.discountAmount ?: BigDecimal.ZERO
                if (coupon.maxDiscountAmount != null) {
                    discount.min(coupon.maxDiscountAmount).min(orderAmount)
                } else {
                    discount.min(orderAmount)
                }
            }
            DiscountType.PERCENTAGE -> {
                val discountRate = (coupon.discountPercentage ?: BigDecimal.ZERO).divide(BigDecimal(100))
                val discount = orderAmount.multiply(discountRate)
                if (coupon.maxDiscountAmount != null) {
                    discount.min(coupon.maxDiscountAmount)
                } else {
                    discount
                }
            }
        }
    }

    /**
     * 적용 가능한 쿠폰 정보 데이터 클래스
     */
    data class ApplicableCouponInfo(
        val userCoupon: UserCoupon,
        val coupon: Coupon,
        val discountAmount: BigDecimal,
        val finalAmount: BigDecimal,
        val discountRate: BigDecimal
    )

    /**
     * 쿠폰 히스토리 통계 데이터 클래스
     */
    data class CouponHistoryStatistics(
        val action: String,
        val count: Long
    )
}
