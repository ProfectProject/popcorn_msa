package com.popcorn.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 📈 주문 통계 응답 DTO
 */
@Getter
@Builder
@Schema(description = "주문 통계 정보")
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

    @Schema(description = "인기 상품 TOP 5")
    private final List<PopularItem> popularItems;

    @Schema(description = "결제 수단별 통계")
    private final Map<String, PaymentMethodStats> paymentMethodStats;

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
    }

    @Getter
    @Builder
    @Schema(description = "인기 상품")
    public static class PopularItem {
        @Schema(description = "상품 ID")
        private final String itemId;

        @Schema(description = "상품명", example = "팝콘 세트 A")
        private final String itemName;

        @Schema(description = "주문 횟수", example = "123")
        private final Long orderCount;

        @Schema(description = "총 매출", example = "2460000")
        private final BigDecimal totalRevenue;
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
    }
}