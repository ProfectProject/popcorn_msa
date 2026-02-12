package com.popcorn.store.domain.validation.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배치 가격 검증 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "배치 가격 검증 요청")
public class BatchPriceValidationRequest {

    @Schema(description = "주문 ID", example = "00000000-0000-0000-0000-000000000101")
    private UUID orderId;

    @Schema(description = "라인 아이템 목록")
    private List<LineItemPriceRequest> lineItems;

    @Schema(description = "총 예상 금액", example = "75000")
    private Integer totalExpectedAmount;
}