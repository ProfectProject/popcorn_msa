package com.popcorn.store.event.kafka;

import com.popcorn.store.constants.EventConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Store-Inventory 전용 Kafka 이벤트 발행기.
 * goods.reservation 관련 이벤트를 지정된 store-events 토픽으로 보낸다.
 */
@Component
@Slf4j
public class KafkaPublisher {

    private final KafkaProducerService producerService;
    private final String storeEventsTopic;

    public KafkaPublisher(
            KafkaProducerService producerService,
            @Value("${popcorn.kafka.topics.storeEvents:store-events}") String storeEventsTopic) {
        this.producerService = producerService;
        this.storeEventsTopic = storeEventsTopic;
    }

    /**
     * 굿즈 예약 성공 이벤트를 Kafka store-events로 발행한다.
     */
    public void publishGoodsReservationSucceeded(UUID orderId, UUID goodsId, int quantity, LocalDateTime expiresAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventType", EventConstants.EventTypes.GOODS_RESERVATION_SUCCEEDED);
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("goodsId", safeUuid(goodsId));
        eventData.put("quantity", quantity);
        eventData.put("expiresAt", formatTime(expiresAt));
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        log.info("📣 [KafkaPublisher] 굿즈 예약 성공 이벤트 준비 - orderId={}, goodsId={}, qty={}",
                orderId, goodsId, quantity);

        producerService.publish(storeEventsTopic, keyOrDefault(orderId, eventId), eventData);
    }

    /**
     * 굿즈 예약 실패 이벤트를 Kafka store-events로 발행한다.
     */
    public void publishGoodsReservationFailed(UUID orderId, UUID goodsId, String reason) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventType", EventConstants.EventTypes.GOODS_RESERVATION_FAILED);
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("goodsId", safeUuid(goodsId));
        eventData.put("reason", reason);
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        log.warn("📣 [KafkaPublisher] 굿즈 예약 실패 이벤트 준비 - orderId={}, goodsId={}, reason={}",
                orderId, goodsId, reason);

        producerService.publish(storeEventsTopic, keyOrDefault(orderId, eventId), eventData);
    }

    /**
     * TTL 만료 등으로 인한 예약 만료를 Kafka store-events로 알린다.
     */
    public void publishReservationExpired(UUID orderId, UUID popupId, String reservationType,
                                          List<String> reservationIds, LocalDateTime expiredAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventType", EventConstants.EventTypes.RESERVATION_EXPIRED);
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("popupId", safeUuid(popupId));
        eventData.put("reservationType", reservationType);
        eventData.put("reservationIds", reservationIds);
        eventData.put("expiredAt", formatTime(expiredAt));
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        log.info("📣 [KafkaPublisher] 예약 만료 이벤트 준비 - orderId={}, reservationType={}",
                orderId, reservationType);

        producerService.publish(storeEventsTopic, keyOrDefault(orderId, eventId), eventData);
    }

    private String safeUuid(UUID value) {
        return value != null ? value.toString() : null;
    }

    private String keyOrDefault(UUID orderId, String eventId) {
        return safeUuid(orderId) != null ? orderId.toString() : eventId;
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.toString() : null;
    }
}
