package com.example.orderquery.dto.response;

import com.example.orderquery.domain.itemView.dto.OrderItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🏠 대시보드 메인 화면 응답 DTO
 */
@Getter
@Builder
@Schema(description = "대시보드 메인 화면 데이터")
public class OrderDashboardResponse {

    // === 📊 주요 지표 ===
    @Schema(description = "총 주문 수", example = "1234")
    private final Long totalOrders;

    @Schema(description = "총 매출 금액", example = "56789000")
    private final BigDecimal totalRevenue;

    @Schema(description = "평균 주문 금액", example = "46000")
    private final BigDecimal averageOrderAmount;

    // === 📅 기간별 통계 ===
    @Schema(description = "오늘 주문 수", example = "23")
    private final Long todayOrders;

    @Schema(description = "오늘 매출", example = "1150000")
    private final BigDecimal todayRevenue;

    @Schema(description = "이번 주 주문 수", example = "156")
    private final Long weeklyOrders;

    @Schema(description = "이번 주 매출", example = "7200000")
    private final BigDecimal weeklyRevenue;

    @Schema(description = "이번 달 주문 수", example = "678")
    private final Long monthlyOrders;

    @Schema(description = "이번 달 매출", example = "31200000")
    private final BigDecimal monthlyRevenue;

    // === 📋 상태별 분포 ===
    @Schema(description = "주문 상태별 카운트")
    private final Map<String, Long> statusCounts;

    @Schema(description = "주문 상태별 비율 (%)")
    private final Map<String, Double> statusRatios;

    // === 📝 최근 주문 목록 ===
    @Schema(description = "최근 주문 목록 (10개)")
    private final List<OrderItemDto> recentOrders;

    // === 🔥 인기 상품 ===
    @Schema(description = "인기 상품 TOP 5")
    private final List<OrderStatisticsResponse.PopularItem> popularItems;

    // === ⏰ 시간대별 분포 ===
    @Schema(description = "시간대별 주문 분포 (0-23시)")
    private final Map<Integer, Long> hourlyDistribution;

    // === 📈 성장률 정보 ===
    @Schema(description = "전주 대비 성장률 (%)", example = "15.2")
    private final Double weeklyGrowthRate;

    @Schema(description = "전월 대비 성장률 (%)", example = "8.7")
    private final Double monthlyGrowthRate;

    // === 🚨 알림 정보 ===
    @Schema(description = "긴급 처리 필요한 주문 수", example = "3")
    private final Long urgentOrdersCount;

    @Schema(description = "처리 지연 주문 수 (30분 이상)", example = "2")
    private final Long delayedOrdersCount;

    // === 💳 결제 정보 ===
    @Schema(description = "오늘 결제 성공률 (%)", example = "94.5")
    private final Double todayPaymentSuccessRate;

    @Schema(description = "평균 주문 처리 시간 (분)", example = "12")
    private final Double averageProcessingTimeMinutes;

    // === 📍 지역별 정보 ===
    @Schema(description = "활성 팝업 수", example = "8")
    private final Long activePopupsCount;

    @Schema(description = "가장 인기 있는 팝업 이름", example = "강남 팝업스토어")
    private final String mostPopularPopupName;

    // === 🔄 업데이트 정보 ===
    @Schema(description = "마지막 업데이트 시간")
    private final LocalDateTime lastUpdated;

    @Schema(description = "다음 자동 업데이트 예정 시간")
    private final LocalDateTime nextUpdateAt;

    // === 📊 추가 지표 ===
    @Schema(description = "신규 고객 비율 (%)", example = "23.4")
    private final Double newCustomerRatio;

    @Schema(description = "재구매 고객 비율 (%)", example = "76.6")
    private final Double returningCustomerRatio;

    @Schema(description = "평균 배송 시간 (시간)", example = "2.5")
    private final Double averageDeliveryHours;

    @Schema(description = "고객 만족도 점수", example = "4.7")
    private final Double customerSatisfactionScore;

    /**
     * 📊 대시보드 요약 정보
     */
    @Getter
    @Builder
    @Schema(description = "대시보드 요약 정보")
    public static class DashboardSummary {
        @Schema(description = "총 주문 수")
        private final Long totalOrders;

        @Schema(description = "총 매출")
        private final BigDecimal totalRevenue;

        @Schema(description = "성장률")
        private final Double growthRate;

        @Schema(description = "처리율")
        private final Double processingRate;
    }

    /**
     * 🚨 알림 정보
     */
    @Getter
    @Builder
    @Schema(description = "대시보드 알림 정보")
    public static class AlertInfo {
        @Schema(description = "알림 레벨", example = "WARNING")
        private final String level;

        @Schema(description = "알림 메시지", example = "처리 지연 주문이 있습니다")
        private final String message;

        @Schema(description = "알림 수", example = "3")
        private final Long count;

        @Schema(description = "알림 생성 시간")
        private final LocalDateTime createdAt;
    }

    /**
     * 📈 트렌드 정보
     */
    @Getter
    @Builder
    @Schema(description = "트렌드 정보")
    public static class TrendInfo {
        @Schema(description = "트렌드 방향", example = "UP")
        private final String direction;

        @Schema(description = "변화율 (%)", example = "15.2")
        private final Double changeRate;

        @Schema(description = "비교 기간", example = "지난 주 대비")
        private final String comparisonPeriod;
    }
}