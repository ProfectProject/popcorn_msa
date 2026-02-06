package com.popcorn.checkIns.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "outbox_events",
    schema = "\"checkIns\"",
    indexes = {
        @Index(name = "idx_outbox_created_at", columnList = "created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_outbox_event_type", columnList = "event_type"),
        @Index(name = "idx_outbox_partition_key", columnList = "partition_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_outbox_event_id", columnNames = "event_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "partition_key", nullable = false, updatable = false, length = 255)
    private String partitionKey;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> headers;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "popup_id")
    private UUID popupId;

    @Column(name = "order_goods_id")
    private UUID orderGoodsId;

    @Builder
    private OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String partitionKey,
        Integer schemaVersion,
        Map<String, Object> eventData,
        Map<String, Object> headers,
        Instant occurredAt,
        UUID popupId,
        UUID orderGoodsId
    ) {
        this.eventId = (eventId != null) ? eventId : UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.partitionKey = partitionKey;
        this.schemaVersion = (schemaVersion != null) ? schemaVersion : 1;
        this.eventData = eventData;
        this.headers = headers;
        this.occurredAt = (occurredAt != null) ? occurredAt : Instant.now();
        this.createdAt = Instant.now();
        this.popupId = popupId;
        this.orderGoodsId = orderGoodsId;
    }

    public static OutboxEvent of(
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload,
        Map<String, Object> headers
    ) {
        return OutboxEvent.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .partitionKey(aggregateId)
            .eventData(payload)
            .headers(headers)
            .build();
    }

    public static OutboxEvent of(
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload,
        Map<String, Object> headers,
        UUID popupId,
        UUID orderGoodsId
    ) {
        return OutboxEvent.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .partitionKey(aggregateId)
            .eventData(payload)
            .headers(headers)
            .popupId(popupId)
            .orderGoodsId(orderGoodsId)
            .build();
    }
}
