package com.example.orderquery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 📈 주문 상세 통계 응답 DTO - orderQuery 서비스용
 */
@Getter
@Builder
@Schema(description = "주문 상세 통계 정보")
public class OrderStatisticsResponse {

    @Schema(description = "총 주문 수", example = "1234")
    private final Long totalOrders;

    @Schema(description = "총 매출 금액", example = "15000000")
    private final BigDecimal totalRevenue;

    @Schema(description = "평균 주문 금액", example = "45000")
    private final BigDecimal averageOrderAmount;

    @Schema(description = "오늘 주문 수", example = "23")
    private final Long todayOrders;

    @Schema(description = "이번 주 주문 수", example = "156")
    private final Long weeklyOrders;

    @Schema(description = "이번 달 주문 수", example = "678")
    private final Long monthlyOrders;

    @Schema(description = "주문 상태별 카운트")
    private final Map<String, Long> statusCounts;

    @Schema(description = "주문 상태별 비율 (%)")
    private final Map<String, Double> statusRatios;

    @Schema(description = "최근 7일간 일별 주문 수")
    private final List<DailyOrderCount> dailyOrders;

    @Schema(description = "인기 상품 TOP 10")
    private final List<PopularItem> popularItems;

    @Schema(description = "결제 수단별 통계")
    private final Map<String, PaymentMethodStats> paymentMethodStats;

    @Schema(description = "시간대별 주문 분포")
    private final Map<Integer, Long> hourlyDistribution;

    @Schema(description = "요일별 주문 패턴")
    private final Map<String, Long> weeklyPattern;

    @Schema(description = "팝업별 매출 순위")
    private final List<PopupSalesRank> popupSalesRanks;

    @Schema(description = "고객 세그먼트 분석")
    private final CustomerSegmentAnalysis customerSegmentAnalysis;

    @Getter
    @Builder
    @Schema(description = "일별 주문 수")
    public static class DailyOrderCount {
        @Schema(description = "날짜", example = "2026-02-14")
        private final String date;

        @Schema(description = "주문 수", example = "45")
        private final Long count;

        @Schema(description = "매출 금액", example = "1250000")
        private final BigDecimal revenue;

        @Schema(description = "전일 대비 증감율 (%)", example = "12.5")
        private final Double changeRate;
    }

    @Getter
    @Builder
    @Schema(description = "인기 상품")
    public static class PopularItem {
        @Schema(description = "상품 ID")
        private final String itemId;

        @Schema(description = "상품명", example = "팝콘 세트 A")
        private final String itemName;

        @Schema(description = "상품 카테고리", example = "음식")
        private final String category;

        @Schema(description = "주문 횟수", example = "123")
        private final Long orderCount;

        @Schema(description = "총 매출", example = "2460000")
        private final BigDecimal totalRevenue;

        @Schema(description = "평균 단가", example = "20000")
        private final BigDecimal averagePrice;

        @Schema(description = "전체 주문 대비 비율 (%)", example = "8.5")
        private final Double orderRatio;
    }

    @Getter
    @Builder
    @Schema(description = "결제 수단별 통계")
    public static class PaymentMethodStats {
        @Schema(description = "결제 건수", example = "456")
        private final Long count;

        @Schema(description = "총 결제 금액", example = "12300000")
        private final BigDecimal amount;

        @Schema(description = "비율 (%)", example = "78.5")
        private final Double ratio;

        @Schema(description = "평균 결제 금액", example = "26973")
        private final BigDecimal averageAmount;

        @Schema(description = "성공률 (%)", example = "94.2")
        private final Double successRate;
    }

    @Getter
    @Builder
    @Schema(description = "팝업별 매출 순위")
    public static class PopupSalesRank {
        @Schema(description = "팝업 ID")
        private final String popupId;

        @Schema(description = "팝업명", example = "강남 팝업스토어")
        private final String popupName;

        @Schema(description = "지역", example = "강남구")
        private final String location;

        @Schema(description = "주문 수", example = "234")
        private final Long orderCount;

        @Schema(description = "매출액", example = "5600000")
        private final BigDecimal revenue;

        @Schema(description = "순위", example = "1")
        private final Integer rank;

        @Schema(description = "전체 매출 대비 비율 (%)", example = "23.4")
        private final Double revenueRatio;
    }

    @Getter
    @Builder
    @Schema(description = "고객 세그먼트 분석")
    public static class CustomerSegmentAnalysis {
        @Schema(description = "신규 고객 수", example = "145")
        private final Long newCustomers;

        @Schema(description = "재구매 고객 수", example = "456")
        private final Long returningCustomers;

        @Schema(description = "VIP 고객 수", example = "78")
        private final Long vipCustomers;

        @Schema(description = "신규 고객 비율 (%)", example = "21.4")
        private final Double newCustomerRatio;

        @Schema(description = "재구매율 (%)", example = "67.2")
        private final Double returningCustomerRatio;

        @Schema(description = "고객당 평균 주문 횟수", example = "2.3")
        private final Double avgOrdersPerCustomer;

        @Schema(description = "고객 생애 가치 (CLV)", example = "125000")
        private final BigDecimal customerLifetimeValue;
    }

    @Getter
    @Builder
    @Schema(description = "성과 지표")
    public static class PerformanceMetrics {
        @Schema(description = "전환율 (%)", example = "3.2")
        private final Double conversionRate;

        @Schema(description = "장바구니 포기율 (%)", example = "15.6")
        private final Double cartAbandonmentRate;

        @Schema(description = "평균 주문 처리 시간 (분)", example = "12")
        private final Double avgProcessingTime;

        @Schema(description = "고객 만족도", example = "4.7")
        private final Double customerSatisfaction;

        @Schema(description = "리뷰 평점", example = "4.5")
        private final Double averageRating;
    }
}