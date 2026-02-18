package com.example.orderquery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 📋 주문 상태별 요약 응답 DTO - orderQuery 서비스용
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

    @Schema(description = "처리 시간 통계")
    private final ProcessingTimeStats processingTimeStats;

    @Schema(description = "상태 전이 통계")
    private final StatusTransitionStats statusTransitionStats;

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

        @Schema(description = "환불 처리 중", example = "5")
        private final Long refunding;

        @Schema(description = "배송 중", example = "32")
        private final Long shipping;

        @Schema(description = "픽업 대기", example = "18")
        private final Long pickupReady;
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

        @Schema(description = "환불 처리 중 비율", example = "0.9")
        private final Double refunding;

        @Schema(description = "배송 중 비율", example = "6.2")
        private final Double shipping;

        @Schema(description = "픽업 대기 비율", example = "3.5")
        private final Double pickupReady;
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

        @Schema(description = "환불 처리 중 주문")
        private final List<RecentOrderItem> refunding;

        @Schema(description = "픽업 대기 주문")
        private final List<RecentOrderItem> pickupReady;
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

        @Schema(description = "사용자 이름", example = "김철수")
        private final String userName;

        @Schema(description = "주문 금액", example = "45000")
        private final BigDecimal totalAmount;

        @Schema(description = "주문 일시")
        private final LocalDateTime createdAt;

        @Schema(description = "주문 상태", example = "PENDING")
        private final String status;

        @Schema(description = "팝업명", example = "강남 팝업스토어")
        private final String popupName;

        @Schema(description = "대기 시간 (분)", example = "25")
        private final Long waitingMinutes;

        @Schema(description = "우선순위", example = "HIGH")
        private final String priority;

        @Schema(description = "특이사항", example = "VIP 고객")
        private final String note;
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

        @Schema(description = "고객 문의 접수된 주문", example = "6")
        private final Long customerInquiryOrders;

        @Schema(description = "시스템 오류 발생 주문", example = "1")
        private final Long systemErrorOrders;

        @Schema(description = "재고 부족 주문", example = "2")
        private final Long outOfStockOrders;
    }

    @Getter
    @Builder
    @Schema(description = "처리 시간 통계")
    public static class ProcessingTimeStats {
        @Schema(description = "평균 처리 시간 (분)", example = "12.5")
        private final Double averageProcessingTime;

        @Schema(description = "최대 처리 시간 (분)", example = "45")
        private final Double maxProcessingTime;

        @Schema(description = "최소 처리 시간 (분)", example = "2")
        private final Double minProcessingTime;

        @Schema(description = "SLA 달성률 (%)", example = "94.2")
        private final Double slaAchievementRate;

        @Schema(description = "목표 처리 시간 (분)", example = "15")
        private final Double targetProcessingTime;

        @Schema(description = "처리 시간 초과 주문 수", example = "8")
        private final Long overtimeOrders;
    }

    @Getter
    @Builder
    @Schema(description = "상태 전이 통계")
    public static class StatusTransitionStats {
        @Schema(description = "오늘 상태 변경된 주문 수", example = "156")
        private final Long todayTransitions;

        @Schema(description = "시간당 평균 상태 변경 수", example = "6.5")
        private final Double avgTransitionsPerHour;

        @Schema(description = "가장 빈번한 상태 전이", example = "PENDING -> CONFIRMED")
        private final String mostFrequentTransition;

        @Schema(description = "가장 느린 상태 전이", example = "PAID -> COMPLETED")
        private final String slowestTransition;

        @Schema(description = "상태 롤백 발생 건수", example = "3")
        private final Long rollbackCount;

        @Schema(description = "자동 처리된 상태 변경 비율 (%)", example = "78.5")
        private final Double automaticTransitionRatio;
    }

    @Getter
    @Builder
    @Schema(description = "실시간 알림")
    public static class RealtimeAlert {
        @Schema(description = "알림 ID")
        private final String alertId;

        @Schema(description = "알림 유형", example = "URGENT_ORDER")
        private final String type;

        @Schema(description = "알림 제목", example = "긴급 처리 필요")
        private final String title;

        @Schema(description = "알림 내용", example = "30분 이상 대기 중인 주문이 3건 있습니다")
        private final String message;

        @Schema(description = "심각도", example = "HIGH")
        private final String severity;

        @Schema(description = "생성 시간")
        private final LocalDateTime createdAt;

        @Schema(description = "관련 주문 수", example = "3")
        private final Long affectedOrderCount;
    }
}