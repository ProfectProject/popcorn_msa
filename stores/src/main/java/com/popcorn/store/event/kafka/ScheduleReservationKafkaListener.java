package com.popcorn.store.event.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleCapacity;
import com.popcorn.store.domain.popup.repository.PopupScheduleReservationRepository;
import com.popcorn.store.event.inventory.StoreInventoryEventPublisher;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleReservationKafkaListener {

    private final ObjectMapper objectMapper;
    private final PopupScheduleReservationRepository reservationRepository;
    private final StoreInventoryEventPublisher eventPublisher;
    private final InventoryRedisHoldService inventoryHoldService;

    @Value("${popcorn.kafka.topics.storeRequests:store-requests}")
    private String storeRequestsTopic;

    @KafkaListener(topics = "${popcorn.kafka.topics.storeRequests:store-requests}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        try {
            Map<String, Object> envelope = objectMapper.readValue(rawMessage, new TypeReference<>() {});
            String eventType = asString(envelope.get("eventType"));
            if (EventConstants.EventTypes.SCHEDULE_CONFIRMATION_REQUESTED.equals(eventType)) {
                handleScheduleConfirmation(envelope);
            } else if (EventConstants.EventTypes.SCHEDULE_RELEASE_REQUESTED.equals(eventType)) {
                handleScheduleRelease(envelope);
            } else if (EventConstants.EventTypes.SCHEDULE_RESERVATION_CANCEL_REQUESTED.equals(eventType)) {
                handleScheduleReservationCancel(envelope);
            }
        } catch (Exception e) {
            log.error("🚨 [ScheduleReservationKafkaListener] 메시지 처리 실패 - rawMessage={}, error={}", rawMessage, e.getMessage(), e);
        }
    }

    private void handleScheduleConfirmation(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        String orderNo = asString(envelope.get("orderNo"));
        UUID popupId = asUUID(envelope.get("popupId"));
        List<Map<String, Object>> sessions = asListOfMaps(envelope.get("reservedSessions"));
        if (sessions.isEmpty()) {
            log.warn("⚠️ [ScheduleConfirmation] reservedSessions 없음 - orderId={}", orderId);
            return;
        }
        List<String> details = new ArrayList<>();
        boolean anySuccess = false;
        for (Map<String, Object> session : sessions) {
            UUID scheduleId = asUUID(session.get("scheduleId"));
            Integer quantity = asInteger(session.get("qty"));
            if (scheduleId == null || quantity == null || quantity <= 0) {
                continue;
            }
            PopupScheduleCapacity capacity = reservationRepository.completeCapacity(scheduleId, quantity);
            if (capacity == null) {
                log.warn("⚠️ [ScheduleConfirmation] capacity 부족 - scheduleId={}, qty={}", scheduleId, quantity);
                continue;
            }
            details.add(String.format("scheduleId=%s qty=%d", scheduleId, quantity));
            anySuccess = true;
        }
        if (anySuccess) {
            eventPublisher.publishScheduleConfirmationSuccessEvent(orderId, orderNo, popupId, String.join(", ", details));
        } else {
            eventPublisher.publishScheduleConfirmationFailedEvent(orderId, orderNo, popupId,
                    "스케줄 차감 실패");
        }
    }

    private void handleScheduleRelease(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        List<Map<String, Object>> sessions = asListOfMaps(envelope.get("releaseSessions"));
        if (sessions.isEmpty()) {
            sessions = asListOfMaps(envelope.get("releaseItems"));
        }
        if (sessions.isEmpty()) {
            log.warn("⚠️ [ScheduleRelease] releaseSessions 없음 - orderId={}", orderId);
            return;
        }
        boolean anySuccess = false;
        for (Map<String, Object> session : sessions) {
            UUID scheduleId = asUUID(session.get("scheduleId"));
            Integer quantity = asInteger(session.get("qty"));
            if (scheduleId == null || quantity == null || quantity <= 0) {
                continue;
            }
            PopupScheduleCapacity capacity = reservationRepository.cancelCapacity(scheduleId, quantity);
            if (capacity == null) {
                log.warn("⚠️ [ScheduleRelease] reservation_capacity 부족 - scheduleId={}, qty={}", scheduleId, quantity);
                continue;
            }
            anySuccess = true;
        }
        if (anySuccess) {
            eventPublisher.publishScheduleReleasedEvent(orderId, LocalDateTime.now());
        }
    }

    private void handleScheduleReservationCancel(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        inventoryHoldService.releaseHold(orderId);
        eventPublisher.publishScheduleReleasedEvent(orderId, LocalDateTime.now());
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                result.add(typed);
            }
        }
        return result;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private UUID asUUID(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
