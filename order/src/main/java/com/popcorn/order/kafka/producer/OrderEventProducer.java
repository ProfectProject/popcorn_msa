package com.popcorn.order.kafka.producer;

import com.popcorn.common.event.BaseEvent;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.kafka.event.MetaEvent;
import com.popcorn.order.kafka.event.order_events.OrderCANCELLEDEvent;
import com.popcorn.order.kafka.event.order_events.OrderCOMPLETEDEvent;
import com.popcorn.order.kafka.event.order_events.OrderCreateEvent;
import com.popcorn.order.kafka.event.order_events.OrderLine;
import com.popcorn.order.kafka.event.order_events.OrderPAIDEvent;
import com.popcorn.order.kafka.event.order_events.OrderStatusUpdatedEvent;
import com.popcorn.order.service.lookup.OrderPopupLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sound.sampled.Line;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderPopupLookupService orderPopupLookupService;
    private final ObjectMapper objectMapper;

    /*
    * ORDER_CREATED
    */
    public void publishOrderCreated(Order order, List<OrderItem> orderItems,
                                boolean hasGoods, boolean hasReservation) {
        List<OrderLine> lines = orderItems.stream()
            .map(this::toLine)
            .toList();

        OrderCreateEvent event = OrderCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType("ORDER_CREATED")
                .eventVersion("1.0")
                .producer("order-service")

                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getCustomerId())
                .orderType(order.getOrderType())
                .popupId(order.getPopupId())
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)

                .lines(lines)

                .totalAmount(order.getTotalAmount())
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), toJson(event));
    }

    /*
    * ORDER_PAID
    */
    public void publishOrderPaid(Order order,
                                boolean hasGoods, boolean hasReservation) {
        publishOrderPaid(order, hasGoods, hasReservation, null);
    }

    public void publishOrderPaid(Order order,
                                boolean hasGoods, boolean hasReservation, String paymentId) {

        OrderPAIDEvent event = OrderPAIDEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType("ORDER_PAID")
                .eventVersion("1.0")
                .producer("order-service")
                .orderId(order.getId())
                .popupId(order.getPopupId())
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)
                .totalAmount(order.getTotalAmount())
                .paymentId(paymentId)
                .paidAt(order.getPaidAt())
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), toJson(event));
    }


    /*
    * ORDER_STATUS_UPDATED
    */
    public void publishOrderStatusUpdate(Order order,
                                OrderStatus fromStatus, OrderStatus toStatus,
                                boolean hasGoods, boolean hasReservation) {

        OrderStatusUpdatedEvent event = OrderStatusUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType("ORDER_STATUS_UPDATED")
                .eventVersion("1.0")
                .producer("order-service")
                .orderId(order.getId())
                .popupId(order.getPopupId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)
                //.updatedAt(order.getUpdatedAt())
                .updatedAt(order.getUpdatedAt().atZone(ZoneOffset.UTC).toString())
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), toJson(event));
    }

    /*
    * ORDER_COMPLETED
    */
    public void publishOrderCompleted(Order order,
                                boolean hasGoods, boolean hasReservation) {
        UUID storeId = resolveStoreId(order.getPopupId());

        OrderCOMPLETEDEvent event = OrderCOMPLETEDEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType("ORDER_COMPLETED")
                .eventVersion("1.0")
                .producer("order-service")
                .orderId(order.getId())
                .popupId(order.getPopupId())
                .storeId(storeId)
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)
                .totalAmount(order.getTotalAmount())
                .completedAt(order.getConfirmedAt())
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), toJson(event));
    }


    /* 
    * ORDER_CANCELLED
    */
    public void publishOrderCancelled(Order order,List<OrderItem> orderItems,
                                boolean hasGoods, boolean hasReservation,LocalDateTime cancleAt) {
        UUID storeId = resolveStoreId(order.getPopupId());
        List<OrderLine> lines = orderItems.stream()
            .map(this::toLine)
            .toList();

        OrderCANCELLEDEvent event = OrderCANCELLEDEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType("ORDER_CANCELLED")
                .eventVersion("1.0")
                .producer("order-service")
                .orderId(order.getId())
                .popupId(order.getPopupId())
                .storeId(storeId)
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)
                .lines(lines)
                .cancelledAt(cancleAt)
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), toJson(event));
    }

    private OrderLine toLine(OrderItem item) {
    if (item.getOrderItemType() == ItemType.GOODS) {
        return OrderLine.builder()
                .itemId(item.getId())
                .itemType("GOODS")
                .goodsId(item.getGoodsId())
                .qty(item.getQty())
                .unitPrice(item.getUnitPrice())
                .lineAmount(item.getLineAmount())
                .build();
    }

    // 예약/스케줄
    return OrderLine.builder()
            .itemId(item.getId())
            .itemType("SCHEDULE")
            .scheduleId(item.getSessionOptionId())
            .qty(item.getQty())
            .unitPrice(item.getUnitPrice()) // 있으면
            .lineAmount(item.getLineAmount())
            .build();
}

    private UUID resolveStoreId(UUID popupId) {
        if (popupId == null) {
            return null;
        }
        try {
            return orderPopupLookupService.getPopupInfo(popupId)
                    .map(info -> info.getStoreId())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka payload 직렬화 실패", e);
        }
    }
}
