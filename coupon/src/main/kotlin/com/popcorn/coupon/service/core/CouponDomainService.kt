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

        val updatedCoupon = couponRepository.save(coupon.copy(status = status))

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

        val updatedRows = couponRepository.updateIssuedQuantity(couponId, increment)
        if (updatedRows == 0) {
            throw CouponException("쿠폰 수량 업데이트 대상이 없습니다: couponId=$couponId")
        }
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
                couponRepository.save(coupon.copy(status = CouponStatus.EXPIRED))
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
     * ⚡ 성능 최적화: 쿠폰 수정 (선택적 필드 업데이트)
     */
    suspend fun updateCoupon(
        couponId: Long,
        name: String? = null,
        description: String? = null,
        discountAmount: BigDecimal? = null,
        discountPercentage: BigDecimal? = null,
        minOrderAmount: BigDecimal? = null,
        maxDiscountAmount: BigDecimal? = null,
        totalQuantity: Int? = null,
        validFrom: LocalDateTime? = null,
        validUntil: LocalDateTime? = null,
        targetType: TargetType? = null
    ): Coupon = withContext(Dispatchers.IO) {
        logger.info { "📝 쿠폰 수정 (성능 최적화): couponId=$couponId" }

        val existingCoupon = getCouponById(couponId)

        // 수정 불가능한 상태 확인
        if (existingCoupon.status == CouponStatus.EXPIRED) {
            throw CouponException("만료된 쿠폰은 수정할 수 없습니다")
        }

        // 이미 발급된 쿠폰이 있는 경우 일부 필드만 수정 가능
        if (existingCoupon.issuedQuantity > 0) {
            validateUpdateWithIssuedCoupons(existingCoupon, totalQuantity, validUntil)
        }

        // 수정 후 값 검증 (기존 값과 새 값 병합하여 검증)
        val mergedCoupon = existingCoupon.copy(
            name = name ?: existingCoupon.name,
            description = description ?: existingCoupon.description,
            discountAmount = discountAmount ?: existingCoupon.discountAmount,
            discountPercentage = discountPercentage ?: existingCoupon.discountPercentage,
            minOrderAmount = minOrderAmount ?: existingCoupon.minOrderAmount,
            maxDiscountAmount = maxDiscountAmount ?: existingCoupon.maxDiscountAmount,
            totalQuantity = totalQuantity ?: existingCoupon.totalQuantity,
            validFrom = validFrom ?: existingCoupon.validFrom,
            validUntil = validUntil ?: existingCoupon.validUntil,
            targetType = targetType ?: existingCoupon.targetType
        )
        validateCouponCreation(mergedCoupon)

        try {
            // ⚡ 성능 최적화: 선택적 필드만 업데이트하는 네이티브 쿼리 사용
            val updatedRows = couponRepository.updateCouponFields(
                couponId = couponId,
                name = name,
                description = description,
                discountAmount = discountAmount,
                discountPercentage = discountPercentage,
                minOrderAmount = minOrderAmount,
                maxDiscountAmount = maxDiscountAmount,
                totalQuantity = totalQuantity,
                validFrom = validFrom,
                validUntil = validUntil,
                targetType = targetType?.name
            )

            if (updatedRows == 0) {
                throw CouponException("쿠폰을 찾을 수 없거나 업데이트할 수 없습니다: couponId=$couponId")
            }

            // 업데이트된 쿠폰 반환 (메모리에서 병합된 값 사용)
            val updatedCoupon = mergedCoupon
            logger.info { "✅ 쿠폰 수정 완료 (최적화): couponId=$couponId, 업데이트된 행 수=$updatedRows" }
            updatedCoupon
        } catch (e: CouponException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "❌ 쿠폰 수정 실패: couponId=$couponId" }
            throw CouponException("쿠폰 수정에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 쿠폰 삭제
     */
    suspend fun deleteCoupon(couponId: Long): Unit = withContext(Dispatchers.IO) {
        logger.info { "🗑️ 쿠폰 삭제: couponId=$couponId" }

        val coupon = getCouponById(couponId)

        // 삭제 가능 여부 확인
        validateCouponDeletion(coupon)

        try {
            couponRepository.deleteById(couponId)
            logger.info { "✅ 쿠폰 삭제 완료: couponId=$couponId" }
        } catch (e: Exception) {
            logger.error(e) { "❌ 쿠폰 삭제 실패: couponId=$couponId" }
            throw CouponException("쿠폰 삭제에 실패했습니다: ${e.message}")
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

        // ✅ 멱등성 처리: 같은 상태로의 전이는 허용 (이미 해당 상태인 경우)
        if (from == to) {
            return // 같은 상태로의 전이는 성공으로 처리
        }

        if (to !in (allowedTransitions[from] ?: emptyList())) {
            throw CouponException("잘못된 상태 전이입니다: $from -> $to")
        }
    }

    /**
     * 발급된 쿠폰이 있는 경우 수정 검증
     */
    private fun validateUpdateWithIssuedCoupons(
        existingCoupon: Coupon,
        newTotalQuantity: Int?,
        newValidUntil: LocalDateTime?
    ) {
        // 총 수량을 현재 발급 수량보다 작게 설정하려는 경우
        newTotalQuantity?.let { newTotal ->
            if (newTotal < existingCoupon.issuedQuantity) {
                throw CouponException("총 수량을 현재 발급된 수량(${existingCoupon.issuedQuantity})보다 작게 설정할 수 없습니다")
            }
        }

        // 유효 종료일을 현재 시점보다 이전으로 설정하려는 경우
        newValidUntil?.let { newUntil ->
            if (newUntil.isBefore(LocalDateTime.now())) {
                throw CouponException("이미 발급된 쿠폰이 있어 유효 종료일을 과거로 설정할 수 없습니다")
            }
        }
    }

    /**
     * 쿠폰 삭제 검증
     */
    private fun validateCouponDeletion(coupon: Coupon) {
        // 이미 발급된 쿠폰이 있는 경우 삭제 불가
        if (coupon.issuedQuantity > 0) {
            throw CouponException("이미 발급된 쿠폰이 있어 삭제할 수 없습니다 (발급 수량: ${coupon.issuedQuantity})")
        }

        // 활성화 상태인 경우 삭제 불가 (먼저 비활성화 필요)
        if (coupon.status == CouponStatus.ACTIVE) {
            throw CouponException("활성화된 쿠폰은 삭제할 수 없습니다. 먼저 비활성화해주세요")
        }
    }
}
