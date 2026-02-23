package com.popcorn.store.event.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.event.inventory.OrderCreatedReservationService;
import com.popcorn.store.event.order.OrderCreatedEvent;
import com.popcorn.store.event.standard.EventLineItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka order-events 토픽을 구독하여 ORDER_CREATED 이벤트를 처리한다.
 * payload를 OrderCreatedEvent로 변환하여 주문 예약 로직을 직접 호출한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListener {

    private final ObjectMapper objectMapper;
    private final OrderCreatedReservationService reservationService;

    @Value("${popcorn.kafka.topics.orderEvents:order-events}")
    private String orderEventsTopic;

    /**
     * ORDER_CREATED 메시지를 받으면 파싱하여 Store 내부 이벤트로 변환한다.
     */
    @org.springframework.kafka.annotation.KafkaListener(
            topics = "${popcorn.kafka.topics.orderEvents:order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String rawMessage) {
        try {
            if (rawMessage == null || rawMessage.trim().isEmpty()) {
                log.debug("⚠️ [KafkaListener] 빈 메시지 수신, 파싱 없이 무시");
                return;
            }

            Map<String, Object> envelope = objectMapper.readValue(rawMessage,
                    new TypeReference<>() {});
            String eventType = asString(envelope.get("eventType"));
            if (!EventConstants.EventTypes.ORDER_CREATED.equals(eventType)) {
                log.debug("⭐️ [KafkaListener] 무시할 이벤트 수신 - topic={}, eventType={}",
                        orderEventsTopic, eventType);
                return;
            }

            OrderCreatedEvent storeEvent = buildOrderCreatedEvent(envelope);
            log.info("🧾 [KafkaListener] ORDER_CREATED 처리 시작 - orderId={}, eventId={}, popupId={}",
                    storeEvent.getOrderId(), storeEvent.getEventId(), storeEvent.getPopupId());
            reservationService.reserveForOrderCreated(storeEvent);


        } catch (Exception e) {
            log.error("🚨 [KafkaListener] ORDER_CREATED 처리 실패 - rawMessage={}", rawMessage, e);
        }
    }

    private OrderCreatedEvent buildOrderCreatedEvent(Map<String, Object> envelope) {
        return OrderCreatedEvent.builder()
                .eventId(asString(envelope.get("eventId")))
                .orderId(asUUID(envelope.get("orderId")))
                .orderNo(asString(envelope.get("orderNo")))
                .userId(asLong(envelope.get("userId")))
                .orderType(asString(envelope.get("orderType")))
                .popupId(asUUID(envelope.get("popupId")))
                .hasReservation(asBoolean(envelope.get("hasReservation")))
                .hasGoods(asBoolean(envelope.get("hasGoods")))
                .lines(buildLineItems(envelope.get("lines")))
                .totalAmount(asInteger(envelope.get("totalAmount")))
                .createdAt(asString(envelope.get("timestamp")))
                .build();
    }

    private List<EventLineItem> buildLineItems(Object rawLines) {
        if (!(rawLines instanceof List<?> lines)) {
            return List.of();
        }
        return lines.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> objectMapper.convertValue(item, EventLineItem.class))
                .toList();
    }

    private UUID asUUID(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().replace("\"", ""));
        } catch (Exception e) {
            log.warn("⚠️ [KafkaListener] UUID 변환 실패 - value={}", value);
            return null;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString().replace("\"", "");
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().replace("\"", ""));
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().replace("\"", ""));
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString().replace("\"", ""));
    }
}
