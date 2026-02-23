package com.popcorn.order.kafka.consumer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.popcorn.order.dto.payment.PaymentUrlResponse;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.service.cache.OrderCacheService;
import com.popcorn.order.service.core.OrderCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final OrderCommandService orderCommandService;
    private final OrderCacheService orderCacheService;

    @KafkaListener(
            topics = "payment-events",
            groupId = "${kafka.consumer.groups.payment:order-payment-cg}"
    )
    public void storeReserveConsumer(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("[KAFKA_CONSUME] topic={}, partition={}, offset={}, key={}", topic, partition, offset, key);
            log.info("PaymentEventConsumer payload={}", payload);

            Map<String, Object> eventData = unwrapPayloadIfNeeded(payload);
            String eventType = normalizeEventType(eventData);
            String orderIdRaw = stringOf(eventData, "orderId", "order_id");

            log.info("추출된 값들 - orderId='{}', eventType='{}'", orderIdRaw, eventType);

            if (orderIdRaw == null || orderIdRaw.isBlank()) {
                log.warn("Kafka 메시지에서 orderId 누락 - eventType={}, payload={}", eventType, eventData);
                acknowledgment.acknowledge();
                return;
            }

            UUID orderId;
            try {
                orderId = UUID.fromString(orderIdRaw);
            } catch (IllegalArgumentException e) {
                log.warn("Kafka 메시지 orderId UUID 파싱 실패 - orderIdRaw={}, payload={}", orderIdRaw, eventData);
                acknowledgment.acknowledge();
                return;
            }

            if (eventType == null || eventType.isBlank()) {
                log.warn("eventType 누락 메시지 무시 - orderId={}, payload={}", orderId, eventData);
                acknowledgment.acknowledge();
                return;
            }

            switch (eventType) {
                case "PAYMENT_CREATED":
                case "PAYMENT_URL_CREATED":
                    if (!canApplyPaymentCreated(orderId)) {
                        log.info("{} 이벤트 무시 - orderId={}, status={}", eventType, orderId,
                                orderCommandService.getOrderStatus(orderId));
                        break;
                    }
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                            "PAYMENT_URL_CREATED".equals(eventType) ? "결제 URL 생성 이벤트 수신" : "결제 생성 이벤트 수신");
                    if ("PAYMENT_URL_CREATED".equals(eventType)) {
                        cachePaymentUrl(orderId, eventData);
                    }
                    break;
                case "PAYMENT_APPROVED":
                    if (!canApplyPaymentApproved(orderId)) {
                        log.info("PAYMENT_APPROVED 이벤트 무시 - orderId={}, status={}", orderId,
                                orderCommandService.getOrderStatus(orderId));
                        break;
                    }
                    String paymentId = stringOf(eventData, "paymentId", "payment_id");
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAID.name(),
                            "결제 승인 이벤트 수신", paymentId);
                    orderCommandService.requestStockDeduction(orderId);
                    orderCommandService.requestScheduleConfirmation(orderId);
                    break;
                case "PAYMENT_FAILED":
                case "PAYMENT_USER_CANCELLED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "결제 실패/취소 이벤트 수신");
                    orderCommandService.cancelStockReservationsForOrder(orderId);
                    orderCommandService.cancelScheduleReservationsCfororder(orderId);
                    break;
                case "PAYMENT_CANCEL_SUCCEEDED":
                    orderCommandService.releaseScheduleReservationsForOrder(orderId);
                    orderCommandService.realeaseGoodsReservationsForOrder(orderId);
                    break;
                case "PAYMENT_CANCEL_FAILED":
                    log.error("Payment cancel failed for orderId={}, payload={}", orderId, eventData);
                    break;
                default:
                    log.info("Unhandled payment eventType: {}", eventType);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA_CONSUME_FAIL] topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);
            throw e;
        }
    }

    private boolean canApplyPaymentCreated(UUID orderId) {
        OrderStatus current = orderCommandService.getOrderStatus(orderId);
        if (current == null) {
            return true;
        }
        return switch (current) {
            case REQUESTED, RESERVED -> true;
            default -> false;
        };
    }

    private boolean canApplyPaymentApproved(UUID orderId) {
        OrderStatus current = orderCommandService.getOrderStatus(orderId);
        if (current == null) {
            return true;
        }
        return switch (current) {
            case REQUESTED, RESERVED, PAYMENT_PENDING -> true;
            default -> false;
        };
    }

    private String normalizeEventType(Map<String, Object> map) {
        String eventType = stringOf(map, "eventType", "event_type");
        if (eventType != null && !eventType.isBlank()) {
            return eventType;
        }

        String actionType = stringOf(map, "actionType");
        if ("CONFIRM".equalsIgnoreCase(actionType)) {
            return "PAYMENT_APPROVED";
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapPayloadIfNeeded(Map<String, Object> parsed) {
        Object nestedPayload = parsed.get("payload");
        if (nestedPayload instanceof Map<?, ?> payloadMap) {
            try {
                return (Map<String, Object>) payloadMap;
            } catch (ClassCastException ignored) {
                return parsed;
            }
        }
        return parsed;
    }

    private void cachePaymentUrl(UUID orderId, Map<String, Object> eventData) {
        String paymentUrl = stringOf(eventData, "paymentUrl", "payment_url");
        if (paymentUrl == null || paymentUrl.isBlank()) {
            log.warn("PAYMENT_URL_CREATED 이벤트에 paymentUrl 누락 - orderId={}", orderId);
            return;
        }

        LocalDateTime createdAt = parseDateTime(eventData.get("createdAt"), LocalDateTime.now());
        LocalDateTime expiresAt = parseDateTime(eventData.get("expiresAt"), createdAt.plusMinutes(30));
        Long amount = parseLong(eventData.get("amount"));
        String token = extractToken(paymentUrl);
        long remainingMinutes = Math.max(Duration.between(LocalDateTime.now(), expiresAt).toMinutes(), 0);

        PaymentUrlResponse response = PaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .token(token)
                .orderId(orderId)
                .orderNo(stringOf(eventData, "orderNo", "order_no"))
                .amount(amount)
                .paymentMethod(stringOf(eventData, "paymentMethod", "payment_method"))
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .expiresInMinutes((int) remainingMinutes)
                .build();

        orderCacheService.storePaymentUrl(orderId, response);
        log.info("PAYMENT_URL_CREATED 캐시 저장 완료 - orderId={}, expiresAt={}", orderId, expiresAt);
    }

    private LocalDateTime parseDateTime(Object raw, LocalDateTime fallback) {
        String value = Objects.toString(raw, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Long parseLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String value = Objects.toString(raw, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractToken(String paymentUrl) {
        if (paymentUrl == null || paymentUrl.isBlank()) {
            return null;
        }
        int idx = paymentUrl.indexOf("token=");
        if (idx < 0) {
            return null;
        }
        String tokenPart = paymentUrl.substring(idx + "token=".length());
        int ampIndex = tokenPart.indexOf('&');
        return ampIndex > 0 ? tokenPart.substring(0, ampIndex) : tokenPart;
    }

    private String stringOf(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = Objects.toString(source.get(key), null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
