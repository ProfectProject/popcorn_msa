package com.popcorn.order.kafka.consumer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.service.core.OrderCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoreEventConsumer {
    private final OrderCommandService orderCommandService;

    @KafkaListener(
            topics = "store-events",
            groupId = "${kafka.consumer.groups.store-reservation:order-reservation-cg}"
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
            log.info("StoreEventConsumer payload={}", payload);

            Map<String, Object> eventData = unwrapPayloadIfNeeded(payload);
            String eventType = normalizeEventType(eventData);
            String orderIdRaw = stringOf(eventData, "orderId", "order_id");

            log.info("카프카 orderId={}, eventType={}", orderIdRaw, eventType);

            if (eventType == null || eventType.isBlank()) {
                log.warn("[KAFKA_CONSUME_SKIP] eventType is empty. topic={}, partition={}, offset={}",
                        topic, partition, offset);
                acknowledgment.acknowledge();
                return;
            }

            if (orderIdRaw == null || orderIdRaw.isBlank()) {
                log.warn("[KAFKA_CONSUME_SKIP] orderId is empty. topic={}, partition={}, offset={}",
                        topic, partition, offset);
                acknowledgment.acknowledge();
                return;
            }

            UUID orderId;
            try {
                orderId = UUID.fromString(orderIdRaw);
            } catch (IllegalArgumentException e) {
                log.warn("[KAFKA_CONSUME_SKIP] invalid orderId. topic={}, partition={}, offset={}, orderId={}",
                        topic, partition, offset, orderIdRaw);
                acknowledgment.acknowledge();
                return;
            }

            switch (eventType) {
                case "GOODS_RESERVATION_SUCCEEDED":
                case "SCHEDULE_RESERVATION_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.RESERVED.name(),
                            "예약 성공 이벤트 수신");
                    orderCommandService.publishPaymentCreateRequestedEvent(orderId);
                    break;
                case "STOCK_DEDUCTION_SUCCEEDED":
                case "SCHEDULE_CONFIRMATION_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.COMPLETED.name(),
                            "차감/확정 성공 이벤트 수신");
                    orderCommandService.publishOrderCompletedEvent(orderId);
                    break;
                case "GOODS_RESERVATION_FAILED":
                case "SCHEDULE_RESERVATION_FAILED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.REJECTED.name(),
                            "예약 실패 이벤트 수신");

                    String reason = stringOf(eventData, "reason");
                    log.warn("재고 예약 실패 처리 - orderId: {}, reason: {}", orderId, reason);
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "재고 예약 실패 처리 : " + (reason != null ? reason : "재고 부족"));
                    break;
                case "RESERVATION_EXPIRED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.REJECTED.name(),
                            "예약 만료 이벤트 수신");
                    break;
                case "STOCK_DEDUCTION_FAILED":
                case "SCHEDULE_CONFIRMATION_FAILED":
                    orderCommandService.cancelPaymentForOrder(orderId, null,
                            "차감/확정 실패로 인한 결제 취소");
                    break;
                case "STOCK_RELEASED":
                case "SCHEDULE_RELEASED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "재고/스케줄 해제 이벤트 수신");
                    break;
                default:
                    log.info("Unhandled store eventType: {}", eventType);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA_CONSUME_FAIL] topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);
            throw e;
        }
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

    private String normalizeEventType(Map<String, Object> eventData) {
        String eventType = stringOf(eventData, "eventType", "event_type");
        if (eventType != null && !eventType.isBlank()) {
            return eventType;
        }
        return null;
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
