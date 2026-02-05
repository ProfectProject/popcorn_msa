package com.popcorn.payment.outbox

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    fun existsByEventId(eventId: UUID): Boolean
}
