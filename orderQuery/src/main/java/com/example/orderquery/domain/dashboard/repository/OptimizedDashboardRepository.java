package com.example.orderquery.domain.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderItemViewId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 🚀 최적화된 대시보드 쿼리 리포지토리
 * - 배치 쿼리로 N+1 문제 해결
 * - 인덱스 활용 최적화
 * - 집계 함수 성능 향상
 */
@Repository
public interface OptimizedDashboardRepository extends JpaRepository<OrderItemView, OrderItemViewId> {

    /**
     * 🎯 단일 쿼리로 전체 대시보드 통계 조회 (N+1 문제 해결)
     */
    @Query(value = """
        WITH dashboard_stats AS (
            SELECT
                COUNT(*) as total_orders,
                COALESCE(SUM(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as total_revenue,
                COUNT(CASE WHEN DATE(created_at) = DATE(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as today_orders,
                COALESCE(SUM(CASE WHEN DATE(created_at) = DATE(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as today_revenue,
                COUNT(CASE WHEN created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as weekly_orders,
                COALESCE(SUM(CASE WHEN created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as weekly_revenue,
                COUNT(CASE WHEN created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as monthly_orders,
                COALESCE(SUM(CASE WHEN created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as monthly_revenue
            FROM popup_order_items_view
            WHERE deleted_at IS NULL
        )
        SELECT
            total_orders,
            total_revenue,
            CASE WHEN total_orders > 0 THEN ROUND(total_revenue::numeric / total_orders, 2) ELSE 0 END as avg_order_amount,
            today_orders,
            today_revenue,
            weekly_orders,
            weekly_revenue,
            monthly_orders,
            monthly_revenue
        FROM dashboard_stats
        """, nativeQuery = true)
    List<Object[]> findDashboardStatsOptimized();

    /**
     * 🏪 단일 쿼리로 스토어별 대시보드 통계 조회
     */
    @Query(value = """
        WITH store_stats AS (
            SELECT
                COUNT(*) as total_orders,
                COALESCE(SUM(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as total_revenue,
                COUNT(CASE WHEN DATE(created_at) = DATE(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as today_orders,
                COALESCE(SUM(CASE WHEN DATE(created_at) = DATE(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as today_revenue,
                COUNT(CASE WHEN created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as weekly_orders,
                COALESCE(SUM(CASE WHEN created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as weekly_revenue,
                COUNT(CASE WHEN created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') THEN 1 END) as monthly_orders,
                COALESCE(SUM(CASE WHEN created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    AND order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as monthly_revenue
            FROM popup_order_items_view
            WHERE store_id = :storeId AND deleted_at IS NULL
        )
        SELECT
            total_orders,
            total_revenue,
            CASE WHEN total_orders > 0 THEN ROUND(total_revenue::numeric / total_orders, 2) ELSE 0 END as avg_order_amount,
            today_orders,
            today_revenue,
            weekly_orders,
            weekly_revenue,
            monthly_orders,
            monthly_revenue
        FROM store_stats
        """, nativeQuery = true)
    List<Object[]> findDashboardStatsByStoreOptimized(@Param("storeId") UUID storeId);

    /**
     * 📊 최적화된 상태별 카운트 및 비율 조회 (단일 쿼리)
     */
    @Query(value = """
        WITH status_stats AS (
            SELECT
                order_status,
                COUNT(*) as status_count,
                COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() as status_ratio
            FROM popup_order_items_view
            WHERE deleted_at IS NULL
            GROUP BY order_status
        )
        SELECT order_status, status_count, ROUND(status_ratio, 2) as status_ratio
        FROM status_stats
        ORDER BY status_count DESC
        """, nativeQuery = true)
    List<Object[]> findOrderStatusStatsOptimized();

    /**
     * 🏪 스토어별 최적화된 상태별 통계
     */
    @Query(value = """
        WITH store_status_stats AS (
            SELECT
                order_status,
                COUNT(*) as status_count,
                COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() as status_ratio
            FROM popup_order_items_view
            WHERE store_id = :storeId AND deleted_at IS NULL
            GROUP BY order_status
        )
        SELECT order_status, status_count, ROUND(status_ratio, 2) as status_ratio
        FROM store_status_stats
        ORDER BY status_count DESC
        """, nativeQuery = true)
    List<Object[]> findOrderStatusStatsByStoreOptimized(@Param("storeId") UUID storeId);

    /**
     * 🛍️ 최적화된 인기 상품 TOP N 조회 (인덱스 활용)
     */
    @Query(value = """
        SELECT
            goods_name,
            COUNT(*) as order_count,
            COALESCE(SUM(line_price), 0) as total_revenue,
            ROUND(AVG(line_price), 2) as avg_price
        FROM popup_order_items_view
        WHERE goods_name IS NOT NULL
          AND order_status IN ('COMPLETED', 'PAID')
          AND deleted_at IS NULL
        GROUP BY goods_name
        ORDER BY order_count DESC, total_revenue DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findPopularItemsOptimized(@Param("limit") int limit);

    /**
     * 🏪 스토어별 최적화된 인기 상품 조회
     */
    @Query(value = """
        SELECT
            goods_name,
            COUNT(*) as order_count,
            COALESCE(SUM(line_price), 0) as total_revenue,
            ROUND(AVG(line_price), 2) as avg_price
        FROM popup_order_items_view
        WHERE store_id = :storeId
          AND goods_name IS NOT NULL
          AND order_status IN ('COMPLETED', 'PAID')
          AND deleted_at IS NULL
        GROUP BY goods_name
        ORDER BY order_count DESC, total_revenue DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findPopularItemsByStoreOptimized(@Param("storeId") UUID storeId, @Param("limit") int limit);

    /**
     * 📈 최적화된 일별 주문 트렌드 (인덱스 활용)
     */
    @Query(value = """
        SELECT
            DATE(created_at) as order_date,
            COUNT(*) as order_count,
            COALESCE(SUM(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as revenue,
            ROUND(AVG(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price END), 2) as avg_order_value
        FROM popup_order_items_view
        WHERE created_at >= CURRENT_DATE - INTERVAL ':days days'
          AND deleted_at IS NULL
        GROUP BY DATE(created_at)
        ORDER BY order_date DESC
        LIMIT 30
        """, nativeQuery = true)
    List<Object[]> findDailyOrderTrendOptimized(@Param("days") int days);

    /**
     * ⏰ 최적화된 시간대별 주문 분포 (함수 기반 인덱스 활용)
     */
    @Query(value = """
        SELECT
            EXTRACT(hour FROM ordered_at) as hour,
            COUNT(*) as order_count,
            COALESCE(SUM(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as revenue
        FROM popup_order_items_view
        WHERE ordered_at BETWEEN :startDate AND :endDate
          AND deleted_at IS NULL
        GROUP BY EXTRACT(hour FROM ordered_at)
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> findHourlyDistributionOptimized(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);

    /**
     * 🏪 스토어별 최적화된 시간대별 분포
     */
    @Query(value = """
        SELECT
            EXTRACT(hour FROM ordered_at) as hour,
            COUNT(*) as order_count,
            COALESCE(SUM(CASE WHEN order_status IN ('COMPLETED', 'PAID') THEN line_price ELSE 0 END), 0) as revenue
        FROM popup_order_items_view
        WHERE store_id = :storeId
          AND ordered_at BETWEEN :startDate AND :endDate
          AND deleted_at IS NULL
        GROUP BY EXTRACT(hour FROM ordered_at)
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> findHourlyDistributionByStoreOptimized(@Param("storeId") UUID storeId,
                                                          @Param("startDate") LocalDateTime startDate,
                                                          @Param("endDate") LocalDateTime endDate);

    /**
     * 🚨 최적화된 긴급 처리 통계 (단일 쿼리)
     */
    @Query(value = """
        SELECT
            SUM(CASE WHEN order_status IN ('PENDING', 'CONFIRMED')
                     AND ordered_at < :sinceDate THEN 1 ELSE 0 END) as long_waiting_orders,
            SUM(CASE WHEN payment_status = 'FAILED'
                     OR (order_status = 'PAYMENT_PENDING' AND ordered_at < CURRENT_TIMESTAMP - INTERVAL '1 hour')
                     THEN 1 ELSE 0 END) as payment_failed_orders,
            SUM(CASE WHEN order_status = 'CANCELLED'
                     AND updated_at >= CURRENT_DATE THEN 1 ELSE 0 END) as cancellation_requests,
            SUM(CASE WHEN order_status = 'REFUND_PENDING'
                     OR (order_status = 'CANCELLED' AND payment_status = 'PAID')
                     THEN 1 ELSE 0 END) as refund_pending_orders
        FROM popup_order_items_view
        WHERE deleted_at IS NULL
        """, nativeQuery = true)
    List<Object[]> findUrgentCountsOptimized(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * 💳 최적화된 결제 상태별 통계
     */
    @Query(value = """
        SELECT
            payment_status,
            COUNT(*) as payment_count,
            COALESCE(SUM(line_price), 0) as payment_amount,
            COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() as payment_ratio
        FROM popup_order_items_view
        WHERE payment_status IS NOT NULL
          AND order_status IN ('COMPLETED', 'PAID')
          AND deleted_at IS NULL
        GROUP BY payment_status
        ORDER BY payment_count DESC
        """, nativeQuery = true)
    List<Object[]> findPaymentStatsOptimized();

    /**
     * 📋 최적화된 최근 주문 조회 (제한적 필드만 조회)
     */
    @Query(value = """
        SELECT
            order_id,
            order_no,
            user_id,
            line_price,
            order_status,
            created_at,
            goods_name
        FROM popup_order_items_view
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findRecentOrdersOptimized(@Param("limit") int limit);

    /**
     * 🏪 스토어별 최적화된 최근 주문 조회
     */
    @Query(value = """
        SELECT
            order_id,
            order_no,
            user_id,
            line_price,
            order_status,
            created_at,
            goods_name
        FROM popup_order_items_view
        WHERE store_id = :storeId AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findRecentOrdersByStoreOptimized(@Param("storeId") UUID storeId, @Param("limit") int limit);
}