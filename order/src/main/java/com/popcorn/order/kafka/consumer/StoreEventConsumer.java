package com.popcorn.order.kafka.consumer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            String kafkaMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ){
        log.info("[KAFKA_CONSUME] topic={}, partition={}, offset={}, key={}", topic, partition, offset, key);
        log.info("StoreEventConsumer payload={}", kafkaMessage);

        Map<String, Object> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            map = parseKafkaMessage(kafkaMessage, mapper);
        } catch (JsonProcessingException e) {
            log.error("[KAFKA_CONSUME_PARSE_FAIL] topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);
            return;
        }

        Map<String, Object> eventData = unwrapPayloadIfNeeded(map);

        String eventType = normalizeEventType(eventData);
        String orderIdRaw = Objects.toString(eventData.get("orderId"), null);

        log.info("카프카 orderId={} , eventType={}", orderIdRaw, eventType);

        /*if (orderIdRaw  == null || orderIdRaw .isBlank()) {
            return null;
        }*/

        if (orderIdRaw == null || orderIdRaw.isBlank()) {
            log.warn("[KAFKA_CONSUME_SKIP] orderId is empty. topic={}, partition={}, offset={}",
                    topic, partition, offset);
            return;
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdRaw);
        } catch (IllegalArgumentException e) {
            log.warn("[KAFKA_CONSUME_SKIP] invalid orderId. topic={}, partition={}, offset={}, orderId={}",
                    topic, partition, offset, orderIdRaw);
            return;
        }
        

        switch (eventType) {
                case "GOODS_RESERVATION_SUCCEEDED":
                case "SCHEDULE_RESERVATION_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.RESERVED.name(),
                            "예약 성공 이벤트 수신");
                    // 결제 생성 요청 이벤트 발행
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

                    String reason = Objects.toString(eventData.get("reason"), null);
                    log.warn(" 재고 예약 실패 처리 - orderId: {}, reason: {}", orderId, reason);

                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                        " 재고 예약 실패 처리 : " + (reason != null ? reason : "재고 부족"));
                    break;
                case "RESERVATION_EXPIRED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.REJECTED.name(),
                            "예약 만료 이벤트 수신");
                    break;
                case "STOCK_DEDUCTION_FAILED":
                case "SCHEDULE_CONFIRMATION_FAILED":
                    /*orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "차감/확정 실패 이벤트 수신");*/
                    //String paymentId = (String)map.get("paymentId");
                    orderCommandService.cancelPaymentForOrder(orderId, null,
                            "차감/확정 실패로 인한 결제 취소");
                    break;
                case "STOCK_RELEASED":
                case "SCHEDULE_RELEASED":
                    // 최종적으로 여기서 cancel?
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "재고/스케줄 해제 이벤트 수신");
                    break;
                default:
                    log.info("Unhandled store eventType: {}", eventType);
        }
    }

    private Map<String, Object> parseKafkaMessage(String kafkaMessage, ObjectMapper mapper) throws JsonProcessingException {
        String trimmed = kafkaMessage == null ? "" : kafkaMessage.trim();
        if (trimmed.isEmpty()) {
            return new HashMap<>();
        }

        // 일부 프로듀서/커넥터에서 JSON 문자열을 한 번 더 감싸서 보낼 수 있어 보정.
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            String unwrapped = mapper.readValue(trimmed, String.class);
            return mapper.readValue(unwrapped, new TypeReference<Map<String, Object>>() {});
        }

        return mapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapPayloadIfNeeded(Map<String, Object> parsed) {
        Object payload = parsed.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            try {
                return (Map<String, Object>) payloadMap;
            } catch (ClassCastException ignored) {
                // fall through and use original map
            }
        }
        return parsed;
    }

    private String normalizeEventType(Map<String, Object> eventData) {
        String eventType = Objects.toString(eventData.get("eventType"), null);
        if (eventType != null && !eventType.isBlank()) {
            return eventType;
        }
        // snake_case 호환 fallback
        return Objects.toString(eventData.get("event_type"), null);
    }
}
