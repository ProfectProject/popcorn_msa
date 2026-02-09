package com.popcorn.order.dto.request;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주문 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {

    // userId는 JWT 토큰에서 추출하므로 요청 본문에 포함하지 않음

    /** 주문 타입 - "RESERVATION", "GOODS", "MIXED" */
    @NotBlank(message = "주문 타입은 필수입니다.")
    @Schema(description = "주문 타입", allowableValues = {"RESERVATION", "GOODS", "MIXED"}, example = "RESERVATION")
    private String orderType;

    /** 팝업 ID - 어떤 팝업에서 주문하는지 */
    @NotNull(message = "팝업 ID는 필수입니다.")
    private UUID popupId;

    /** 예약 ID - 예약형 주문의 경우 예약 정보 */
    private UUID reservationId;

    /** 결제 방식 - "CARD", "CASH" 등 */
    @Schema(description = "결제 방식", allowableValues = {"CARD", "TRANSFER", "MOBILE_PHONE", "VIRTUAL_ACCOUNT", "CASH"}, example = "CARD")
    private String paymentMethod;

    /** 주문 항목 목록 - 주문할 상품들의 리스트 */
    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> items;

    /** 주소는 user 모듈에서 userId로 조회 */

    public boolean isReservationType() {
        return "RESERVATION".equals(orderType);
    }

    public boolean isGoodsType() {
        return "GOODS".equals(orderType);
    }

    public boolean isMixedType() {
        return "MIXED".equals(orderType);
    }

    public boolean hasConsistentItemTypes() {
        if (items == null || items.isEmpty()) {
            return false;
        }

        if (isReservationType()) {
            return items.stream().allMatch(item -> "RESERVATION".equals(item.getOrderItemType()));
        }

        if (isGoodsType()) {
            return items.stream().allMatch(item -> "GOODS".equals(item.getOrderItemType()));
        }

        if (isMixedType()) {
            boolean hasReservation = items.stream().anyMatch(item -> "RESERVATION".equals(item.getOrderItemType()));
            boolean hasGoods = items.stream().anyMatch(item -> "GOODS".equals(item.getOrderItemType()));
            return hasReservation && hasGoods;
        }

        return false;
    }

    public boolean isValidReservationRequest() {
        if (!isReservationType()) {
            return false;
        }

        return items.stream()
                .allMatch(item ->
                        "RESERVATION".equals(item.getOrderItemType())
                                && item.getSessionId() != null
                );
    }

    public boolean isValidGoodsRequest() {
        if (!isGoodsType()) {
            return false;
        }

        return items.stream()
                .allMatch(item ->
                        "GOODS".equals(item.getOrderItemType())
                                && item.getGoodsId() != null
                );
    }

    public boolean isValidMixedRequest() {
        if (!isMixedType()) {
            return false;
        }

        if (!hasConsistentItemTypes()) {
            return false;
        }

        return items.stream().allMatch(item -> {
            if ("RESERVATION".equals(item.getOrderItemType())) {
                return item.isValidReservationItem();
            } else if ("GOODS".equals(item.getOrderItemType())) {
                return item.isValidGoodsItem();
            }
            return false;
        });
    }

    public boolean requiresShippingAddress() {
        return items != null && items.stream()
                .anyMatch(item -> "GOODS".equals(item.getOrderItemType()));
    }

    public int getTotalQuantity() {
        if (items == null) {
            return 0;
        }
        return items.stream()
                .mapToInt(OrderItemRequest::getQty)
                .sum();
    }
}
