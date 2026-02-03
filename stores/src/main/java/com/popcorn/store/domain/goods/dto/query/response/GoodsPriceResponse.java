package com.popcorn.store.domain.goods.dto.query.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 굿즈 가격 조회 응답 DTO
 * Order 서비스의 굿즈 가격 조회 요청에 대한 응답
 */
@Getter
@Builder
@Schema(description = "굿즈 가격 조회 응답")
public class GoodsPriceResponse {

    @Schema(description = "굿즈 ID", example = "00000000-0000-0000-0000-000000000301")
    private UUID goodsId;

    @Schema(description = "상품명", example = "Seed Popup 굿즈")
    private String productName;

    @Schema(description = "가격", example = "30000")
    private Integer price;

    @Schema(description = "원래 가격 (할인 전)", example = "30000")
    private Integer originalPrice;

    @Schema(description = "할인율 (기본 0%)", example = "0")
    private Integer discountRate;

    @Schema(description = "재고 수량", example = "25")
    private Integer stockQuantity;

    @Schema(description = "상품 상태", example = "AVAILABLE")
    private String status;

    @Schema(description = "통화", example = "KRW")
    private String currency;

    /**
     * 상태 계산 메서드
     */
    public String calculateStatus() {
        if (stockQuantity == null || stockQuantity <= 0) {
            return "OUT_OF_STOCK";
        }
        return "AVAILABLE";
    }
}