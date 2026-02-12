package com.popcorn.checkIns.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    boolean existsByEventId(UUID eventId);

    int deleteByCreatedAtBefore(Instant cutoff);
}
