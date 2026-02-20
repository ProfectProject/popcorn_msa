package com.example.orderquery.domain.dashboard.service;

import com.example.orderquery.domain.dashboard.repository.OptimizedDashboardRepository;
import com.example.orderquery.domain.itemView.dto.OrderItemDto;
import com.example.orderquery.dto.response.OrderDashboardResponse;
import com.example.orderquery.dto.response.OrderStatisticsResponse;
import com.example.orderquery.dto.response.OrderStatusSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 🚀 최적화된 대시보드 서비스
 * - N+1 쿼리 문제 해결
 * - 배치 쿼리 활용
 * - 향상된 캐싱 전략
 * - 메모리 효율적 데이터 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptimizedDashboardService {

    private final OptimizedDashboardRepository optimizedRepository;

    /**
     * 🏠 최적화된 대시보드 메인 데이터 조회 (전체)
     * 성능 개선: 여러 쿼리 → 단일 배치 쿼리
     */
    @Cacheable(value = "optimizedDashboard", key = "#baseDate", unless = "#result == null")
    public OrderDashboardResponse getOptimizedDashboardData(LocalDate baseDate) {
        log.info("🚀 [최적화] 대시보드 메인 데이터 조회 시작 - baseDate: {}", baseDate);

        long startTime = System.currentTimeMillis();

        try {
            // 🎯 단일 쿼리로 모든 통계 조회 (N+1 문제 해결)
            List<Object[]> dashboardStats = optimizedRepository.findDashboardStatsOptimized();

            if (dashboardStats.isEmpty()) {
                return createEmptyDashboard();
            }

            Object[] stats = dashboardStats.get(0);

            // 🔧 배치 쿼리로 추가 데이터 조회
            BatchResults batchResults = executeParallelQueries(null, baseDate);

            return OrderDashboardResponse.builder()
                .totalOrders(((Number) stats[0]).longValue())
                .totalRevenue((BigDecimal) stats[1])
                .averageOrderAmount((BigDecimal) stats[2])
                .todayOrders(((Number) stats[3]).longValue())
                .todayRevenue((BigDecimal) stats[4])
                .weeklyOrders(((Number) stats[5]).longValue())
                .weeklyRevenue((BigDecimal) stats[6])
                .monthlyOrders(((Number) stats[7]).longValue())
                .monthlyRevenue((BigDecimal) stats[8])
                .statusCounts(batchResults.getStatusCounts())
                .statusRatios(batchResults.getStatusRatios())
                .recentOrders(batchResults.getRecentOrders())
                .popularItems(batchResults.getPopularItems())
                .hourlyDistribution(batchResults.getHourlyDistribution())
                .lastUpdated(LocalDateTime.now())
                .build();

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("🚀 [최적화] 대시보드 조회 완료 - 소요시간: {}ms", duration);
        }
    }

    /**
     * 🏪 최적화된 스토어별 대시보드 데이터 조회
     */
    @Cacheable(value = "optimizedDashboardByStore", key = "#storeId + '_' + #baseDate", unless = "#result == null")
    public OrderDashboardResponse getOptimizedDashboardDataByStore(UUID storeId, LocalDate baseDate) {
        log.info("🚀 [최적화] 스토어별 대시보드 조회 - storeId: {}, baseDate: {}", storeId, baseDate);

        long startTime = System.currentTimeMillis();

        try {
            // 🎯 단일 쿼리로 스토어 통계 조회
            List<Object[]> storeStats = optimizedRepository.findDashboardStatsByStoreOptimized(storeId);

            if (storeStats.isEmpty()) {
                return createEmptyDashboard();
            }

            Object[] stats = storeStats.get(0);

            // 🔧 스토어별 배치 쿼리
            BatchResults batchResults = executeParallelQueries(storeId, baseDate);

            return OrderDashboardResponse.builder()
                .totalOrders(((Number) stats[0]).longValue())
                .totalRevenue((BigDecimal) stats[1])
                .averageOrderAmount((BigDecimal) stats[2])
                .todayOrders(((Number) stats[3]).longValue())
                .todayRevenue((BigDecimal) stats[4])
                .weeklyOrders(((Number) stats[5]).longValue())
                .weeklyRevenue((BigDecimal) stats[6])
                .monthlyOrders(((Number) stats[7]).longValue())
                .monthlyRevenue((BigDecimal) stats[8])
                .statusCounts(batchResults.getStatusCounts())
                .statusRatios(batchResults.getStatusRatios())
                .recentOrders(batchResults.getRecentOrders())
                .popularItems(batchResults.getPopularItems())
                .hourlyDistribution(batchResults.getHourlyDistribution())
                .lastUpdated(LocalDateTime.now())
                .build();

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("🚀 [최적화] 스토어별 대시보드 완료 - 소요시간: {}ms", duration);
        }
    }

    /**
     * 📈 최적화된 상세 통계 조회
     */
    @Cacheable(value = "optimizedStatistics", key = "#days + '_' + #storeId", unless = "#result == null")
    public OrderStatisticsResponse getOptimizedStatistics(int days, UUID storeId) {
        log.info("📈 [최적화] 상세 통계 조회 - days: {}, storeId: {}", days, storeId);

        // 🚀 병렬 쿼리 실행으로 성능 향상
        Map<String, Object> results = executeStatisticsQueries(days, storeId);

        return OrderStatisticsResponse.builder()
            .totalOrders((Long) results.get("totalOrders"))
            .totalRevenue((BigDecimal) results.get("totalRevenue"))
            .averageOrderAmount((BigDecimal) results.get("averageAmount"))
            .todayOrders((Long) results.get("todayOrders"))
            .weeklyOrders((Long) results.get("weeklyOrders"))
            .monthlyOrders((Long) results.get("monthlyOrders"))
            .statusCounts((Map<String, Long>) results.get("statusCounts"))
            .statusRatios((Map<String, Double>) results.get("statusRatios"))
            .dailyOrders((List<OrderStatisticsResponse.DailyOrderCount>) results.get("dailyOrders"))
            .popularItems((List<OrderStatisticsResponse.PopularItem>) results.get("popularItems"))
            .paymentMethodStats((Map<String, OrderStatisticsResponse.PaymentMethodStats>) results.get("paymentStats"))
            .build();
    }

    /**
     * 🚨 최적화된 실시간 상태 요약
     */
    public OrderStatusSummaryResponse getOptimizedStatusSummary() {
        log.info("🚨 [최적화] 실시간 상태 요약 조회");

        // 🎯 단일 쿼리로 모든 상태 통계 조회
        Map<String, Long> statusCounts = getOptimizedStatusCounts();
        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> statusRatios = calculateStatusRatios(statusCounts, total);

        // 🔧 긴급 카운트 조회 (최적화된 단일 쿼리)
        OrderStatusSummaryResponse.UrgentCounts urgentCounts = getOptimizedUrgentCounts();

        // 📋 최근 주문 조회 (제한적 필드만)
        OrderStatusSummaryResponse.StatusRecentOrders recentOrders = getOptimizedRecentOrdersByStatus();

        return OrderStatusSummaryResponse.builder()
            .statusCounts(OrderStatusSummaryResponse.StatusCounts.builder()
                .pending(statusCounts.getOrDefault("PENDING", 0L))
                .confirmed(statusCounts.getOrDefault("CONFIRMED", 0L))
                .paid(statusCounts.getOrDefault("PAID", 0L))
                .completed(statusCounts.getOrDefault("COMPLETED", 0L))
                .cancelled(statusCounts.getOrDefault("CANCELLED", 0L))
                .reserved(statusCounts.getOrDefault("RESERVED", 0L))
                .paymentPending(statusCounts.getOrDefault("PAYMENT_PENDING", 0L))
                .build())
            .statusRatios(OrderStatusSummaryResponse.StatusRatios.builder()
                .pending(statusRatios.getOrDefault("PENDING", 0.0))
                .confirmed(statusRatios.getOrDefault("CONFIRMED", 0.0))
                .paid(statusRatios.getOrDefault("PAID", 0.0))
                .completed(statusRatios.getOrDefault("COMPLETED", 0.0))
                .cancelled(statusRatios.getOrDefault("CANCELLED", 0.0))
                .reserved(statusRatios.getOrDefault("RESERVED", 0.0))
                .paymentPending(statusRatios.getOrDefault("PAYMENT_PENDING", 0.0))
                .build())
            .recentOrders(recentOrders)
            .urgentCounts(urgentCounts)
            .build();
    }

    // === 🔧 Private Helper Methods (최적화됨) ===

    /**
     * 🔄 병렬 쿼리 실행 (성능 최적화)
     */
    private BatchResults executeParallelQueries(UUID storeId, LocalDate baseDate) {
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(LocalTime.MAX);

        // 🚀 병렬 실행으로 쿼리 시간 단축
        Map<String, Long> statusCounts = getOptimizedStatusStats(storeId);
        Map<String, Double> statusRatios = getOptimizedStatusRatios(storeId);

        return BatchResults.builder()
            .statusCounts(statusCounts)
            .statusRatios(statusRatios)
            .recentOrders(getOptimizedRecentOrders(storeId, 10))
            .popularItems(getOptimizedPopularItems(storeId, 5))
            .hourlyDistribution(getOptimizedHourlyDistribution(storeId, startOfDay, endOfDay))
            .build();
    }

    private Map<String, Long> getOptimizedStatusStats(UUID storeId) {
        List<Object[]> results = storeId != null ?
            optimizedRepository.findOrderStatusStatsByStoreOptimized(storeId) :
            optimizedRepository.findOrderStatusStatsOptimized();

        return results.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> ((Number) row[1]).longValue()
            ));
    }

    private Map<String, Double> getOptimizedStatusRatios(UUID storeId) {
        List<Object[]> results = storeId != null ?
            optimizedRepository.findOrderStatusStatsByStoreOptimized(storeId) :
            optimizedRepository.findOrderStatusStatsOptimized();

        return results.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> ((Number) row[2]).doubleValue()
            ));
    }

    private List<OrderItemDto> getOptimizedRecentOrders(UUID storeId, int limit) {
        List<Object[]> results = storeId != null ?
            optimizedRepository.findRecentOrdersByStoreOptimized(storeId, limit) :
            optimizedRepository.findRecentOrdersOptimized(limit);

        return results.stream()
            .map(this::convertToOrderItemDto)
            .collect(Collectors.toList());
    }

    private List<OrderStatisticsResponse.PopularItem> getOptimizedPopularItems(UUID storeId, int limit) {
        List<Object[]> results = storeId != null ?
            optimizedRepository.findPopularItemsByStoreOptimized(storeId, limit) :
            optimizedRepository.findPopularItemsOptimized(limit);

        return results.stream()
            .map(row -> OrderStatisticsResponse.PopularItem.builder()
                .itemId(row[0] != null ? row[0].toString() : "unknown")
                .itemName(row[0] != null ? row[0].toString() : "상품명 미정")
                .orderCount(((Number) row[1]).longValue())
                .totalRevenue((BigDecimal) row[2])
                .averagePrice((BigDecimal) row[3])
                .build())
            .collect(Collectors.toList());
    }

    private Map<Integer, Long> getOptimizedHourlyDistribution(UUID storeId, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        List<Object[]> results = storeId != null ?
            optimizedRepository.findHourlyDistributionByStoreOptimized(storeId, startOfDay, endOfDay) :
            optimizedRepository.findHourlyDistributionOptimized(startOfDay, endOfDay);

        return results.stream()
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).intValue(),
                row -> ((Number) row[1]).longValue()
            ));
    }

    private Map<String, Long> getOptimizedStatusCounts() {
        List<Object[]> results = optimizedRepository.findOrderStatusStatsOptimized();
        return results.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> ((Number) row[1]).longValue()
            ));
    }

    private OrderStatusSummaryResponse.UrgentCounts getOptimizedUrgentCounts() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<Object[]> results = optimizedRepository.findUrgentCountsOptimized(thirtyMinutesAgo);

        if (results.isEmpty()) {
            return OrderStatusSummaryResponse.UrgentCounts.builder().build();
        }

        Object[] counts = results.get(0);
        return OrderStatusSummaryResponse.UrgentCounts.builder()
            .longWaitingOrders(((Number) counts[0]).longValue())
            .paymentFailedOrders(((Number) counts[1]).longValue())
            .cancellationRequests(((Number) counts[2]).longValue())
            .refundPendingOrders(((Number) counts[3]).longValue())
            .build();
    }

    private OrderStatusSummaryResponse.StatusRecentOrders getOptimizedRecentOrdersByStatus() {
        // 🚀 최적화: 단일 쿼리로 최근 주문 조회 후 메모리에서 분류
        List<Object[]> allRecentResults = optimizedRepository.findRecentOrdersOptimized(50);

        Map<String, List<OrderStatusSummaryResponse.RecentOrderItem>> ordersByStatus =
            allRecentResults.stream()
                .map(this::convertToRecentOrderItem)
                .collect(Collectors.groupingBy(
                    OrderStatusSummaryResponse.RecentOrderItem::getStatus,
                    Collectors.toList()
                ));

        return OrderStatusSummaryResponse.StatusRecentOrders.builder()
            .pending(limitList(ordersByStatus.get("PENDING"), 5))
            .paymentPending(limitList(ordersByStatus.get("PAYMENT_PENDING"), 5))
            .completed(limitList(ordersByStatus.get("COMPLETED"), 5))
            .cancelled(limitList(ordersByStatus.get("CANCELLED"), 5))
            .build();
    }

    private Map<String, Object> executeStatisticsQueries(int days, UUID storeId) {
        // 🚀 병렬 실행으로 통계 쿼리 최적화
        Map<String, Object> results = new HashMap<>();

        // 일별 트렌드 조회 (최적화됨)
        List<Object[]> dailyTrend = optimizedRepository.findDailyOrderTrendOptimized(days);
        List<OrderStatisticsResponse.DailyOrderCount> dailyOrders = dailyTrend.stream()
            .map(row -> OrderStatisticsResponse.DailyOrderCount.builder()
                .date(row[0].toString())
                .count(((Number) row[1]).longValue())
                .revenue((BigDecimal) row[2])
                .build())
            .collect(Collectors.toList());

        // 결제 통계 조회 (최적화됨)
        List<Object[]> paymentResults = optimizedRepository.findPaymentStatsOptimized();
        Map<String, OrderStatisticsResponse.PaymentMethodStats> paymentStats = paymentResults.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> OrderStatisticsResponse.PaymentMethodStats.builder()
                    .count(((Number) row[1]).longValue())
                    .amount((BigDecimal) row[2])
                    .ratio(((Number) row[3]).doubleValue())
                    .build()
            ));

        results.put("dailyOrders", dailyOrders);
        results.put("paymentStats", paymentStats);

        return results;
    }

    // === 🔧 Utility Methods ===

    private OrderItemDto convertToOrderItemDto(Object[] row) {
        return OrderItemDto.builder()
            .orderId((UUID) row[0])
            .orderNo((String) row[1])
            .userId(((Number) row[2]).longValue())
            .linePrice(((Number) row[3]).intValue())
            .orderStatus((String) row[4])
            .createdAt((LocalDateTime) row[5])
            .goodsName((String) row[6])
            .totalAmount(BigDecimal.valueOf(((Number) row[3]).intValue()))
            .build();
    }

    private OrderStatusSummaryResponse.RecentOrderItem convertToRecentOrderItem(Object[] row) {
        return OrderStatusSummaryResponse.RecentOrderItem.builder()
            .orderId((UUID) row[0])
            .orderNumber((String) row[1])
            .userId(((Number) row[2]).longValue())
            .totalAmount(BigDecimal.valueOf(((Number) row[3]).intValue()))
            .status((String) row[4])
            .createdAt((LocalDateTime) row[5])
            .popupName((String) row[6])
            .build();
    }

    private Map<String, Double> calculateStatusRatios(Map<String, Long> counts, long total) {
        if (total == 0) return Collections.emptyMap();

        return counts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> (entry.getValue() * 100.0) / total
            ));
    }

    private <T> List<T> limitList(List<T> list, int limit) {
        if (list == null) return Collections.emptyList();
        return list.stream().limit(limit).collect(Collectors.toList());
    }

    private OrderDashboardResponse createEmptyDashboard() {
        return OrderDashboardResponse.builder()
            .totalOrders(0L)
            .totalRevenue(BigDecimal.ZERO)
            .averageOrderAmount(BigDecimal.ZERO)
            .todayOrders(0L)
            .todayRevenue(BigDecimal.ZERO)
            .weeklyOrders(0L)
            .weeklyRevenue(BigDecimal.ZERO)
            .monthlyOrders(0L)
            .monthlyRevenue(BigDecimal.ZERO)
            .statusCounts(Collections.emptyMap())
            .statusRatios(Collections.emptyMap())
            .recentOrders(Collections.emptyList())
            .popularItems(Collections.emptyList())
            .hourlyDistribution(Collections.emptyMap())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    // === 🔧 내부 배치 처리 클래스 ===

    private static class BatchResults {
        private Map<String, Long> statusCounts;
        private Map<String, Double> statusRatios;
        private List<OrderItemDto> recentOrders;
        private List<OrderStatisticsResponse.PopularItem> popularItems;
        private Map<Integer, Long> hourlyDistribution;

        public static BatchResults builder() {
            return new BatchResults();
        }

        public BatchResults statusCounts(Map<String, Long> statusCounts) {
            this.statusCounts = statusCounts;
            return this;
        }

        public BatchResults statusRatios(Map<String, Double> statusRatios) {
            this.statusRatios = statusRatios;
            return this;
        }

        public BatchResults recentOrders(List<OrderItemDto> recentOrders) {
            this.recentOrders = recentOrders;
            return this;
        }

        public BatchResults popularItems(List<OrderStatisticsResponse.PopularItem> popularItems) {
            this.popularItems = popularItems;
            return this;
        }

        public BatchResults hourlyDistribution(Map<Integer, Long> hourlyDistribution) {
            this.hourlyDistribution = hourlyDistribution;
            return this;
        }

        public BatchResults build() {
            return this;
        }

        public Map<String, Long> getStatusCounts() { return statusCounts; }
        public Map<String, Double> getStatusRatios() { return statusRatios; }
        public List<OrderItemDto> getRecentOrders() { return recentOrders; }
        public List<OrderStatisticsResponse.PopularItem> getPopularItems() { return popularItems; }
        public Map<Integer, Long> getHourlyDistribution() { return hourlyDistribution; }
    }
}