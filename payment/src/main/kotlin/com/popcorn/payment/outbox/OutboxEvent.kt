package com.popcorn.payment.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_created_at", columnList = "created_at"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
        Index(name = "idx_outbox_event_type", columnList = "event_type"),
        Index(name = "idx_outbox_partition_key", columnList = "partition_key")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_outbox_event_id", columnNames = ["event_id"])
    ]
)
class OutboxEvent(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:Column(name = "event_id", nullable = false, updatable = false)
    val eventId: UUID,

    @field:Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    val aggregateType: String,

    @field:Column(name = "aggregate_id", nullable = false, updatable = false, length = 255)
    val aggregateId: String,

    @field:Column(name = "event_type", nullable = false, updatable = false, length = 100)
    val eventType: String,

    @field:Column(name = "partition_key", nullable = false, updatable = false, length = 255)
    val partitionKey: String,

    @field:Column(name = "schema_version", nullable = false, updatable = false)
    val schemaVersion: Int = 1,

    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "event_data", nullable = false, updatable = false, columnDefinition = "jsonb")
    val eventData: Map<String, Any?>,

    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "headers", updatable = false, columnDefinition = "jsonb")
    val headers: Map<String, Any?>? = null,

    @field:Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant = Instant.now(),

    @field:Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
