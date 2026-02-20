package com.popcorn.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 📋 주문 상태별 요약 응답 DTO
 */
@Getter
@Builder
@Schema(description = "주문 상태별 요약 정보")
public class OrderStatusSummaryResponse {

    @Schema(description = "상태별 주문 수")
    private final StatusCounts statusCounts;

    @Schema(description = "상태별 비율")
    private final StatusRatios statusRatios;

    @Schema(description = "상태별 최신 주문 목록")
    private final StatusRecentOrders recentOrders;

    @Schema(description = "긴급 처리 필요한 주문 수")
    private final UrgentCounts urgentCounts;

    @Getter
    @Builder
    @Schema(description = "상태별 주문 수")
    public static class StatusCounts {
        @Schema(description = "처리 대기 중", example = "12")
        private final Long pending;

        @Schema(description = "확인됨", example = "45")
        private final Long confirmed;

        @Schema(description = "결제 완료", example = "156")
        private final Long paid;

        @Schema(description = "완료됨", example = "1234")
        private final Long completed;

        @Schema(description = "취소됨", example = "78")
        private final Long cancelled;

        @Schema(description = "예약됨", example = "23")
        private final Long reserved;

        @Schema(description = "결제 대기", example = "8")
        private final Long paymentPending;
    }

    @Getter
    @Builder
    @Schema(description = "상태별 비율 (%)")
    public static class StatusRatios {
        @Schema(description = "처리 대기 중 비율", example = "8.5")
        private final Double pending;

        @Schema(description = "확인됨 비율", example = "12.3")
        private final Double confirmed;

        @Schema(description = "결제 완료 비율", example = "25.7")
        private final Double paid;

        @Schema(description = "완료됨 비율", example = "78.9")
        private final Double completed;

        @Schema(description = "취소됨 비율", example = "5.2")
        private final Double cancelled;

        @Schema(description = "예약됨 비율", example = "3.1")
        private final Double reserved;

        @Schema(description = "결제 대기 비율", example = "1.8")
        private final Double paymentPending;
    }

    @Getter
    @Builder
    @Schema(description = "상태별 최신 주문")
    public static class StatusRecentOrders {
        @Schema(description = "대기 중인 주문")
        private final List<RecentOrderItem> pending;

        @Schema(description = "결제 대기 주문")
        private final List<RecentOrderItem> paymentPending;

        @Schema(description = "최근 완료 주문")
        private final List<RecentOrderItem> completed;

        @Schema(description = "최근 취소 주문")
        private final List<RecentOrderItem> cancelled;
    }

    @Getter
    @Builder
    @Schema(description = "최신 주문 아이템")
    public static class RecentOrderItem {
        @Schema(description = "주문 ID")
        private final UUID orderId;

        @Schema(description = "주문 번호", example = "ORDER-20260214-001")
        private final String orderNumber;

        @Schema(description = "사용자 ID", example = "123")
        private final Long userId;

        @Schema(description = "주문 금액", example = "45000")
        private final BigDecimal totalAmount;

        @Schema(description = "주문 일시")
        private final LocalDateTime createdAt;

        @Schema(description = "주문 상태", example = "PENDING")
        private final String status;

        @Schema(description = "팝업명", example = "강남 팝업스토어")
        private final String popupName;
    }

    @Getter
    @Builder
    @Schema(description = "긴급 처리 필요 카운트")
    public static class UrgentCounts {
        @Schema(description = "30분 이상 대기 중인 주문", example = "3")
        private final Long longWaitingOrders;

        @Schema(description = "결제 실패 후 재시도 대기", example = "2")
        private final Long paymentFailedOrders;

        @Schema(description = "취소 요청된 주문", example = "1")
        private final Long cancellationRequests;

        @Schema(description = "환불 대기 주문", example = "4")
        private final Long refundPendingOrders;
    }
}