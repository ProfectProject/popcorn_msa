package com.popcorn.coupon.domain.repository

import com.popcorn.coupon.domain.entity.Coupon
import com.popcorn.coupon.domain.entity.CouponStatus
import com.popcorn.coupon.domain.entity.TargetType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {

    /**
     * 활성 쿠폰 조회
     */
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.status = :status
        AND c.validFrom <= :now
        AND c.validUntil > :now
        ORDER BY c.createdAt DESC
    """)
    fun findActiveCoupons(
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    /**
     * 대상 타입별 활성 쿠폰 조회
     */
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.status = :status
        AND c.targetType IN :targetTypes
        AND c.validFrom <= :now
        AND c.validUntil > :now
        ORDER BY c.createdAt DESC
    """)
    fun findActiveCouponsByTargetType(
        @Param("targetTypes") targetTypes: List<TargetType>,
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    /**
     * 발급 가능한 쿠폰 조회 (재고 있는 것만)
     */
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.status = :status
        AND c.validFrom <= :now
        AND c.validUntil > :now
        AND (c.totalQuantity IS NULL OR c.issuedQuantity < c.totalQuantity)
        ORDER BY c.createdAt DESC
    """)
    fun findAvailableCoupons(
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    /**
     * 환영 쿠폰 조회 (신규 사용자용)
     */
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.status = :status
        AND c.targetType = :targetType
        AND c.validFrom <= :now
        AND c.validUntil > :now
        AND (c.totalQuantity IS NULL OR c.issuedQuantity < c.totalQuantity)
        ORDER BY c.createdAt DESC
    """)
    fun findWelcomeCoupons(
        @Param("status") status: CouponStatus = CouponStatus.ACTIVE,
        @Param("targetType") targetType: TargetType = TargetType.NEW_USERS,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    /**
     * 만료된 쿠폰 조회 (배치 처리용)
     */
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.status = :activeStatus
        AND c.validUntil < :now
        ORDER BY c.validUntil ASC
    """,
    nativeQuery = false)
    fun findExpiredCoupons(
        @Param("activeStatus") activeStatus: CouponStatus = CouponStatus.ACTIVE,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<Coupon>

    /**
     * 상태별 쿠폰 개수 조회
     */
    fun countByStatus(status: CouponStatus): Long

    /**
     * 발급 수량 업데이트
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + :increment WHERE c.id = :id")
    fun updateIssuedQuantity(@Param("id") id: Long, @Param("increment") increment: Int = 1)

    /**
     * 상태 업데이트
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.status = :status WHERE c.id = :id")
    fun updateStatus(@Param("id") id: Long, @Param("status") status: CouponStatus)
}