package com.example.orderquery.domain.itemView.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderItemViewId;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;

public interface OrderItemViewRepository extends JpaRepository<OrderItemView, OrderItemViewId>,
        JpaSpecificationExecutor<OrderItemView> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.checkedIn = true, v.checkinAt = :checkinAt " +
            "where v.storeId = :storeId and v.id.popupId = :popupId and v.id.orderGoodsId = :orderGoodsId")
    int markCheckedIn(@Param("storeId") UUID storeId,
                      @Param("popupId") UUID popupId,
                      @Param("orderGoodsId") UUID orderGoodsId,
                      @Param("checkinAt") LocalDateTime checkinAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.orderStatus = :orderStatus " +
            "where v.id.popupId = :popupId and v.orderId = :orderId")
    int updateOrderStatus(@Param("popupId") UUID popupId,
                          @Param("orderId") UUID orderId,
                          @Param("orderStatus") OrderStatus orderStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemView v set v.paymentStatus = :paymentStatus, v.paymentApprovedAt = :approvedAt " +
            "where v.id.popupId = :popupId and v.orderId = :orderId")
    int updatePaymentStatus(@Param("popupId") UUID popupId,
                            @Param("orderId") UUID orderId,
                            @Param("paymentStatus") PaymentStatus paymentStatus,
                            @Param("approvedAt") LocalDateTime approvedAt);

    // === 📊 대시보드 통계 쿼리 메서드들 ===

    /**
     * 전체 매출 총액 조회
     */
    @Query("select sum(v.linePrice) from OrderItemView v where v.orderStatus in ('COMPLETED', 'PAID')")
    Optional<BigDecimal> findTotalRevenue();

    /**
     * 🏪 스토어별 매출 총액 조회
     */
    @Query("select sum(v.linePrice) from OrderItemView v where v.storeId = :storeId and v.orderStatus in ('COMPLETED', 'PAID')")
    Optional<BigDecimal> findTotalRevenueByStore(@Param("storeId") UUID storeId);

    /**
     * 기간별 매출 조회
     */
    @Query("select sum(v.linePrice) from OrderItemView v " +
            "where v.orderedAt between :startDate and :endDate " +
            "and v.orderStatus in ('COMPLETED', 'PAID')")
    Optional<BigDecimal> findRevenueByDateRange(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    /**
     * 🏪 스토어별 기간별 매출 조회
     */
    @Query("select sum(v.linePrice) from OrderItemView v " +
            "where v.storeId = :storeId and v.orderedAt between :startDate and :endDate " +
            "and v.orderStatus in ('COMPLETED', 'PAID')")
    Optional<BigDecimal> findRevenueByStoreAndDateRange(@Param("storeId") UUID storeId,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);

    /**
     * 기간별 주문 수 조회
     */
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 🏪 스토어별 기간별 주문 수 조회
     */
    long countByStoreIdAndCreatedAtBetween(UUID storeId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 🏪 스토어별 총 주문 수 조회
     */
    long countByStoreId(UUID storeId);

    /**
     * 주문 상태별 카운트 조회
     */
    @Query("select v.orderStatus, count(v) from OrderItemView v group by v.orderStatus")
    List<Object[]> findOrderCountsByStatus();

    /**
     * 🏪 스토어별 주문 상태별 카운트 조회
     */
    @Query("select v.orderStatus, count(v) from OrderItemView v where v.storeId = :storeId group by v.orderStatus")
    List<Object[]> findOrderCountsByStatusAndStore(@Param("storeId") UUID storeId);

    /**
     * 기간별 주문 상태별 카운트 조회
     */
    @Query("select v.orderStatus, count(v) from OrderItemView v " +
            "where v.orderedAt between :startDate and :endDate " +
            "group by v.orderStatus")
    List<Object[]> findOrderCountsByStatusAndDateRange(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    /**
     * 🏪 스토어별 기간별 주문 상태별 카운트 조회
     */
    @Query("select v.orderStatus, count(v) from OrderItemView v " +
            "where v.storeId = :storeId and v.orderedAt between :startDate and :endDate " +
            "group by v.orderStatus")
    List<Object[]> findOrderCountsByStatusAndStoreAndDateRange(@Param("storeId") UUID storeId,
                                                               @Param("startDate") LocalDateTime startDate,
                                                               @Param("endDate") LocalDateTime endDate);

    /**
     * 인기 상품 TOP N 조회
     */
    @Query(value = "select v.goods_name, count(*), sum(v.line_price) " +
            "from popup_order_items_view v " +
            "where v.order_status in ('COMPLETED', 'PAID') " +
            "and v.goods_name is not null " +
            "group by v.goods_name " +
            "order by count(*) desc " +
            "limit :limit", nativeQuery = true)
    List<Object[]> findPopularItems(@Param("limit") int limit);

    /**
     * 🏪 스토어별 인기 상품 TOP N 조회
     */
    @Query(value = "select v.goods_name, count(*), sum(v.line_price) " +
            "from popup_order_items_view v " +
            "where v.store_id = :storeId and v.order_status in ('COMPLETED', 'PAID') " +
            "and v.goods_name is not null " +
            "group by v.goods_name " +
            "order by count(*) desc " +
            "limit :limit", nativeQuery = true)
    List<Object[]> findPopularItemsByStore(@Param("storeId") UUID storeId, @Param("limit") int limit);

    /**
     * 시간대별 주문 분포 조회
     */
    @Query(value = "select extract(hour from v.ordered_at) as hour, count(*) " +
            "from popup_order_items_view v " +
            "where v.ordered_at between :startDate and :endDate " +
            "group by extract(hour from v.ordered_at) " +
            "order by hour", nativeQuery = true)
    List<Object[]> findHourlyOrderDistribution(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    /**
     * 🏪 스토어별 시간대별 주문 분포 조회
     */
    @Query(value = "select extract(hour from v.ordered_at) as hour, count(*) " +
            "from popup_order_items_view v " +
            "where v.store_id = :storeId and v.ordered_at between :startDate and :endDate " +
            "group by extract(hour from v.ordered_at) " +
            "order by hour", nativeQuery = true)
    List<Object[]> findHourlyOrderDistributionByStore(@Param("storeId") UUID storeId,
                                                      @Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    /**
     * 최근 N일간 일별 주문 수 조회
     */
    @Query(value = "select date(v.ordered_at) as order_date, count(*), sum(v.line_price) " +
            "from popup_order_items_view v " +
            "where v.ordered_at >= current_date - (:days || ' days')::interval " +
            "group by date(v.ordered_at) " +
            "order by order_date desc", nativeQuery = true)
    List<Object[]> findDailyOrderCounts(@Param("days") int days);

    /**
     * 🏪 스토어별 최근 N일간 일별 주문 수 조회
     */
    @Query(value = "select date(v.ordered_at) as order_date, count(*), sum(v.line_price) " +
            "from popup_order_items_view v " +
            "where v.store_id = :storeId and v.ordered_at >= current_date - (:days || ' days')::interval " +
            "group by date(v.ordered_at) " +
            "order by order_date desc", nativeQuery = true)
    List<Object[]> findDailyOrderCountsByStore(@Param("storeId") UUID storeId, @Param("days") int days);

    /**
     * 결제 상태별 통계 조회
     */
    @Query("select v.paymentStatus, count(v), sum(v.linePrice) " +
            "from OrderItemView v " +
            "where v.paymentStatus is not null and v.orderStatus in ('COMPLETED', 'PAID') " +
            "group by v.paymentStatus")
    List<Object[]> findPaymentStatusStatistics();

    /**
     * 🏪 스토어별 결제 상태별 통계 조회
     */
    @Query("select v.paymentStatus, count(v), sum(v.linePrice) " +
            "from OrderItemView v " +
            "where v.storeId = :storeId and v.paymentStatus is not null and v.orderStatus in ('COMPLETED', 'PAID') " +
            "group by v.paymentStatus")
    List<Object[]> findPaymentStatusStatisticsByStore(@Param("storeId") UUID storeId);

    /**
     * 특정 상태의 주문 조회 (페이징)
     */
    Page<OrderItemView> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

    /**
     * 🏪 스토어별 특정 상태의 주문 조회 (페이징)
     */
    Page<OrderItemView> findByStoreIdAndOrderStatus(UUID storeId, OrderStatus orderStatus, Pageable pageable);

    /**
     * 상태별 주문 조회 (호환성을 위한 메서드)
     */
    default Page<OrderItemView> findByStatus(OrderStatus status, Pageable pageable) {
        return findByOrderStatus(status, pageable);
    }

    /**
     * 장시간 대기 중인 주문 수 조회 (30분 이상)
     */
    @Query("select count(v) from OrderItemView v " +
            "where v.orderStatus in ('PENDING', 'CONFIRMED') " +
            "and v.orderedAt < :sinceDate")
    long countLongWaitingOrders(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * 결제 실패한 주문 수 조회
     */
    @Query(value = "select count(*) from popup_order_items_view v " +
            "where v.payment_status = 'FAILED' " +
            "or (v.order_status = 'PAYMENT_PENDING' and v.ordered_at < current_timestamp - interval '1 hour')",
            nativeQuery = true)
    long countPaymentFailedOrders();

    /**
     * 취소 요청된 주문 수 조회
     */
    @Query(value = "select count(*) from popup_order_items_view v " +
            "where v.order_status = 'CANCELLED' " +
            "and v.updated_at >= current_date",
            nativeQuery = true)
    long countCancellationRequests();

    /**
     * 환불 대기 중인 주문 수 조회
     */
    @Query("select count(v) from OrderItemView v " +
            "where v.orderStatus = 'REFUND_PENDING' " +
            "or (v.orderStatus = 'CANCELLED' and v.paymentStatus = 'PAID')")
    long countRefundPendingOrders();
}
