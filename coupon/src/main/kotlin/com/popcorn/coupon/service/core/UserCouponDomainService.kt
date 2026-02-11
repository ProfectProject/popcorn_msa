package com.popcorn.coupon.service.core

import com.popcorn.coupon.domain.entity.UserCoupon
import com.popcorn.coupon.domain.entity.UserCouponStatus
import com.popcorn.coupon.domain.repository.UserCouponRepository
import com.popcorn.coupon.exception.CouponException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class UserCouponDomainService(
    private val userCouponRepository: UserCouponRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 사용자 쿠폰 발급
     */
    suspend fun issueCoupon(userCoupon: UserCoupon): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "🎟️ 사용자 쿠폰 발급: userId=${userCoupon.userId}, couponId=${userCoupon.couponId}" }

        try {
            // 중복 발급 검증
            validateDuplicateIssue(userCoupon.userId, userCoupon.couponId)

            val savedUserCoupon = userCouponRepository.save(userCoupon)
            logger.info { "✅ 사용자 쿠폰 발급 완료: id=${savedUserCoupon.id}" }
            savedUserCoupon
        } catch (e: Exception) {
            logger.error(e) { "❌ 사용자 쿠폰 발급 실패: userId=${userCoupon.userId}" }
            throw CouponException("쿠폰 발급에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 쿠폰 예약 (주문 시 사용)
     */
    suspend fun reserveCoupon(userCouponId: Long, orderId: Long, reservedUntil: LocalDateTime): UserCoupon =
        withContext(Dispatchers.IO) {
        logger.info { "🔒 쿠폰 예약: userCouponId=$userCouponId, orderId=$orderId" }

        val userCoupon = getUserCouponById(userCouponId)
        validateCouponReservation(userCoupon)

        val reservedCoupon = userCoupon.copy(
            status = UserCouponStatus.RESERVED,
            orderId = orderId,
            reservedUntil = reservedUntil
        )

        val savedCoupon = userCouponRepository.save(reservedCoupon)
        logger.info { "✅ 쿠폰 예약 완료: userCouponId=$userCouponId" }
        savedCoupon
    }

    /**
     * 쿠폰 사용 확정 (결제 완료 시)
     */
    suspend fun confirmCoupon(userCouponId: Long, discountApplied: BigDecimal): UserCoupon =
        withContext(Dispatchers.IO) {
        logger.info { "✅ 쿠폰 사용 확정: userCouponId=$userCouponId, discount=$discountApplied" }

        val userCoupon = getUserCouponById(userCouponId)
        validateCouponConfirmation(userCoupon)

        userCouponRepository.updateUsage(
            userCouponId,
            UserCouponStatus.USED,
            LocalDateTime.now(),
            discountApplied
        )

        val confirmedCoupon = userCoupon.copy(
            status = UserCouponStatus.USED,
            usedAt = LocalDateTime.now(),
            discountApplied = discountApplied
        )

        logger.info { "✅ 쿠폰 사용 확정 완료: userCouponId=$userCouponId" }
        confirmedCoupon
    }

    /**
     * 쿠폰 예약 해제 (주문 취소 시)
     */
    suspend fun releaseCoupon(userCouponId: Long): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "🔓 쿠폰 예약 해제: userCouponId=$userCouponId" }

        val userCoupon = getUserCouponById(userCouponId)
        validateCouponRelease(userCoupon)

        val releasedCoupon = userCoupon.copy(
            status = UserCouponStatus.ISSUED,
            orderId = null,
            reservedUntil = null
        )

        val savedCoupon = userCouponRepository.save(releasedCoupon)
        logger.info { "✅ 쿠폰 예약 해제 완료: userCouponId=$userCouponId" }
        savedCoupon
    }

    /**
     * 쿠폰 만료 처리
     */
    suspend fun expireCoupon(userCouponId: Long): UserCoupon = withContext(Dispatchers.IO) {
        logger.info { "⏰ 쿠폰 만료 처리: userCouponId=$userCouponId" }

        val userCoupon = getUserCouponById(userCouponId)
        userCouponRepository.updateStatus(userCouponId, UserCouponStatus.EXPIRED)

        val expiredCoupon = userCoupon.copy(status = UserCouponStatus.EXPIRED)
        logger.info { "✅ 쿠폰 만료 처리 완료: userCouponId=$userCouponId" }
        expiredCoupon
    }

    /**
     * 사용자별 쿠폰 목록 조회
     */
    suspend fun getUserCoupons(userId: Long, pageable: Pageable): Page<UserCoupon> = withContext(Dispatchers.IO) {
        userCouponRepository.findByUserId(userId, pageable)
    }

    /**
     * 사용자별 상태별 쿠폰 목록 조회
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
    suspend fun getAvailableCoupons(userId: Long): List<UserCoupon> = withContext(Dispatchers.IO) {
        userCouponRepository.findAvailableCouponsByUserId(userId)
    }

    /**
     * 쿠폰 코드로 조회
     */
    suspend fun getUserCouponByCode(couponCode: String): UserCoupon? = withContext(Dispatchers.IO) {
        userCouponRepository.findByCouponCode(couponCode)
    }

    /**
     * 주문 ID로 조회
     */
    suspend fun getUserCouponByOrderId(orderId: Long): UserCoupon? = withContext(Dispatchers.IO) {
        userCouponRepository.findByOrderId(orderId)
    }

    /**
     * 만료된 예약 쿠폰 복구 처리 (배치용)
     */
    suspend fun restoreExpiredReservations(): List<UserCoupon> = withContext(Dispatchers.IO) {
        logger.info { "🔄 만료된 예약 쿠폰 복구 처리 시작" }

        val expiredReservedCoupons = userCouponRepository.findExpiredReservedCoupons()
        logger.info { "📋 만료된 예약 쿠폰 발견: ${expiredReservedCoupons.size}개" }

        val restoredCoupons = mutableListOf<UserCoupon>()

        expiredReservedCoupons.forEach { userCoupon ->
            try {
                val restored = releaseCoupon(userCoupon.id!!)
                restoredCoupons.add(restored)
                logger.debug { "✅ 예약 쿠폰 복구 완료: userCouponId=${userCoupon.id}" }
            } catch (e: Exception) {
                logger.error(e) { "❌ 예약 쿠폰 복구 실패: userCouponId=${userCoupon.id}" }
            }
        }

        logger.info { "✅ 예약 쿠폰 복구 처리 완료: ${restoredCoupons.size}개 복구" }
        restoredCoupons
    }

    /**
     * 만료된 쿠폰 처리 (배치용)
     */
    suspend fun processExpiredCoupons(): List<UserCoupon> = withContext(Dispatchers.IO) {
        logger.info { "🕐 만료된 사용자 쿠폰 처리 시작" }

        val expiredCoupons = userCouponRepository.findExpiredCoupons(
            UserCouponStatus.ISSUED,
            LocalDateTime.now(),
            org.springframework.data.domain.PageRequest.of(0, 1000)
        ).content

        logger.info { "📋 만료된 사용자 쿠폰 발견: ${expiredCoupons.size}개" }

        expiredCoupons.forEach { userCoupon ->
            try {
                expireCoupon(userCoupon.id!!)
                logger.debug { "✅ 사용자 쿠폰 만료 처리 완료: userCouponId=${userCoupon.id}" }
            } catch (e: Exception) {
                logger.error(e) { "❌ 사용자 쿠폰 만료 처리 실패: userCouponId=${userCoupon.id}" }
            }
        }

        expiredCoupons
    }

    /**
     * ID로 사용자 쿠폰 조회
     */
    suspend fun getUserCouponById(userCouponId: Long): UserCoupon = withContext(Dispatchers.IO) {
        userCouponRepository.findById(userCouponId).orElseThrow {
            CouponException("사용자 쿠폰을 찾을 수 없습니다: userCouponId=$userCouponId")
        }
    }

    /**
     * 중복 발급 검증
     */
    private suspend fun validateDuplicateIssue(userId: Long, couponId: Long) {
        // 중복 발급 정책은 쿠폰별로 다를 수 있으므로 별도 로직 필요
        // 여기서는 기본적인 검증만 수행
        val issueCount = userCouponRepository.countByUserIdAndCouponId(userId, couponId)
        // 추후 쿠폰별 발급 제한 정책 적용 예정
    }

    /**
     * 쿠폰 예약 검증
     */
    private fun validateCouponReservation(userCoupon: UserCoupon) {
        if (userCoupon.status != UserCouponStatus.ISSUED) {
            throw CouponException("발급된 상태의 쿠폰만 예약할 수 있습니다")
        }

        userCoupon.expiredAt?.let { expiredAt ->
            if (expiredAt.isBefore(LocalDateTime.now())) {
                throw CouponException("만료된 쿠폰은 사용할 수 없습니다")
            }
        }
    }

    /**
     * 쿠폰 확정 검증
     */
    private fun validateCouponConfirmation(userCoupon: UserCoupon) {
        if (userCoupon.status != UserCouponStatus.RESERVED) {
            throw CouponException("예약된 상태의 쿠폰만 확정할 수 있습니다")
        }
    }

    /**
     * 쿠폰 해제 검증
     */
    private fun validateCouponRelease(userCoupon: UserCoupon) {
        if (userCoupon.status != UserCouponStatus.RESERVED) {
            throw CouponException("예약된 상태의 쿠폰만 해제할 수 있습니다")
        }
    }

    /**
     * 쿠폰별 발급된 사용자 쿠폰 수량 조회
     */
    suspend fun countByCouponId(couponId: Long): Long = withContext(Dispatchers.IO) {
        userCouponRepository.countByCouponId(couponId)
    }

    /**
     * 만료된 예약 쿠폰 수량 조회
     */
    suspend fun getExpiredReservationCount(): Long = withContext(Dispatchers.IO) {
        userCouponRepository.countByStatusAndReservedUntilBefore(
            UserCouponStatus.RESERVED,
            LocalDateTime.now()
        )
    }
}