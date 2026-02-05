package com.popcorn.checkIns.outbox;

import com.popcorn.common.event.BaseEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void record(BaseEvent event) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", event.getEventId().toString());
        headers.put("correlationId", event.getCorrelationId().toString());
        headers.put("timestamp", event.getTimestamp().toString());
        headers.put("eventVersion", event.getEventVersion());
        headers.put("aggregateType", event.getAggregateType());
        if (event.getUserId() != null) {
            headers.put("userId", event.getUserId());
        }

        OutboxEvent outbox = OutboxEvent.builder()
            .eventId(event.getEventId())
            .aggregateType(event.getAggregateType())
            .aggregateId(event.getAggregateId().toString())
            .eventType(event.getEventType())
            .partitionKey(event.getAggregateId().toString())
            .schemaVersion(1)
            .eventData(event.getEventPayload())
            .headers(headers)
            .occurredAt(Instant.now())
            .build();

        outboxEventRepository.save(outbox);
    }
}
