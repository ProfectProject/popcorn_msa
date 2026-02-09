package com.popcorn.store.domain.popup.repository.owner.jpa;

import com.popcorn.store.domain.popup.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

}
