package com.popcorn.order.kafka.event;

import com.popcorn.common.event.BaseEvent;
import com.popcorn.order.entity.Order;
import com.popcorn.order.kafka.event.OrderCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sound.sampled.Line;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order,
                                boolean hasGoods, boolean hasReservation) {

        OrderCreateEvent event = OrderCreateEvent.builder()
                .meta(MetaEvent.builder()
                        .eventId(UUID.randomUUID())
                        .correlationId(UUID.randomUUID())
                        .timestamp(LocalDateTime.now())
                        .eventType("ORDER_CREATED")
                        .eventVersion("1.0")
                        .producer("order-service")
                        .aggregateType("Order")
                        .metadata(Map.of())
                        .build())
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getCustomerId())
                .orderType(order.getOrderType())
                .popupId(order.getPopupId())
                .hasReservation(hasReservation)
                .hasGoods(hasGoods)
                .orderItems(order.getOrderItems())
                .totalAmount(order.getTotalAmount())
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), event);
    }
}
