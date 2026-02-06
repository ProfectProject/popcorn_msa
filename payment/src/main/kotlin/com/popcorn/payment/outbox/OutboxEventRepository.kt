package com.popcorn.payment.outbox

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    fun existsByEventId(eventId: UUID): Boolean
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
