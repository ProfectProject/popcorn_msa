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

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentRequestsProducer {
        private static final String PAYMENT_REQUESTS_TOPIC = "payment-requests";
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final ObjectMapper objectMapper;

    public void publishPaymentCreateRequested(Order order, String paymentMethod, String paymentKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "PAYMENT_CREATE_REQUESTED");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producer", "order-service");
        payload.put("orderId", order.getId().toString()); 
        payload.put("orderNo", order.getOrderNo());
        payload.put("amount", order.getTotalAmount());
        payload.put("orderName", order.getOrderNo());
        payload.put("successUrl", null);
        payload.put("failUrl", null);
        payload.put("requestedAt", Instant.now().toString());
        payload.put("paymentMethod", paymentMethod);
        payload.put("paymentKey", paymentKey);

        kafkaTemplate.send(PAYMENT_REQUESTS_TOPIC, order.getId().toString(), toJson(payload));
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

        kafkaTemplate.send(PAYMENT_REQUESTS_TOPIC, order.getId().toString(), toJson(payload));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka payload 직렬화 실패", e);
        }
    }
}
