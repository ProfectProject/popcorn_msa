package com.popcorn.store.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreOutboxEventRepository extends JpaRepository<StoreOutboxEvent, Long> {
}
