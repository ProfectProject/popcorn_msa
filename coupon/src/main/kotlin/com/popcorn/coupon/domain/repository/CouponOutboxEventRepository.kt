package com.popcorn.coupon.domain.repository

import com.popcorn.coupon.domain.entity.CouponOutboxEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CouponOutboxEventRepository : JpaRepository<CouponOutboxEvent, Long> {

    /**
     * 처리되지 않은 이벤트 조회 (발행 대기 중)
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.processedAt IS NULL
        AND coe.createdAt >= :sinceDate
        ORDER BY coe.createdAt ASC
    """)
    fun findUnpublishedEvents(
        @Param("sinceDate") sinceDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponOutboxEvent>

    /**
     * 처리되지 않은 이벤트 조회 (제한된 수량)
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.processedAt IS NULL
        ORDER BY coe.createdAt ASC
    """)
    fun findUnpublishedEvents(pageable: Pageable): List<CouponOutboxEvent>

    /**
     * 이벤트 타입별 미처리 이벤트 조회
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.eventType = :eventType
        AND coe.processedAt IS NULL
        ORDER BY coe.createdAt ASC
    """)
    fun findUnpublishedEventsByType(
        @Param("eventType") eventType: String,
        pageable: Pageable
    ): List<CouponOutboxEvent>

    /**
     * 집계 ID별 이벤트 조회
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.aggregateId = :aggregateId
        ORDER BY coe.createdAt ASC
    """)
    fun findByAggregateId(@Param("aggregateId") aggregateId: String): List<CouponOutboxEvent>

    /**
     * 이벤트 처리 완료 상태 업데이트
     */
    @Modifying
    @Query("""
        UPDATE CouponOutboxEvent coe
        SET coe.processedAt = :processedAt
        WHERE coe.id = :id
    """)
    fun markAsProcessed(
        @Param("id") id: Long,
        @Param("processedAt") processedAt: LocalDateTime
    ): Int

    /**
     * 여러 이벤트 처리 완료 상태 일괄 업데이트
     */
    @Modifying
    @Query("""
        UPDATE CouponOutboxEvent coe
        SET coe.processedAt = :processedAt
        WHERE coe.id IN :ids
    """)
    fun markAsProcessed(
        @Param("ids") ids: List<Long>,
        @Param("processedAt") processedAt: LocalDateTime
    ): Int

    /**
     * 오래된 처리된 이벤트 조회 (정리용)
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.processedAt IS NOT NULL
        AND coe.processedAt < :cutoffDate
        ORDER BY coe.processedAt ASC
    """)
    fun findOldProcessedEvents(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponOutboxEvent>

    /**
     * 처리되지 않은 오래된 이벤트 조회 (장애 복구용)
     */
    @Query("""
        SELECT coe FROM CouponOutboxEvent coe
        WHERE coe.processedAt IS NULL
        AND coe.createdAt < :cutoffDate
        ORDER BY coe.createdAt ASC
    """)
    fun findOldUnprocessedEvents(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        pageable: Pageable
    ): Page<CouponOutboxEvent>

    /**
     * 이벤트 타입별 처리 통계 조회
     */
    @Query("""
        SELECT coe.eventType,
               COUNT(CASE WHEN coe.processedAt IS NOT NULL THEN 1 END) as processed,
               COUNT(CASE WHEN coe.processedAt IS NULL THEN 1 END) as unprocessed
        FROM CouponOutboxEvent coe
        WHERE coe.createdAt BETWEEN :startDate AND :endDate
        GROUP BY coe.eventType
        ORDER BY processed DESC
    """)
    fun getEventStatistics(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Array<Any>>

    /**
     * 처리되지 않은 이벤트 개수 조회
     */
    fun countByProcessedAtIsNull(): Long

    /**
     * 특정 기간 내 처리되지 않은 이벤트 개수 조회
     */
    @Query("""
        SELECT COUNT(coe) FROM CouponOutboxEvent coe
        WHERE coe.processedAt IS NULL
        AND coe.createdAt BETWEEN :startDate AND :endDate
    """)
    fun countUnprocessedEventsByPeriod(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): Long
}
