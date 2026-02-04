package com.popcorn.payment.event.integration;

import com.popcorn.common.event.BaseEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 외부 시스템(다른 마이크로서비스/스트림)으로 발행되는 결제 성공 통합 이벤트.
 *
 * 도메인 쪽 Kotlin PaymentSuccessEvent와 1:1로 매핑되는 형태를 유지하되,
 * 공통 BaseEvent 계층을 상속해서 메타데이터/트래킹을 통일합니다.
 *
 * 업데이트: Kotlin BasePaymentEvent 대신 common-lib BaseEvent를 직접 상속
 */
public class PaymentSuccessIntegrationEvent extends BaseEvent {

    private final UUID orderId;
    private final String orderNo;
    private final String orderType;      // PURCHASE, RESERVATION 등
    private final int totalAmount;
    private final Long userId;
    private final List<OrderItemInfo> orderItems;
    private final LocalDateTime paidAt;
    private final String paymentMethod;
    private final String paymentKey;

    public PaymentSuccessIntegrationEvent(
            UUID paymentId,
            UUID orderId,
            String orderNo,
            String orderType,
            int totalAmount,
            Long userId,
            List<OrderItemInfo> orderItems,
            LocalDateTime paidAt,
            String paymentMethod,
            String paymentKey
    ) {
        super(paymentId, "Payment", "payment-success", userId);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.orderType = orderType;
        this.totalAmount = totalAmount;
        this.userId = userId;
        this.orderItems = orderItems;
        this.paidAt = paidAt;
        this.paymentMethod = paymentMethod;
        this.paymentKey = paymentKey;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getOrderType() {
        return orderType;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public Long getUserId() {
        return userId;
    }

    public List<OrderItemInfo> getOrderItems() {
        return orderItems;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    /**
     * 결제 ID 접근자 (aggregateId의 별명)
     */
    public UUID getPaymentId() {
        return getAggregateId();
    }

    @Override
    public Map<String, Object> getEventPayload() {
        return Map.of(
                "paymentId", getPaymentId(),
                "orderId", orderId,
                "orderNo", orderNo,
                "orderType", orderType,
                "totalAmount", totalAmount,
                "userId", userId != null ? userId : "null",
                "paidAt", paidAt != null ? paidAt.toString() : "null",
                "paymentMethod", paymentMethod != null ? paymentMethod : "null",
                "paymentKey", paymentKey != null ? paymentKey : "null",
                "itemCount", orderItems != null ? orderItems.size() : 0
        );
    }

    /**
     * 주문 항목 정보 (외부 이벤트용 최소 정보)
     */
    public record OrderItemInfo(
            UUID id,
            Long productId,
            String productName,
            int quantity,
            int price,
            String itemType
    ) {
    }
}
