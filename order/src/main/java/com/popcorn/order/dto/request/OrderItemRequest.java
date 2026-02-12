package com.popcorn.order.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {

    /** 주문 항목 타입 - "RESERVATION" 또는 "GOODS" */
    @NotBlank(message = "주문 항목 타입은 필수입니다.")
    @Schema(description = "주문 항목 타입", allowableValues = {"RESERVATION", "GOODS"}, example = "RESERVATION")
    private String orderItemType;

    /** 수량 - 주문하는 개수 */
    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private Integer qty;

    /** 단가 - 개당 가격 (원) */
    private Integer unitPrice;

    /** 세션 ID - 예약형 상품의 경우 시간 슬롯 */
    private UUID sessionId;

    /** 옵션 ID - 세션의 추가 옵션 */
    private UUID optionId;

    /** 굿즈 변형 ID - 구매형 상품의 경우 (색상, 사이즈 등) */
    @JsonAlias("goodsVariantId")
    private UUID goodsId;

    public boolean isReservationType() {
        return "RESERVATION".equals(orderItemType);
    }

    public boolean isGoodsType() {
        return "GOODS".equals(orderItemType);
    }

    public boolean isValidReservationItem() {
        return isReservationType()
                && sessionId != null
                && qty != null
                && qty > 0;
    }

    public boolean isValidGoodsItem() {
        return isGoodsType()
                && goodsId != null
                && qty != null
                && qty > 0;
    }

    public boolean hasRequiredFields() {
        if (isReservationType()) {
            return isValidReservationItem();
        }
        if (isGoodsType()) {
            return isValidGoodsItem();
        }
        return false;
    }

    public boolean hasUnnecessaryFields() {
        if (isReservationType()) {
            return goodsId != null;
        }
        if (isGoodsType()) {
            return sessionId != null || optionId != null;
        }
        return false;
    }

    public String getSessionOptionKey() {
        if (isReservationType() && sessionId != null) {
            return sessionId.toString();
        }
        return null;
    }

    public String getStockIdentifier() {
        if (isReservationType()) {
            return getSessionOptionKey();
        }
        if (isGoodsType()) {
            return "VARIANT_" + goodsId;
        }
        return null;
    }
}
