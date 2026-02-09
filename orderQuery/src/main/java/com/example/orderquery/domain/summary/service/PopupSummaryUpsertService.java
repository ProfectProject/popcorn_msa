package com.example.orderquery.domain.summary.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.summary.entity.EventType;
import com.example.orderquery.domain.summary.entity.OrderSummary;
import com.example.orderquery.domain.summary.entity.SummaryAppliedLog;
import com.example.orderquery.domain.summary.repository.OrderSummaryRepository;
import com.example.orderquery.domain.summary.repository.SummaryAppliedLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PopupSummaryUpsertService {

    private final OrderSummaryRepository orderSummaryRepository;
    private final SummaryAppliedLogRepository summaryAppliedLogRepository;

    public void createFromPopup(UUID eventId,
                                EventType eventType,
                                UUID popupId,
                                UUID storeId,
                                Long ownerId,
                                String title,
                                String status,
                                String addressRoad,
                                String addressDetail,
                                LocalDateTime reservationOpenAt) {
        if (isAlreadyApplied(eventId, eventType)) {
            log.debug("Popup create event already applied: eventId={}, eventType={}", eventId, eventType);
            return;
        }

        OrderSummary summary = orderSummaryRepository.findByStoreIdAndPopupId(storeId, popupId)
                .orElse(null);
        if (summary != null) {
            if (ownerId != null && summary.getOwnerId() == null) {
                summary.setOwnerId(ownerId);
            }
            summary.setUpdatedBy(ownerId);
            summary.updatePopupInfo(title, status, addressRoad, addressDetail, reservationOpenAt);
            orderSummaryRepository.save(summary);
            return;
        }

        OrderSummary created = OrderSummary.builder()
                .popupId(popupId)
                .storeId(storeId)
                .popupTitle(title)
                .popupStatus(status)
                .addressRoad(addressRoad)
                .addressDetail(addressDetail)
                .reservationOpenAt(reservationOpenAt)
                .ownerId(ownerId)
                .reservationTotalOrders(0)
                .reservationPaidOrders(0)
                .reservationCancelledOrders(0)
                .goodsTotalOrders(0)
                .goodsPaidOrders(0)
                .goodsCancelledOrders(0)
                .checkedInOrders(0)
                .build();
        created.setCreatedBy(ownerId);
        created.setUpdatedBy(ownerId);
        orderSummaryRepository.save(created);
        recordPopupEvent(eventId, eventType, popupId);
    }

    public void updateFromPopup(UUID eventId,
                                EventType eventType,
                                UUID popupId,
                                UUID storeId,
                                Long ownerId,
                                String title,
                                String status,
                                String addressRoad,
                                String addressDetail,
                                LocalDateTime reservationOpenAt) {
        if (isAlreadyApplied(eventId, eventType)) {
            log.debug("Popup update event already applied: eventId={}, eventType={}", eventId, eventType);
            return;
        }
        OrderSummary summary = orderSummaryRepository.findByStoreIdAndPopupId(storeId, popupId)
                .orElse(null);
        if (summary == null) {
            log.warn("Popup summary update ignored: summary not found. storeId={}, popupId={}", storeId, popupId);
            return;
        }

        if (ownerId != null && summary.getOwnerId() == null) {
            summary.setOwnerId(ownerId);
        }
        summary.setUpdatedBy(ownerId);
        summary.updatePopupInfo(title, status, addressRoad, addressDetail, reservationOpenAt);
        orderSummaryRepository.save(summary);
        recordPopupEvent(eventId, eventType, popupId);
    }

    public void deleteSummary(UUID storeId, UUID popupId) {
        orderSummaryRepository.findByStoreIdAndPopupId(storeId, popupId)
                .ifPresent(orderSummaryRepository::delete);
    }

    private boolean isAlreadyApplied(UUID eventId, EventType eventType) {
        return eventId != null && eventType != null && summaryAppliedLogRepository.existsByEventIdAndEventType(eventId, eventType);
    }

    private void recordPopupEvent(UUID eventId, EventType eventType, UUID popupId) {
        if (eventId == null || eventType == null || popupId == null) {
            return;
        }
        SummaryAppliedLog logEntry = SummaryAppliedLog.of(
                eventId,
                eventType,
                popupId,
                null,
                0, 0, 0,
                0, 0, 0,
                0,
                LocalDateTime.now());
        summaryAppliedLogRepository.save(logEntry);
    }
}
