package com.popcorn.payment.outbox

import com.popcorn.payment.event.base.BasePaymentEvent
import java.time.Instant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxWriter(
    private val outboxEventRepository: OutboxEventRepository
) {

    @Transactional
    fun record(event: BasePaymentEvent) {
        val headers = mutableMapOf<String, Any?>(
            "eventId" to event.eventId.toString(),
            "correlationId" to event.correlationId.toString(),
            "timestamp" to event.timestamp.toString(),
            "eventVersion" to event.eventVersion,
            "aggregateType" to event.aggregateType
        )
        event.userId?.let { headers["userId"] = it }

        val outbox = OutboxEvent(
            eventId = event.eventId,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId.toString(),
            eventType = event.eventType,
            partitionKey = event.aggregateId.toString(),
            schemaVersion = 1,
            eventData = event.eventPayload,
            headers = headers,
            occurredAt = Instant.now()
        )

        outboxEventRepository.save(outbox)
    }
}
