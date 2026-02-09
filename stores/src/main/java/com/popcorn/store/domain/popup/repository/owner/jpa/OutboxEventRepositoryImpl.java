package com.popcorn.store.domain.popup.repository.owner.jpa;

import com.popcorn.store.domain.popup.entity.OutboxEvent;
import com.popcorn.store.domain.popup.repository.owner.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;

    @Override
    public void save(OutboxEvent event) {
        jpaOutboxEventRepository.save(event);
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return jpaOutboxEventRepository.findById(id);
    }
}
