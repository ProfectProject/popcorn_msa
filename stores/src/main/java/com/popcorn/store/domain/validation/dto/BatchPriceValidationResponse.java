package com.popcorn.store.domain.validation.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배치 가격 검증 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "배치 가격 검증 응답")
public class BatchPriceValidationResponse {

    @Schema(description = "주문 ID", example = "00000000-0000-0000-0000-000000000101")
    private UUID orderId;

    @Schema(description = "전체 검증 성공 여부", example = "true")
    @com.fasterxml.jackson.annotation.JsonProperty("valid")
    private boolean isValid;

    @Schema(description = "총 실제 금액", example = "75000")
    private Integer totalActualAmount;

    @Schema(description = "총 예상 금액", example = "75000")
    private Integer totalExpectedAmount;

    @Schema(description = "개별 아이템 검증 결과")
    private List<LineItemValidationResult> itemResults;

    @Schema(description = "검증 실패 이유")
    private String failureReason;

    /**
     * 개별 아이템 검증 결과
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "개별 아이템 검증 결과")
    public static class LineItemValidationResult {

        @Schema(description = "아이템 ID", example = "00000000-0000-0000-0000-000000000201")
        private UUID itemId;

        @Schema(description = "아이템 타입", example = "SESSION")
        private String itemType;

        @Schema(description = "검증 성공 여부", example = "true")
        @com.fasterxml.jackson.annotation.JsonProperty("valid")
        private boolean isValid;

        @Schema(description = "실제 가격", example = "25000")
        private Integer actualPrice;

        @Schema(description = "예상 가격", example = "25000")
        private Integer expectedPrice;

        @Schema(description = "수량", example = "2")
        private Integer quantity;

        @Schema(description = "총 라인 금액 (실제)", example = "50000")
        private Integer actualLineAmount;

        @Schema(description = "총 라인 금액 (예상)", example = "50000")
        private Integer expectedLineAmount;

        @Schema(description = "검증 실패 이유")
        private String failureReason;
    }
}