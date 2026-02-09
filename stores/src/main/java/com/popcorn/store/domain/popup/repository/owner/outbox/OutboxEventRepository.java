package com.popcorn.store.domain.popup.repository.owner.outbox;

import com.popcorn.store.domain.popup.entity.OutboxEvent;

import java.util.Optional;

public interface OutboxEventRepository {
    void save(OutboxEvent event);

    Optional<OutboxEvent> findById(Long id);
}
