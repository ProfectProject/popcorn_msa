package com.popcorn.payment.outbox

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxCleanupScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    @Value("\${payment.outbox.cleanup.retention-days:7}")
    private val retentionDays: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    fun cleanupOutboxEvents() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val deleted = outboxEventRepository.deleteByCreatedAtBefore(cutoff)
        log.info("[OUTBOX-CLEANUP] deleted={} cutoff={} retentionDays={}", deleted, cutoff, retentionDays)
    }
}
