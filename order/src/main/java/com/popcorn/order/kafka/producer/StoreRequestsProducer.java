package com.popcorn.order.kafka.producer;

import com.popcorn.order.entity.Order;
import com.popcorn.order.service.core.OrderCommandService.ScheduleConfirmationItem;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreRequestsProducer {
    private static final String STORE_REQUESTS_TOPIC = "store-requests";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishGoodsReservationCancelRequested(Order order, UUID goodsId, int quantity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "GOODS_RESERVATION_CANCEL_REQUESTED");
        payload.put("goodsId", goodsId != null ? goodsId.toString() : null);
        payload.put("qty", quantity);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    public void publishScheduleReservationCancelRequested(Order order, UUID scheduleId, int quantity) {
        Map<String, Object> payload = basePayload(order, "SCHEDULE_RESERVATION_CANCEL_REQUESTED");
        payload.put("scheduleId", scheduleId != null ? scheduleId.toString() : null);
        payload.put("quantity", quantity);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    public void publishStockDeductionRequested(Order order, List<com.popcorn.order.event.stock.StockDeductionRequestedEvent.DeductionItem> deductionItems) {
        Map<String, Object> payload = basePayload(order, "STOCK_DEDUCTION_REQUESTED");
        payload.put("deductionItems", deductionItems);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    public void publishScheduleConfirmationRequested(Order order, List<ScheduleConfirmationItem> reservedSessions) {
        //Map<String, Object> payload = new HashMap<>();
        Map<String, Object> payload = basePayload(order, "SCHEDULE_CONFIRMATION_REQUESTED");
        payload.put("reservedSessions", reservedSessions);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    public void publishStockReleaseRequested(Order order, List<Map<String, Object>> releaseItems) {
        Map<String, Object> payload = basePayload(order, "STOCK_RELEASE_REQUESTED");
        payload.put("releaseItems", releaseItems);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    public void publishScheduleReleaseRequested(Order order, List<Map<String, Object>> releaseItems) {
        Map<String, Object> payload = basePayload(order, "SCHEDULE_RELEASE_REQUESTED");
        payload.put("releaseItems", releaseItems);
        kafkaTemplate.send(STORE_REQUESTS_TOPIC, order.getId().toString(), payload);
    }

    private Map<String, Object> basePayload(Order order, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producer", "order-service");
        payload.put("orderId", order.getId().toString());
        payload.put("orderNo", order.getOrderNo());
        payload.put("popupId", order.getPopupId() != null ? order.getPopupId().toString() : null);
        return payload;
    }
}