package com.popcorn.store.domain.popup.dto.query.response;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 세션 가격 조회 응답 DTO
 * Order 서비스의 가격 조회 요청에 대한 응답
 */
@Getter
@Builder
@Schema(description = "세션 가격 조회 응답")
public class SessionPriceResponse {

    @Schema(description = "세션 ID", example = "00000000-0000-0000-0000-000000000201")
    private UUID sessionId;

    @Schema(description = "팝업 ID", example = "00000000-0000-0000-0000-000000000101")
    private UUID popupId;

    @Schema(description = "세션명 (팝업 제목 기반)", example = "Seed Popup 1 - 세션")
    private String sessionName;

    @Schema(description = "가격", example = "50000")
    private Integer price;

    @Schema(description = "원래 가격 (할인 전)", example = "50000")
    private Integer originalPrice;

    @Schema(description = "할인율 (기본 0%)", example = "0")
    private Integer discountRate;

    @Schema(description = "사용 가능한 좌석 수", example = "15")
    private Integer availableSeats;

    @Schema(description = "총 좌석 수", example = "20")
    private Integer totalSeats;

    @Schema(description = "세션 상태", example = "AVAILABLE")
    private String status;

    @Schema(description = "세션 시작 시간", example = "2025-01-15T14:00:00")
    private LocalDateTime sessionStartTime;

    @Schema(description = "세션 종료 시간", example = "2025-01-15T16:00:00")
    private LocalDateTime sessionEndTime;

    @Schema(description = "통화", example = "KRW")
    private String currency;

    /**
     * 상태 계산 메서드
     */
    public String calculateStatus() {
        if (availableSeats == null || availableSeats <= 0) {
            return "SOLD_OUT";
        }
        LocalDateTime now = LocalDateTime.now();
        if (sessionStartTime != null && now.isAfter(sessionStartTime)) {
            return "EXPIRED";
        }
        return "AVAILABLE";
    }
}