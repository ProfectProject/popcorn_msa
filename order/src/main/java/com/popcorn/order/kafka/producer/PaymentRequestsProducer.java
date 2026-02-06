/*package com.popcorn.order.kafka.producer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.popcorn.order.entity.Order;
import com.popcorn.order.kafka.event.MetaEvent;
import com.popcorn.order.kafka.event.order_events.OrderCreateEvent;
import com.popcorn.order.kafka.event.payment_requests.PaymentCANCELRequestEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentRequestsProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /*
    * PAYMENT_CANCEL_REQUESTED
    */
    /*public void publishPaymentCancelRequested(Order order,UUID paymentId,
                                boolean hasGoods, boolean hasReservation) {

        PaymentCANCELRequestEvent event = PaymentCANCELRequestEvent.builder()
                .meta(MetaEvent.builder()
                        .eventId(UUID.randomUUID())
                        .correlationId(UUID.randomUUID())
                        .timestamp(LocalDateTime.now())
                        .eventType("PAYMENT_CANCEL_REQUESTED")
                        .eventVersion("1.0")
                        .producer("order-service")
                        .aggregateType("Order")
                        .metadata(Map.of())
                        .build())
                .paymentId(paymentId)
                .orderId(order.getId())
                .requestedAt(null)
                .build();

        // key 전략: orderId (같은 주문 이벤트는 같은 파티션으로)
        kafkaTemplate.send("order-events", order.getId().toString(), event);
    }

}*/
