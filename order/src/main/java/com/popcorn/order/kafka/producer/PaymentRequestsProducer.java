package com.popcorn.order.kafka.producer;

import com.popcorn.order.entity.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.popcorn.order.entity.OutboxEvent;
import com.popcorn.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestsProducer {
        private static final String PAYMENT_REQUESTS_TOPIC = "payment-requests";
        private static final String AGGREGATE_TYPE_ORDER = "ORDER";
        private final OutboxEventRepository outboxEventRepository;
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final ObjectMapper objectMapper;

    public void publishPaymentCreateRequested(Order order, String paymentMethod, String paymentKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "PAYMENT_CREATE_REQUESTED");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producer", "order-service");
        // + paymentId :: 
        payload.put("orderId", order.getId().toString()); 
        payload.put("orderNo", order.getOrderNo());
        payload.put("amount", order.getTotalAmount());
        payload.put("orderName", order.getOrderNo());
        payload.put("successUrl", null);
        payload.put("failUrl", null);
        payload.put("requestedAt", Instant.now().toString());
        payload.put("paymentMethod", paymentMethod);
        payload.put("paymentKey", paymentKey);

        //kafkaTemplate.send(PAYMENT_REQUESTS_TOPIC, order.getId().toString(), payload);
        saveToOutbox(order, "PAYMENT_CREATE_REQUESTED", payload);
    }
    
    public void publishPaymentCancelRequested(Order order, String paymentId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "PAYMENT_CANCEL_REQUESTED");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producer", "order-service");
        payload.put("orderId", order.getId().toString());
        payload.put("orderNo", order.getOrderNo());
        payload.put("paymentId", paymentId);
        payload.put("amount", order.getTotalAmount());
        payload.put("cancelReason", reason);
        payload.put("requestedAt", Instant.now().toString()); // occurredAt과 같은가?

        //kafkaTemplate.send(PAYMENT_REQUESTS_TOPIC, order.getId().toString(), payload);
        saveToOutbox(order, "PAYMENT_CANCEL_REQUESTED", payload);
    }

    private void saveToOutbox(Order order, String eventType, Map<String, Object> payload) {
        OutboxEvent outboxEvent = OutboxEvent.of(
                PAYMENT_REQUESTS_TOPIC,
                AGGREGATE_TYPE_ORDER,
                order.getId().toString(),
                eventType,
                payload,
                Map.of("producer", "order-service")
        );
        outboxEventRepository.save(outboxEvent);
        log.info(
                "[OUTBOX_SAVE] topic={}, aggregateType={}, aggregateId={}, eventType={}, outboxEventId={}",
                PAYMENT_REQUESTS_TOPIC,
                AGGREGATE_TYPE_ORDER,
                order.getId(),
                eventType,
                outboxEvent.getEventId()
        );
    }
}
