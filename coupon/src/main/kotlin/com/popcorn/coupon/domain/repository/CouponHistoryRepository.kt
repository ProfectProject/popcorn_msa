package com.popcorn.coupon.domain.repository

import com.popcorn.coupon.domain.entity.CouponHistory
import com.popcorn.coupon.domain.entity.CouponAction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CouponHistoryRepository : JpaRepository<CouponHistory, Long> {

    /**
     * 사용자별 쿠폰 히스토리 조회
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE ch.userId = :userId
        ORDER BY ch.createdAt DESC
    """)
    fun findByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<CouponHistory>

    /**
     * 쿠폰별 히스토리 조회
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE EXISTS (
            SELECT 1 FROM UserCoupon uc
            WHERE uc.id = ch.userCouponId
              AND uc.couponId = :couponId
        )
        ORDER BY ch.createdAt DESC
    """)
    fun findByCouponId(
        @Param("couponId") couponId: Long,
        pageable: Pageable
    ): Page<CouponHistory>

    /**
     * 사용자 쿠폰별 히스토리 조회
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE ch.userCouponId = :userCouponId
        ORDER BY ch.createdAt DESC
    """)
    fun findByUserCouponId(
        @Param("userCouponId") userCouponId: Long
    ): List<CouponHistory>

    /**
     * 액션 타입별 히스토리 조회
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE ch.action = :action
        AND ch.createdAt BETWEEN :startDate AND :endDate
        ORDER BY ch.createdAt DESC
    """)
    fun findByActionAndCreatedAtBetween(
        @Param("action") action: CouponAction,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponHistory>

    /**
     * 기간별 히스토리 조회
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE ch.createdAt BETWEEN :startDate AND :endDate
        ORDER BY ch.createdAt DESC
    """)
    fun findByCreatedAtBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponHistory>

    /**
     * 사용자별 액션별 히스토리 개수 조회
     */
    @Query("""
        SELECT COUNT(ch) FROM CouponHistory ch
        WHERE ch.userId = :userId
        AND ch.action = :action
    """)
    fun countByUserIdAndAction(
        @Param("userId") userId: Long,
        @Param("action") action: CouponAction
    ): Long

    /**
     * 기간별 액션별 히스토리 개수 조회 (통계용)
     */
    @Query("""
        SELECT ch.action, COUNT(ch) FROM CouponHistory ch
        WHERE ch.createdAt BETWEEN :startDate AND :endDate
        GROUP BY ch.action
        ORDER BY COUNT(ch) DESC
    """)
    fun countByActionGroupByPeriod(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Array<Any>>

    /**
     * 오래된 히스토리 삭제를 위한 조회 (배치용)
     */
    @Query("""
        SELECT ch FROM CouponHistory ch
        WHERE ch.createdAt < :cutoffDate
        ORDER BY ch.createdAt ASC
    """)
    fun findOldHistory(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponHistory>
}
