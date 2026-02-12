package com.popcorn.coupon.domain.repository

import com.popcorn.coupon.domain.entity.UserCoupon
import com.popcorn.coupon.domain.entity.UserCouponStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface UserCouponRepository : JpaRepository<UserCoupon, Long> {

    /**
     * 쿠폰 코드로 조회
     */
    fun findByCouponCode(couponCode: String): UserCoupon?

    /**
     * 주문 ID로 조회
     */
    fun findByOrderId(orderId: Long): UserCoupon?

    /**
     * 사용자별 쿠폰 목록 조회 (페이징)
     */
    @Query("""
        SELECT uc FROM UserCoupon uc
        WHERE uc.userId = :userId
        ORDER BY uc.createdAt DESC
    """)
    fun findByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<UserCoupon>

    /**
     * 사용자별 상태별 쿠폰 목록 조회 (페이징)
     */
    @Query("""
        SELECT uc FROM UserCoupon uc
        WHERE uc.userId = :userId
        AND uc.status = :status
        ORDER BY uc.createdAt DESC
    """)
    fun findByUserIdAndStatus(
        @Param("userId") userId: Long,
        @Param("status") status: UserCouponStatus,
        pageable: Pageable
    ): Page<UserCoupon>

    /**
     * 사용자 사용 가능한 쿠폰 조회
     */
    @Query("""
        SELECT uc FROM UserCoupon uc
        WHERE uc.userId = :userId
        AND uc.status = :status
        AND (uc.expiredAt IS NULL OR uc.expiredAt > :now)
        AND EXISTS (
            SELECT 1 FROM Coupon c
            WHERE c.id = uc.couponId
            AND c.status = 'ACTIVE'
        )
        ORDER BY uc.createdAt DESC
    """)
    fun findAvailableCouponsByUserId(
        @Param("userId") userId: Long,
        @Param("status") status: UserCouponStatus = UserCouponStatus.ISSUED,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<UserCoupon>

    /**
     * 사용자별 특정 쿠폰 발급 개수 조회
     */
    fun countByUserIdAndCouponId(userId: Long, couponId: Long): Long

    /**
     * 만료된 쿠폰 조회 (배치 처리용)
     */
    @Query("""
        SELECT uc FROM UserCoupon uc
        WHERE uc.status = :status
        AND uc.expiredAt < :now
        ORDER BY uc.expiredAt ASC
    """,
    nativeQuery = false)
    fun findExpiredCoupons(
        @Param("status") status: UserCouponStatus = UserCouponStatus.ISSUED,
        @Param("now") now: LocalDateTime = LocalDateTime.now(),
        pageable: Pageable
    ): Page<UserCoupon>

    /**
     * 상태별 개수 조회
     */
    fun countByStatus(status: UserCouponStatus): Long

    /**
     * 쿠폰별 발급 개수 조회
     */
    fun countByCouponId(couponId: Long): Long

    /**
     * 쿠폰별 사용 개수 조회
     */
    fun countByCouponIdAndStatus(couponId: Long, status: UserCouponStatus): Long

    /**
     * 사용자 쿠폰 상태 업데이트
     */
    @Modifying
    @Query("UPDATE UserCoupon uc SET uc.status = :status WHERE uc.id = :id")
    fun updateStatus(@Param("id") id: Long, @Param("status") status: UserCouponStatus)

    /**
     * 사용자 쿠폰 사용 처리
     */
    @Modifying
    @Query("""
        UPDATE UserCoupon uc
        SET uc.status = :status, uc.usedAt = :usedAt, uc.discountApplied = :discountApplied
        WHERE uc.id = :id
    """)
    fun updateUsage(
        @Param("id") id: Long,
        @Param("status") status: UserCouponStatus,
        @Param("usedAt") usedAt: LocalDateTime,
        @Param("discountApplied") discountApplied: BigDecimal
    )

    /**
     * 예약된 쿠폰 중 만료 시간이 지난 것들 조회 (복구용)
     */
    @Query("""
        SELECT uc FROM UserCoupon uc
        WHERE uc.status = :status
        AND uc.reservedUntil < :now
        ORDER BY uc.reservedUntil ASC
    """)
    fun findExpiredReservedCoupons(
        @Param("status") status: UserCouponStatus = UserCouponStatus.RESERVED,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<UserCoupon>

    /**
     * 만료된 예약 쿠폰 수량 조회
     */
    fun countByStatusAndReservedUntilBefore(
        status: UserCouponStatus,
        dateTime: LocalDateTime
    ): Long
}