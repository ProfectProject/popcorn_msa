package com.popcorn.store.domain.validation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 라인 아이템 가격 검증 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "라인 아이템 가격 검증 요청")
public class LineItemPriceRequest {

    @Schema(description = "아이템 ID (세션 ID 또는 굿즈 ID)", example = "00000000-0000-0000-0000-000000000201")
    private UUID itemId;

    @Schema(description = "아이템 타입 (SESSION 또는 GOODS)", example = "SESSION", allowableValues = {"SESSION", "GOODS"})
    private String itemType;

    @Schema(description = "예상 가격", example = "25000")
    private Integer expectedPrice;

    @Schema(description = "수량", example = "2")
    private Integer quantity;
}