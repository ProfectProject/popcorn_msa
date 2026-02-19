package com.example.orderquery.domain.dashboard.service;

import com.example.orderquery.domain.itemView.dto.OrderItemDto;
import com.example.orderquery.domain.itemView.dto.OrderItemPageDto;
import com.example.orderquery.domain.itemView.dto.OrderItemQuery;
import com.example.orderquery.domain.itemView.dto.PageInfoDto;
import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.repository.OrderItemViewRepository;
import com.example.orderquery.domain.itemView.repository.OrderItemViewSpecifications;
import com.example.orderquery.dto.response.OrderDashboardResponse;
import com.example.orderquery.dto.response.OrderStatisticsResponse;
import com.example.orderquery.dto.response.OrderStatusSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;

/**
 * 📊 주문 대시보드 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderDashboardService {

    private final OrderItemViewRepository orderItemViewRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${USERS_SERVICE_BASE_URL:http://localhost:8080}")
    private String usersServiceBaseUrl;

    /**
     * 🏠 대시보드 메인 데이터 조회 (시스템 전체)
     */
    @Cacheable(value = "dashboardMain", key = "#baseDate", unless = "#result == null")
    public OrderDashboardResponse getDashboardMainData(LocalDate baseDate) {
        log.info("📊 대시보드 메인 데이터 조회 시작 - baseDate: {}", baseDate);

        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(LocalTime.MAX);
        LocalDateTime startOfWeek = baseDate.minusDays(baseDate.getDayOfWeek().getValue() - 1).atStartOfDay();
        LocalDateTime startOfMonth = baseDate.withDayOfMonth(1).atStartOfDay();

        // 전체 통계
        long totalOrders = orderItemViewRepository.count();
        BigDecimal totalRevenue = orderItemViewRepository.findTotalRevenue().orElse(BigDecimal.ZERO);
        BigDecimal avgOrderAmount = totalOrders > 0 ?
            totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 오늘 통계
        long todayOrders = orderItemViewRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        BigDecimal todayRevenue = orderItemViewRepository.findRevenueByDateRange(startOfDay, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 이번 주 통계
        long weeklyOrders = orderItemViewRepository.countByCreatedAtBetween(startOfWeek, endOfDay);
        BigDecimal weeklyRevenue = orderItemViewRepository.findRevenueByDateRange(startOfWeek, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 이번 달 통계
        long monthlyOrders = orderItemViewRepository.countByCreatedAtBetween(startOfMonth, endOfDay);
        BigDecimal monthlyRevenue = orderItemViewRepository.findRevenueByDateRange(startOfMonth, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 상태별 통계
        Map<String, Long> statusCounts = getOrderStatusCounts();
        Map<String, Double> statusRatios = calculateStatusRatios(statusCounts, totalOrders);

        // 최근 주문 목록 (10개)
        List<OrderItemDto> recentOrders = getRecentOrders(10);

        // 인기 상품 TOP 5
        List<OrderStatisticsResponse.PopularItem> popularItems = getPopularItems(5);

        // 시간대별 주문 분포 (오늘)
        Map<Integer, Long> hourlyDistribution = getHourlyOrderDistribution(baseDate);

        return OrderDashboardResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderAmount(avgOrderAmount)
                .todayOrders(todayOrders)
                .todayRevenue(todayRevenue)
                .weeklyOrders(weeklyOrders)
                .weeklyRevenue(weeklyRevenue)
                .monthlyOrders(monthlyOrders)
                .monthlyRevenue(monthlyRevenue)
                .statusCounts(statusCounts)
                .statusRatios(statusRatios)
                .recentOrders(recentOrders)
                .popularItems(popularItems)
                .hourlyDistribution(hourlyDistribution)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * 🏪 스토어별 대시보드 메인 데이터 조회
     */
    @Cacheable(value = "optimizedDashboardByStore", key = "#storeId + '_' + #baseDate", unless = "#result == null")
    public OrderDashboardResponse getDashboardMainDataByStore(UUID storeId, LocalDate baseDate) {
        log.info("🏪 스토어별 대시보드 메인 데이터 조회 시작 - storeId: {}, baseDate: {}", storeId, baseDate);

        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(LocalTime.MAX);
        LocalDateTime startOfWeek = baseDate.minusDays(baseDate.getDayOfWeek().getValue() - 1).atStartOfDay();
        LocalDateTime startOfMonth = baseDate.withDayOfMonth(1).atStartOfDay();

        // 스토어별 전체 통계
        long totalOrders = orderItemViewRepository.countByStoreId(storeId);
        BigDecimal totalRevenue = orderItemViewRepository.findTotalRevenueByStore(storeId).orElse(BigDecimal.ZERO);
        BigDecimal avgOrderAmount = totalOrders > 0 ?
            totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 스토어별 오늘 통계
        long todayOrders = orderItemViewRepository.countByStoreIdAndCreatedAtBetween(storeId, startOfDay, endOfDay);
        BigDecimal todayRevenue = orderItemViewRepository.findRevenueByStoreAndDateRange(storeId, startOfDay, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 스토어별 이번 주 통계
        long weeklyOrders = orderItemViewRepository.countByStoreIdAndCreatedAtBetween(storeId, startOfWeek, endOfDay);
        BigDecimal weeklyRevenue = orderItemViewRepository.findRevenueByStoreAndDateRange(storeId, startOfWeek, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 스토어별 이번 달 통계
        long monthlyOrders = orderItemViewRepository.countByStoreIdAndCreatedAtBetween(storeId, startOfMonth, endOfDay);
        BigDecimal monthlyRevenue = orderItemViewRepository.findRevenueByStoreAndDateRange(storeId, startOfMonth, endOfDay)
            .orElse(BigDecimal.ZERO);

        // 스토어별 상태별 통계
        Map<String, Long> statusCounts = getOrderStatusCountsByStore(storeId);
        Map<String, Double> statusRatios = calculateStatusRatios(statusCounts, totalOrders);

        // 스토어별 최근 주문 목록 (10개)
        List<OrderItemDto> recentOrders = getRecentOrdersByStore(storeId, 10);

        // 스토어별 인기 상품 TOP 5
        List<OrderStatisticsResponse.PopularItem> popularItems = getPopularItemsByStore(storeId, 5);

        // 스토어별 시간대별 주문 분포 (오늘)
        Map<Integer, Long> hourlyDistribution = getHourlyOrderDistributionByStore(storeId, baseDate);

        return OrderDashboardResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderAmount(avgOrderAmount)
                .todayOrders(todayOrders)
                .todayRevenue(todayRevenue)
                .weeklyOrders(weeklyOrders)
                .weeklyRevenue(weeklyRevenue)
                .monthlyOrders(monthlyOrders)
                .monthlyRevenue(monthlyRevenue)
                .statusCounts(statusCounts)
                .statusRatios(statusRatios)
                .recentOrders(recentOrders)
                .popularItems(popularItems)
                .hourlyDistribution(hourlyDistribution)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * 📋 전체 주문 목록 조회 (고급 필터링)
     */
    public OrderItemPageDto getAllOrdersWithFilters(
            String status, LocalDate startDate, LocalDate endDate, Long userId,
            Integer minAmount, Integer maxAmount, String popupId,
            String paymentMethod, String orderType,
            int page, int size, String sortBy, String sortDirection) {

        // Specification 빌드
        Specification<OrderItemView> spec = null;

        if (status != null && !status.isEmpty()) {
            Specification<OrderItemView> statusSpec = OrderItemViewSpecifications.hasStatus(OrderStatus.valueOf(status));
            spec = spec != null ? spec.and(statusSpec) : statusSpec;
        }

        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
            Specification<OrderItemView> dateSpec = OrderItemViewSpecifications.createdAtBetween(startDateTime, endDateTime);
            spec = spec != null ? spec.and(dateSpec) : dateSpec;
        }

        if (userId != null) {
            Specification<OrderItemView> userSpec = OrderItemViewSpecifications.hasUserId(userId);
            spec = spec != null ? spec.and(userSpec) : userSpec;
        }

        if (minAmount != null && maxAmount != null) {
            Specification<OrderItemView> amountSpec = OrderItemViewSpecifications.totalAmountBetween(
                BigDecimal.valueOf(minAmount), BigDecimal.valueOf(maxAmount));
            spec = spec != null ? spec.and(amountSpec) : amountSpec;
        }

        if (popupId != null && !popupId.isEmpty()) {
            Specification<OrderItemView> popupSpec = OrderItemViewSpecifications.hasPopupId(popupId);
            spec = spec != null ? spec.and(popupSpec) : popupSpec;
        }

        // 정렬 설정
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ?
            Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // 페이지 요청
        Pageable pageable = PageRequest.of(page, size, sort);

        // 데이터 조회
        Page<OrderItemView> orderPage = orderItemViewRepository.findAll(spec, pageable);

        Map<Long, String> userNames = resolveUserNames(
                orderPage.getContent().stream()
                        .map(OrderItemView::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        // DTO 변환
        List<OrderItemDto> orderItems = orderPage.getContent().stream()
                .map(orderView -> convertToDto(orderView, userNames.get(orderView.getUserId())))
                .collect(Collectors.toList());

        // 페이지 정보
        PageInfoDto pageInfo = PageInfoDto.builder()
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .page(page)
                .currentPage(page)
                .size(size)
                .hasNext(orderPage.hasNext())
                .hasPrevious(orderPage.hasPrevious())
                .build();

        return OrderItemPageDto.builder()
                .orders(orderItems)
                .pageInfo(pageInfo)
                .build();
    }

    /**
     * 📈 상세 주문 통계
     */
    @Cacheable(value = "optimizedStatistics", key = "'detailed_' + #days + '_' + #includeTypes", unless = "#result == null")
    public OrderStatisticsResponse getDetailedStatistics(int days, String includeTypes) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        // 기본 통계
        long totalOrders = orderItemViewRepository.countByCreatedAtBetween(startDate, endDate);
        BigDecimal totalRevenue = orderItemViewRepository.findRevenueByDateRange(startDate, endDate)
            .orElse(BigDecimal.ZERO);
        BigDecimal avgAmount = totalOrders > 0 ?
            totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 상태별 통계
        Map<String, Long> statusCounts = getOrderStatusCountsByDateRange(startDate, endDate);
        Map<String, Double> statusRatios = calculateStatusRatios(statusCounts, totalOrders);

        // 일별 주문 수 (최근 7일)
        List<OrderStatisticsResponse.DailyOrderCount> dailyOrders = getDailyOrderCounts(7);

        // 인기 상품 TOP 10
        List<OrderStatisticsResponse.PopularItem> popularItems = getPopularItems(10);

        // 결제 수단별 통계
        Map<String, OrderStatisticsResponse.PaymentMethodStats> paymentStats = getPaymentMethodStatistics();

        return OrderStatisticsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderAmount(avgAmount)
                .todayOrders(getTodayOrderCount())
                .weeklyOrders(getWeeklyOrderCount())
                .monthlyOrders(getMonthlyOrderCount())
                .statusCounts(statusCounts)
                .statusRatios(statusRatios)
                .dailyOrders(dailyOrders)
                .popularItems(popularItems)
                .paymentMethodStats(paymentStats)
                .build();
    }

    /**
     * 📊 실시간 주문 상태별 요약
     */
    public OrderStatusSummaryResponse getRealtimeStatusSummary() {
        // 상태별 카운트
        Map<String, Long> counts = getOrderStatusCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        // 상태별 비율 계산
        Map<String, Double> ratios = calculateStatusRatios(counts, total);

        // 상태별 최신 주문 목록
        OrderStatusSummaryResponse.StatusRecentOrders recentOrders = getStatusRecentOrders();

        // 긴급 처리 카운트
        OrderStatusSummaryResponse.UrgentCounts urgentCounts = getUrgentCounts();

        return OrderStatusSummaryResponse.builder()
                .statusCounts(OrderStatusSummaryResponse.StatusCounts.builder()
                    .pending(counts.getOrDefault("PENDING", 0L))
                    .confirmed(counts.getOrDefault("CONFIRMED", 0L))
                    .paid(counts.getOrDefault("PAID", 0L))
                    .completed(counts.getOrDefault("COMPLETED", 0L))
                    .cancelled(counts.getOrDefault("CANCELLED", 0L))
                    .reserved(counts.getOrDefault("RESERVED", 0L))
                    .paymentPending(counts.getOrDefault("PAYMENT_PENDING", 0L))
                    .build())
                .statusRatios(OrderStatusSummaryResponse.StatusRatios.builder()
                    .pending(ratios.getOrDefault("PENDING", 0.0))
                    .confirmed(ratios.getOrDefault("CONFIRMED", 0.0))
                    .paid(ratios.getOrDefault("PAID", 0.0))
                    .completed(ratios.getOrDefault("COMPLETED", 0.0))
                    .cancelled(ratios.getOrDefault("CANCELLED", 0.0))
                    .reserved(ratios.getOrDefault("RESERVED", 0.0))
                    .paymentPending(ratios.getOrDefault("PAYMENT_PENDING", 0.0))
                    .build())
                .recentOrders(recentOrders)
                .urgentCounts(urgentCounts)
                .build();
    }

    /**
     * 🔍 주문 검색
     */
    public List<OrderItemDto> searchOrders(String keyword, String searchScope, int limit) {
        Specification<OrderItemView> spec = OrderItemViewSpecifications.searchByKeyword(keyword, searchScope);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderItemView> results = orderItemViewRepository.findAll(spec, pageable);

        return results.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // === Private Helper Methods ===

    private Map<String, Long> getOrderStatusCounts() {
        List<Object[]> results = orderItemViewRepository.findOrderCountsByStatus();
        return results.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> ((Number) row[1]).longValue()
                ));
    }

    private Map<String, Long> getOrderStatusCountsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = orderItemViewRepository.findOrderCountsByStatusAndDateRange(startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> ((Number) row[1]).longValue()
                ));
    }

    private Map<String, Double> calculateStatusRatios(Map<String, Long> counts, long total) {
        if (total == 0) return Collections.emptyMap();

        return counts.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> (entry.getValue() * 100.0) / total
                ));
    }

    private List<OrderItemDto> getRecentOrders(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderItemView> recentOrders = orderItemViewRepository.findAll(pageable);

        return recentOrders.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private List<OrderStatisticsResponse.PopularItem> getPopularItems(int limit) {
        List<Object[]> results = orderItemViewRepository.findPopularItems(limit);

        return results.stream()
                .filter(row -> row[0] != null) // null 값 필터링
                .map(row -> OrderStatisticsResponse.PopularItem.builder()
                    .itemId(row[0] != null ? row[0].toString() : "unknown")
                    .itemName(row[0] != null ? row[0].toString() : "상품명 미정")
                    .orderCount(((Number) row[1]).longValue())
                    .totalRevenue(row[2] != null ?
                        (row[2] instanceof BigDecimal ? (BigDecimal) row[2] :
                         BigDecimal.valueOf(((Number) row[2]).longValue())) : BigDecimal.ZERO)
                    .build())
                .collect(Collectors.toList());
    }

    private Map<Integer, Long> getHourlyOrderDistribution(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Object[]> results = orderItemViewRepository.findHourlyOrderDistribution(startOfDay, endOfDay);

        return results.stream()
                .collect(Collectors.toMap(
                    row -> ((Number) row[0]).intValue(),
                    row -> ((Number) row[1]).longValue()
                ));
    }

    private List<OrderStatisticsResponse.DailyOrderCount> getDailyOrderCounts(int days) {
        List<Object[]> results = orderItemViewRepository.findDailyOrderCounts(days);

        return results.stream()
                .map(row -> OrderStatisticsResponse.DailyOrderCount.builder()
                    .date(row[0].toString())
                    .count(((Number) row[1]).longValue())
                    .revenue(row[2] != null ?
                        (row[2] instanceof BigDecimal ? (BigDecimal) row[2] :
                         BigDecimal.valueOf(((Number) row[2]).longValue())) : BigDecimal.ZERO)
                    .build())
                .collect(Collectors.toList());
    }

    private Map<String, OrderStatisticsResponse.PaymentMethodStats> getPaymentMethodStatistics() {
        List<Object[]> results = orderItemViewRepository.findPaymentStatusStatistics();
        long total = orderItemViewRepository.count();

        return results.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> {
                        Long count = ((Number) row[1]).longValue();
                        BigDecimal amount = row[2] != null ?
                            (row[2] instanceof BigDecimal ? (BigDecimal) row[2] :
                             BigDecimal.valueOf(((Number) row[2]).longValue())) : BigDecimal.ZERO;
                        Double ratio = total > 0 ? (count * 100.0) / total : 0.0;

                        return OrderStatisticsResponse.PaymentMethodStats.builder()
                            .count(count)
                            .amount(amount)
                            .ratio(ratio)
                            .build();
                    }
                ));
    }

    private OrderStatusSummaryResponse.StatusRecentOrders getStatusRecentOrders() {
        // 🚀 성능 최적화: 단일 쿼리로 모든 최신 주문 조회 후 메모리에서 분류
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "orderedAt"));
        Page<OrderItemView> allRecentOrders = orderItemViewRepository.findAll(pageable);

        Map<String, List<OrderStatusSummaryResponse.RecentOrderItem>> ordersByStatus =
            allRecentOrders.getContent().stream()
                .map(order -> OrderStatusSummaryResponse.RecentOrderItem.builder()
                    .orderId(order.getOrderId())
                    .orderNumber(order.getOrderNo())
                    .userId(order.getUserId())
                    .totalAmount(BigDecimal.valueOf(order.getLinePrice()))
                    .createdAt(order.getCreatedAt())
                    .status(order.getOrderStatus() != null ? order.getOrderStatus().name() : "UNKNOWN")
                    .popupName("팝업명 미정")
                    .build())
                .collect(Collectors.groupingBy(
                    OrderStatusSummaryResponse.RecentOrderItem::getStatus,
                    Collectors.toList()
                ));

        return OrderStatusSummaryResponse.StatusRecentOrders.builder()
                .pending(ordersByStatus.getOrDefault("PENDING", Collections.emptyList()).stream().limit(5).collect(Collectors.toList()))
                .paymentPending(ordersByStatus.getOrDefault("PAYMENT_PENDING", Collections.emptyList()).stream().limit(5).collect(Collectors.toList()))
                .completed(ordersByStatus.getOrDefault("COMPLETED", Collections.emptyList()).stream().limit(5).collect(Collectors.toList()))
                .cancelled(ordersByStatus.getOrDefault("CANCELLED", Collections.emptyList()).stream().limit(5).collect(Collectors.toList()))
                .build();
    }

    private List<OrderStatusSummaryResponse.RecentOrderItem> getRecentOrdersByStatus(String status, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderItemView> orders = orderItemViewRepository.findByOrderStatus(OrderStatus.valueOf(status), pageable);

        return orders.getContent().stream()
                .map(order -> OrderStatusSummaryResponse.RecentOrderItem.builder()
                    .orderId(order.getOrderId())
                    .orderNumber(order.getOrderNo())
                    .userId(order.getUserId())
                    .totalAmount(BigDecimal.valueOf(order.getLinePrice()))
                    .createdAt(order.getCreatedAt())
                    .status(order.getOrderStatus().name())
                    .popupName("팝업명 미정")  // 현재는 엔터티에 없으므로 기본값
                    .build())
                .collect(Collectors.toList());
    }

    private OrderStatusSummaryResponse.UrgentCounts getUrgentCounts() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        return OrderStatusSummaryResponse.UrgentCounts.builder()
                .longWaitingOrders(orderItemViewRepository.countLongWaitingOrders(thirtyMinutesAgo))
                .paymentFailedOrders(orderItemViewRepository.countPaymentFailedOrders())
                .cancellationRequests(orderItemViewRepository.countCancellationRequests())
                .refundPendingOrders(orderItemViewRepository.countRefundPendingOrders())
                .build();
    }

    private Long getTodayOrderCount() {
        LocalDate today = LocalDate.now();
        return orderItemViewRepository.countByCreatedAtBetween(
            today.atStartOfDay(), today.atTime(LocalTime.MAX));
    }

    private Long getWeeklyOrderCount() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        return orderItemViewRepository.countByCreatedAtBetween(
            startOfWeek.atStartOfDay(), today.atTime(LocalTime.MAX));
    }

    private Long getMonthlyOrderCount() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        return orderItemViewRepository.countByCreatedAtBetween(
            startOfMonth.atStartOfDay(), today.atTime(LocalTime.MAX));
    }

    private Map<Long, String> resolveUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        RestClient client = restClientBuilder.baseUrl(usersServiceBaseUrl).build();
        Map<Long, String> userNames = new HashMap<>();

        for (Long userId : userIds) {
            try {
                UserSimpleResponse response = client.get()
                        .uri("/api/users/v1/users/{userId}", userId)
                        .header("X-Internal-Service", "orderquery-service")
                        .header("X-Internal-Call", "true")
                        .retrieve()
                        .body(UserSimpleResponse.class);

                if (response != null && response.name() != null && !response.name().isBlank()) {
                    userNames.put(userId, response.name());
                }
            } catch (Exception ex) {
                log.debug("사용자 이름 조회 실패 - userId: {}", userId, ex);
            }
        }

        return userNames;
    }

    private OrderItemDto convertToDto(OrderItemView orderView) {
        return convertToDto(orderView, null);
    }

    private OrderItemDto convertToDto(OrderItemView orderView, String customerName) {
        return OrderItemDto.builder()
                .popupId(orderView.getId().getPopupId())
                .orderGoodsId(orderView.getId().getOrderGoodsId())
                .orderId(orderView.getOrderId())
                .storeId(orderView.getStoreId())
                .userId(orderView.getUserId())
                .customerName(customerName)
                .orderNo(orderView.getOrderNo())
                .orderNumber(orderView.getOrderNo())  // 호환성을 위한 별칭
                .orderStatus(orderView.getOrderStatus() != null ? orderView.getOrderStatus().name() : null)
                .status(orderView.getOrderStatus() != null ? orderView.getOrderStatus().name() : null)  // 호환성을 위한 별칭
                .orderedAt(orderView.getOrderedAt())
                .createdAt(orderView.getCreatedAt())
                .updatedAt(orderView.getUpdatedAt())
                .itemType(orderView.getItemType())
                .scheduleId(orderView.getScheduleId())
                .scheduleStartAt(orderView.getScheduleStartAt())
                .scheduleEndAt(orderView.getScheduleEndAt())
                .goodsId(orderView.getGoodsId())
                .goodsName(orderView.getGoodsName())
                .stockUnit(orderView.getStockUnit())
                .qty(orderView.getQty())
                .unitPrice(orderView.getUnitPrice())
                .linePrice(orderView.getLinePrice())
                .totalAmount(BigDecimal.valueOf(orderView.getLinePrice()))  // 호환성을 위한 BigDecimal 변환
                .paymentStatus(orderView.getPaymentStatus() != null ? orderView.getPaymentStatus().name() : null)
                .paymentApprovedAt(orderView.getPaymentApprovedAt())
                .checkedIn(orderView.isCheckedIn())
                .checkinAt(orderView.getCheckinAt())
                .popupName("팝업명 미정")  // 현재는 엔터티에 없으므로 기본값
                .build();
    }

    private record UserSimpleResponse(Long id, String name) {}

    // === 🏪 스토어별 헬퍼 메서드들 ===

    private Map<String, Long> getOrderStatusCountsByStore(UUID storeId) {
        List<Object[]> results = orderItemViewRepository.findOrderCountsByStatusAndStore(storeId);
        return results.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> ((Number) row[1]).longValue()
                ));
    }

    private List<OrderItemDto> getRecentOrdersByStore(UUID storeId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 스토어별 조회를 위한 Specification 사용
        Specification<OrderItemView> spec = OrderItemViewSpecifications.hasStoreId(storeId);
        Page<OrderItemView> recentOrders = orderItemViewRepository.findAll(spec, pageable);

        return recentOrders.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private List<OrderStatisticsResponse.PopularItem> getPopularItemsByStore(UUID storeId, int limit) {
        List<Object[]> results = orderItemViewRepository.findPopularItemsByStore(storeId, limit);

        return results.stream()
                .filter(row -> row[0] != null) // null 값 필터링
                .map(row -> OrderStatisticsResponse.PopularItem.builder()
                    .itemId(row[0] != null ? row[0].toString() : "unknown")
                    .itemName(row[0] != null ? row[0].toString() : "상품명 미정")
                    .orderCount(((Number) row[1]).longValue())
                    .totalRevenue(row[2] != null ?
                        (row[2] instanceof BigDecimal ? (BigDecimal) row[2] :
                         BigDecimal.valueOf(((Number) row[2]).longValue())) : BigDecimal.ZERO)
                    .build())
                .collect(Collectors.toList());
    }

    private Map<Integer, Long> getHourlyOrderDistributionByStore(UUID storeId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Object[]> results = orderItemViewRepository.findHourlyOrderDistributionByStore(storeId, startOfDay, endOfDay);

        return results.stream()
                .collect(Collectors.toMap(
                    row -> ((Number) row[0]).intValue(),
                    row -> ((Number) row[1]).longValue()
                ));
    }
}
