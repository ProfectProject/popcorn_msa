package com.popcorn.order.dto.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 이벤트 상세 응답 DTO
 *
 * Event Sourcing에서 발생한 이벤트의 상세 정보를 담는 클래스입니다.
 * 각 이벤트는 시스템에서 일어난 특정 사건을 기록하며,
 * 이를 통해 주문의 전체 생명주기를 추적할 수 있습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEventResponse {

    /** 이벤트 고유 ID */
    @Schema(description = "이벤트 ID", example = "event-12345678-1234-1234-1234-123456789abc")
    private String eventId;

    /** 관련된 주문 ID */
    @Schema(description = "주문 ID", example = "12345678-1234-1234-1234-123456789abc")
    private UUID orderId;

    /** 이벤트 타입 - 어떤 종류의 사건인지 구분 */
    @Schema(
        description = "이벤트 타입",
        allowableValues = {
            "ORDER_CREATED", "ORDER_UPDATED", "ORDER_CANCELLED",
            "PAYMENT_STARTED", "PAYMENT_COMPLETED", "PAYMENT_FAILED",
            "PROCESSING_STARTED", "PROCESSING_COMPLETED",
            "SHIPPED", "DELIVERED", "COMPLETED"
        },
        example = "ORDER_CREATED"
    )
    private String eventType;

    /** 이벤트 발생 시간 */
    @Schema(description = "이벤트 발생 시간", example = "2024-03-15T10:30:00")
    private LocalDateTime timestamp;

    /** 이벤트 상세 데이터 - JSON 형태로 저장된 추가 정보 */
    @Schema(description = "이벤트 상세 데이터 (JSON)")
    private Map<String, Object> eventData;

    /** 이벤트 발생시킨 사용자 ID (선택적) */
    @Schema(description = "이벤트 발생 사용자 ID", example = "1")
    private Long userId;

    /** 이벤트 발생시킨 시스템 또는 서비스명 */
    @Schema(description = "이벤트 발생 시스템", example = "order-service")
    private String sourceSystem;

    /** 이벤트 처리 상태 */
    @Schema(
        description = "이벤트 처리 상태",
        allowableValues = {"PENDING", "PROCESSED", "FAILED", "RETRYING"},
        example = "PROCESSED"
    )
    private String status;

    /** 이벤트 버전 - Event Schema 변경 추적용 */
    @Schema(description = "이벤트 스키마 버전", example = "1.0")
    private String version;

    /** 상관관계 ID - 연관된 이벤트들을 묶어서 추적 */
    @Schema(description = "상관관계 ID (연관 이벤트 추적용)")
    private String correlationId;

    /** 이벤트 처리 시간 (밀리초) */
    @Schema(description = "이벤트 처리 시간 (ms)", example = "150")
    private Long processingTimeMs;

    /** 오류 메시지 (실패한 경우) */
    @Schema(description = "오류 메시지 (실패 시)", example = "결제 서비스 연결 실패")
    private String errorMessage;

    // ========================= 편의 메서드 =========================

    /**
     * 주문 관련 이벤트인지 확인
     * @return 주문 이벤트면 true
     */
    public boolean isOrderEvent() {
        return eventType.startsWith("ORDER_");
    }

    /**
     * 결제 관련 이벤트인지 확인
     * @return 결제 이벤트면 true
     */
    public boolean isPaymentEvent() {
        return eventType.startsWith("PAYMENT_");
    }

    /**
     * 이벤트가 성공적으로 처리되었는지 확인
     * @return 처리 완료되었으면 true
     */
    public boolean isProcessed() {
        return "PROCESSED".equals(status);
    }

    /**
     * 이벤트 처리가 실패했는지 확인
     * @return 처리 실패했으면 true
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * 이벤트 설명을 한글로 반환
     * @return 사용자에게 표시할 이벤트 설명
     */
    public String getEventDescription() {
        return switch (eventType) {
            case "ORDER_CREATED" -> "주문이 생성되었습니다";
            case "ORDER_UPDATED" -> "주문 정보가 수정되었습니다";
            case "ORDER_CANCELLED" -> "주문이 취소되었습니다";
            case "PAYMENT_STARTED" -> "결제가 시작되었습니다";
            case "PAYMENT_COMPLETED" -> "결제가 완료되었습니다";
            case "PAYMENT_FAILED" -> "결제가 실패했습니다";
            case "PROCESSING_STARTED" -> "주문 처리가 시작되었습니다";
            case "PROCESSING_COMPLETED" -> "주문 처리가 완료되었습니다";
            case "SHIPPED" -> "상품이 배송 시작되었습니다";
            case "DELIVERED" -> "상품이 배송 완료되었습니다";
            case "COMPLETED" -> "주문이 완료되었습니다";
            default -> "알 수 없는 이벤트";
        };
    }

    /**
     * 이벤트 데이터에서 특정 값을 추출
     * @param key 추출할 데이터의 키
     * @return 해당 키의 값 (없으면 null)
     */
    public Object getEventDataValue(String key) {
        return eventData != null ? eventData.get(key) : null;
    }

    /**
     * 이벤트가 오래된 것인지 확인 (24시간 이상)
     * @return 24시간 이상 된 이벤트면 true
     */
    public boolean isOldEvent() {
        return timestamp != null &&
               timestamp.isBefore(LocalDateTime.now().minusHours(24));
    }

}
