package com.popcorn.store.domain.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.popcorn.store.constants.EventConstants;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreOutboxEventPublisher {

    private final StoreOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishStoreEvent(String aggregateType,
                                  UUID aggregateId,
                                  String eventType,
                                  Map<String, Object> payload) {
        if (aggregateType == null || aggregateId == null || eventType == null || payload == null) {
            throw new IllegalArgumentException("aggregateType, aggregateId, eventType, and payload must not be null");
        }
        UUID eventId = resolveEventId(payload);
        ObjectNode eventData = convertPayloadToNode(payload);
        eventData.put("eventId", eventId.toString());

        String partitionKey = extractPartitionKey(payload);
        String topic = Optional.ofNullable(payload.get("topic"))
                .map(Object::toString)
                .orElse(EventConstants.Streams.STORE_EVENTS);

        eventData.put("eventType", eventType);
        eventData.put("occurredAt", LocalDateTime.now().toString());
        eventData.put("producer", "store-service");

        StoreOutboxEvent event = StoreOutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId.toString())
                .eventType(eventType)
                .eventId(eventId)
                .partitionKey(partitionKey)
                .topic(topic)
                .schemaVersion(4)
                .eventData(eventData)
                .headers(null)
                .occurredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(event);

        log.info("📦 [OUTBOX] store event queued - aggregateType={}, aggregateId={}, eventType={}, eventId={}",
                aggregateType, aggregateId, eventType, eventId);
    }

    private String extractPartitionKey(Map<String, Object> payload) {
        return Optional.ofNullable(payload.get("orderId"))
                .map(Object::toString)
                .orElse(null);
    }

    private UUID resolveEventId(Map<String, Object> payload) {
        return Optional.ofNullable(payload.get("eventId"))
                .map(Object::toString)
                .flatMap(this::tryParseUuid)
                .orElseGet(UUID::randomUUID);
    }

    private Optional<UUID> tryParseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid eventId payload value '{}', generating a new UUID", value, ex);
            return Optional.empty();
        }
    }

    private ObjectNode convertPayloadToNode(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.convertValue(payload, ObjectNode.class);
        } catch (IllegalArgumentException ex) {
            log.warn("event payload conversion failed, storing empty JSON object instead", ex);
            return objectMapper.createObjectNode();
        }
    }

}
