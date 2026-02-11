package com.popcorn.store.event.kafka;

import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.domain.outbox.StoreOutboxEventPublisher;
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

    private final StoreOutboxEventPublisher outboxPublisher;
    public KafkaPublisher(StoreOutboxEventPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
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

        publishEvent(orderId, EventConstants.EventTypes.GOODS_RESERVATION_SUCCEEDED, eventData);
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

        publishEvent(orderId, EventConstants.EventTypes.GOODS_RESERVATION_FAILED, eventData);
    }

    /**
     * 굿즈 예약 성공 이벤트를 Kafka store-events로 발행한다.
     */
    public void publishScheduleReservationSucceeded(UUID orderId, UUID scheduleId, int quantity, LocalDateTime expiresAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventType", EventConstants.EventTypes.SCHEDULE_RESERVATION_SUCCEEDED);
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("scheduleId", safeUuid(scheduleId));
        eventData.put("quantity", quantity);
        eventData.put("expiresAt", formatTime(expiresAt));
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        log.info("📣 [KafkaPublisher] 스케줄 예약 성공 이벤트 준비 - orderId={}, scheduleId={}, qty={}",
                orderId, scheduleId, quantity);

        publishEvent(orderId, EventConstants.EventTypes.SCHEDULE_RESERVATION_SUCCEEDED, eventData);
    }

    /**
     * 굿즈 예약 실패 이벤트를 Kafka store-events로 발행한다.
     */
    public void publishScheduleReservationFailed(UUID orderId, UUID scheduleId, String reason) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventType", EventConstants.EventTypes.SCHEDULE_RESERVATION_FAILED);
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("scheduleId", safeUuid(scheduleId));
        eventData.put("reason", reason);
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        log.warn("📣 [KafkaPublisher] 스케줄 예약 실패 이벤트 준비 - orderId={}, scheduleId={}, reason={}",
                orderId, scheduleId, reason);

        publishEvent(orderId, EventConstants.EventTypes.SCHEDULE_RESERVATION_FAILED, eventData);
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

        publishEvent(orderId, EventConstants.EventTypes.RESERVATION_EXPIRED, eventData);
    }

    /**
     * 재고 차감 성공 이벤트 발행
     */
    public void publishStockDeductionSucceeded(UUID orderId, String stockDetails, LocalDateTime committedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("stockDetails", stockDetails);
        eventData.put("committedAt", formatTime(committedAt));

        publishEvent(orderId, EventConstants.EventTypes.STOCK_DEDUCTION_SUCCEEDED, eventData);

        log.info("📣 [KafkaPublisher] 재고 차감 성공 이벤트 준비 - orderId={}", orderId);
    }

    /**
     * 재고 차감 실패 이벤트 발행
     */
    public void publishStockDeductionFailed(UUID orderId, String reason, boolean retryable, LocalDateTime failedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("reason", reason);
        eventData.put("retryable", retryable);
        eventData.put("failedAt", formatTime(failedAt));

        publishEvent(orderId, EventConstants.EventTypes.STOCK_DEDUCTION_FAILED, eventData);

        log.warn("📣 [KafkaPublisher] 재고 차감 실패 이벤트 준비 - orderId={}, reason={}", orderId, reason);
    }

    /**
     * 스케줄 확정 성공 이벤트 발행
     */
    public void publishScheduleConfirmationSucceeded(UUID orderId, String stockDetails, LocalDateTime confirmedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("stockDetails", stockDetails);
        eventData.put("confirmedAt", formatTime(confirmedAt));

        publishEvent(orderId, EventConstants.EventTypes.SCHEDULE_CONFIRMATION_SUCCEEDED, eventData);

        log.info("📣 [KafkaPublisher] 스케줄 확정 성공 이벤트 준비 - orderId={}", orderId);
    }

    /**
     * 스케줄 확정 실패 이벤트 발행
     */
    public void publishScheduleConfirmationFailed(UUID orderId, String reason, boolean retryable, LocalDateTime failedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("reason", reason);
        eventData.put("retryable", retryable);
        eventData.put("failedAt", formatTime(failedAt));

        publishEvent(orderId, EventConstants.EventTypes.SCHEDULE_CONFIRMATION_FAILED, eventData);

        log.warn("📣 [KafkaPublisher] 스케줄 확정 실패 이벤트 준비 - orderId={}, reason={}", orderId, reason);
    }

    /**
     * 재고 릴리즈(rollback) 이벤트 발행
     */
    public void publishStockReleased(UUID orderId, LocalDateTime releasedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("releasedAt", formatTime(releasedAt));

        publishEvent(orderId, EventConstants.EventTypes.STOCK_RELEASED, eventData);

        log.info("📣 [KafkaPublisher] 재고 릴리즈 이벤트 준비 - orderId={}", orderId);
    }

    /**
     * 스케줄 릴리즈 이벤트 발행
     */
    public void publishScheduleReleased(UUID orderId, LocalDateTime releasedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", safeUuid(orderId));
        eventData.put("releasedAt", formatTime(releasedAt));

        publishEvent(orderId, EventConstants.EventTypes.SCHEDULE_RELEASED, eventData);

        log.info("📣 [KafkaPublisher] 스케줄 릴리즈 이벤트 준비 - orderId={}", orderId);
    }

    public void publishPopupCreated(UUID popupId, String popupName, LocalDateTime createdAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("popupId", safeUuid(popupId));
        eventData.put("popupName", popupName);
        eventData.put("createdAt", formatTime(createdAt));

        publishEvent(popupId, EventConstants.EventTypes.POPUP_CREATED, eventData);

        log.info("📣 [KafkaPublisher] 팝업 생성 이벤트 준비 - popupId={}", popupId);
    }

    public void publishPopupStatusUpdated(UUID popupId, String status, LocalDateTime updatedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("popupId", safeUuid(popupId));
        eventData.put("status", status);
        eventData.put("updatedAt", formatTime(updatedAt));

        publishEvent(popupId, EventConstants.EventTypes.POPUP_STATUS_UPDATED, eventData);

        log.info("📣 [KafkaPublisher] 팝업 상태 변경 이벤트 준비 - popupId={}", popupId);
    }

    public void publishPopupInfoUpdated(UUID popupId, String name, String description, LocalDateTime updatedAt) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("popupId", safeUuid(popupId));
        eventData.put("name", name);
        eventData.put("description", description);
        eventData.put("updatedAt", formatTime(updatedAt));

        publishEvent(popupId, EventConstants.EventTypes.POPUP_INFO_UPDATED, eventData);

        log.info("📣 [KafkaPublisher] 팝업 정보 변경 이벤트 준비 - popupId={}", popupId);
    }

    private String safeUuid(UUID value) {
        return value != null ? value.toString() : null;
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.toString() : null;
    }

    private void publishEvent(UUID orderId, String eventType, Map<String, Object> basePayload) {
        Map<String, Object> eventData = new HashMap<>(basePayload);
        Object existingEventId = eventData.get("eventId");
        String eventId = existingEventId != null ? existingEventId.toString() : UUID.randomUUID().toString();
        eventData.put("eventId", eventId);
        eventData.put("eventType", eventType);
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        outboxPublisher.publishStoreEvent("store", orderId, eventType, eventData);
    }


}
