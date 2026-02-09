package com.example.orderquery.domain.summary.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.orderquery.domain.summary.entity.EventType;
import com.example.orderquery.domain.summary.entity.SummaryAppliedLog;

public interface SummaryAppliedLogRepository extends JpaRepository<SummaryAppliedLog, UUID> {

    boolean existsByEventIdAndEventType(UUID eventId, EventType eventType);
}
