package com.popcorn.checkIns.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${checkins.outbox.cleanup.retention-days:7}")
    private long retentionDays;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOutboxEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deleteByCreatedAtBefore(cutoff);
        log.info("[OUTBOX-CLEANUP] deleted={} cutoff={} retentionDays={}", deleted, cutoff, retentionDays);
    }
}
