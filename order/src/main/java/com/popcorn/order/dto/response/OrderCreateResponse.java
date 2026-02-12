package com.popcorn.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 주문 생성 응답 DTO
 */
@Getter
@Builder
public class OrderCreateResponse {

    /** 주문 ID - 시스템에서 생성된 고유 식별자 */
    private final UUID orderId;

    /** 주문 번호 - 사용자에게 표시되는 주문 번호 */
    private final String orderNo;

    /** 주문 타입 - "RESERVATION" 또는 "PURCHASE" */
    private final String orderType;

    /** 주문 상태 - "REQUESTED", "ACCEPTED" 등 */
    private final String status;

    /** 팝업 ID - 관련된 팝업 이벤트 */
    private final UUID popupId;

    /** 총 주문 금액 (원) */
    private final Integer totalAmount;

    /** 취소 가능 시한 - 이 시간까지만 주문 취소 가능 */
    private final LocalDateTime cancelableUntil;

    /** 주문 생성 시간 */
    private final LocalDateTime createdAt;

    /** 주문 항목 목록 */
    private final List<OrderItemResponse> items;

    /** 결제 정보 - 주문 생성 후 결제 프로세스 시작 시 포함 */
    private final PaymentInfo paymentInfo;

    @JsonCreator
    public OrderCreateResponse(
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("orderNo") String orderNo,
            @JsonProperty("orderType") String orderType,
            @JsonProperty("status") String status,
            @JsonProperty("popupId") UUID popupId,
            @JsonProperty("totalAmount") Integer totalAmount,
            @JsonProperty("cancelableUntil") LocalDateTime cancelableUntil,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("items") List<OrderItemResponse> items,
            @JsonProperty("paymentInfo") PaymentInfo paymentInfo
    ) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.orderType = orderType;
        this.status = status;
        this.popupId = popupId;
        this.totalAmount = totalAmount;
        this.cancelableUntil = cancelableUntil;
        this.createdAt = createdAt;
        this.items = items;
        this.paymentInfo = paymentInfo;
    }

    @Getter
    @Builder
    public static class OrderItemResponse {
        private final UUID itemId;
        private final String orderItemType;
        private final Integer qty;
        private final Integer unitPrice;
        private final Integer lineAmount;

        @JsonCreator
        public OrderItemResponse(
                @JsonProperty("itemId") UUID itemId,
                @JsonProperty("orderItemType") String orderItemType,
                @JsonProperty("qty") Integer qty,
                @JsonProperty("unitPrice") Integer unitPrice,
                @JsonProperty("lineAmount") Integer lineAmount
        ) {
            this.itemId = itemId;
            this.orderItemType = orderItemType;
            this.qty = qty;
            this.unitPrice = unitPrice;
            this.lineAmount = lineAmount;
        }
    }

    @Getter
    @Builder
    public static class PaymentInfo {
        private final UUID paymentId;
        private final String paymentStatus;
        private final String paymentMethod;
        private final String paymentUrl;
        private final LocalDateTime expiresAt;
        private final String message;

        @JsonCreator
        public PaymentInfo(
                @JsonProperty("paymentId") UUID paymentId,
                @JsonProperty("paymentStatus") String paymentStatus,
                @JsonProperty("paymentMethod") String paymentMethod,
                @JsonProperty("paymentUrl") String paymentUrl,
                @JsonProperty("expiresAt") LocalDateTime expiresAt,
                @JsonProperty("message") String message
        ) {
            this.paymentId = paymentId;
            this.paymentStatus = paymentStatus;
            this.paymentMethod = paymentMethod;
            this.paymentUrl = paymentUrl;
            this.expiresAt = expiresAt;
            this.message = message;
        }
    }

    public static OrderCreateResponse fromOrder(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderCreateResponse::fromOrderItem)
                .toList();

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .popupId(order.getPopupId())
                .totalAmount(order.getTotalAmount())
                .cancelableUntil(order.getCancelableUntil())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .paymentInfo(null)
                .build();
    }

    public static OrderCreateResponse fromOrderWithPayment(Order order,
                                                           List<OrderItem> orderItems,
                                                           UUID paymentId,
                                                           String paymentStatus,
                                                           String paymentMethod,
                                                           String paymentUrl,
                                                           LocalDateTime expiresAt,
                                                           String message) {
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(OrderCreateResponse::fromOrderItem)
                .toList();

        PaymentInfo paymentInfo = PaymentInfo.builder()
                .paymentId(paymentId)
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .paymentUrl(paymentUrl)
                .expiresAt(expiresAt)
                .message(message)
                .build();

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .popupId(order.getPopupId())
                .totalAmount(order.getTotalAmount())
                .cancelableUntil(order.getCancelableUntil())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .paymentInfo(paymentInfo)
                .build();
    }

    private static OrderItemResponse fromOrderItem(OrderItem item) {
        return OrderItemResponse.builder()
                .itemId(item.getId())
                .orderItemType(item.getOrderItemType().name())
                .qty(item.getQty())
                .unitPrice(item.getUnitPrice())
                .lineAmount(item.getLineAmount())
                .build();
    }
}
