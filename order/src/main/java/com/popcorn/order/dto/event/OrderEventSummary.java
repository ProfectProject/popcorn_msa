package com.popcorn.order.dto.event;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 이벤트 요약 DTO
 *
 * 시스템 전체의 이벤트 목록을 조회할 때 사용하는 간략한 정보입니다.
 * 상세한 eventData 없이 핵심 정보만 담아서 성능을 최적화했습니다.
 * 페이징된 목록에서 빠르게 스캔하고 필요시 상세 정보를 별도 조회하는 패턴입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEventSummary {

    /** 이벤트 고유 ID */
    @Schema(description = "이벤트 ID", example = "event-12345678-1234-1234-1234-123456789abc")
    private String eventId;

    /** 관련된 주문 ID */
    @Schema(description = "주문 ID", example = "12345678-1234-1234-1234-123456789abc")
    private UUID orderId;

    /** 주문 번호 (사용자에게 표시되는 번호) */
    @Schema(description = "주문 번호", example = "ORD-20240315-001")
    private String orderNo;

    /** 이벤트 타입 */
    @Schema(
        description = "이벤트 타입",
        allowableValues = {
            "ORDER_CREATED", "ORDER_UPDATED", "ORDER_CANCELLED",
            "PAYMENT_STARTED", "PAYMENT_COMPLETED", "PAYMENT_FAILED",
            "PROCESSING_STARTED", "PROCESSING_COMPLETED",
            "SHIPPED", "DELIVERED", "COMPLETED"
        },
        example = "PAYMENT_COMPLETED"
    )
    private String eventType;

    /** 이벤트 발생 시간 */
    @Schema(description = "이벤트 발생 시간", example = "2024-03-15T10:30:00")
    private LocalDateTime timestamp;

    /** 이벤트 발생시킨 사용자 ID */
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    /** 사용자 이름 (조인해서 가져온 정보) */
    @Schema(description = "사용자 이름", example = "김팝콘")
    private String userName;

    /** 이벤트 발생 시스템 */
    @Schema(description = "발생 시스템", example = "order-service")
    private String sourceSystem;

    /** 이벤트 처리 상태 */
    @Schema(
        description = "처리 상태",
        allowableValues = {"PENDING", "PROCESSED", "FAILED", "RETRYING"},
        example = "PROCESSED"
    )
    private String status;

    /** 이벤트 처리 시간 (밀리초) */
    @Schema(description = "처리 시간 (ms)", example = "150")
    private Long processingTimeMs;

    /** 요약 메시지 - eventData의 핵심 내용을 간단히 표현 */
    @Schema(description = "이벤트 요약", example = "카드결제 50,000원 승인완료")
    private String summary;

    /** 중요도 레벨 - 모니터링시 우선순위 판단용 */
    @Schema(
        description = "중요도 레벨",
        allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"},
        example = "MEDIUM"
    )
    private String severity;

    // ========================= 편의 메서드 =========================

    /**
     * 이벤트 타입에 따른 한글 설명 반환
     * @return 한글 이벤트 설명
     */
    public String getEventTypeDescription() {
        return switch (eventType) {
            case "ORDER_CREATED" -> "주문생성";
            case "ORDER_UPDATED" -> "주문수정";
            case "ORDER_CANCELLED" -> "주문취소";
            case "PAYMENT_STARTED" -> "결제시작";
            case "PAYMENT_COMPLETED" -> "결제완료";
            case "PAYMENT_FAILED" -> "결제실패";
            case "PROCESSING_STARTED" -> "처리시작";
            case "PROCESSING_COMPLETED" -> "처리완료";
            case "SHIPPED" -> "배송시작";
            case "DELIVERED" -> "배송완료";
            case "COMPLETED" -> "주문완료";
            default -> eventType;
        };
    }

    /**
     * 상태에 따른 색상 코드 반환 (UI 표시용)
     * @return CSS 색상 클래스명
     */
    public String getStatusColorClass() {
        return switch (status) {
            case "PROCESSED" -> "success";
            case "FAILED" -> "danger";
            case "RETRYING" -> "warning";
            case "PENDING" -> "info";
            default -> "secondary";
        };
    }

    /**
     * 처리 속도 평가 반환
     * @return 처리 속도 평가 문자열
     */
    public String getPerformanceRating() {
        if (processingTimeMs == null) return "N/A";

        if (processingTimeMs < 100) return "매우빠름";
        else if (processingTimeMs < 500) return "빠름";
        else if (processingTimeMs < 1000) return "보통";
        else if (processingTimeMs < 3000) return "느림";
        else return "매우느림";
    }

    /**
     * 오늘 발생한 이벤트인지 확인
     * @return 오늘 이벤트면 true
     */
    public boolean isToday() {
        if (timestamp == null) return false;
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        return timestamp.isAfter(today);
    }

    /**
     * 실패한 이벤트인지 확인
     * @return 실패 이벤트면 true
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * 중요한 이벤트인지 확인 (HIGH, CRITICAL 레벨)
     * @return 중요 이벤트면 true
     */
    public boolean isImportant() {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }

    /**
     * 느린 처리 이벤트인지 확인 (1초 이상)
     * @return 느린 처리면 true
     */
    public boolean isSlowProcessing() {
        return processingTimeMs != null && processingTimeMs > 1000;
    }

}
