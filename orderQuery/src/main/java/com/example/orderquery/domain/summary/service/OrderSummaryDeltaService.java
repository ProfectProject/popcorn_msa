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
public class OrderSummaryDeltaService {

    private final OrderSummaryRepository orderSummaryRepository;
    private final SummaryAppliedLogRepository summaryAppliedLogRepository;

    public boolean applyOrderCreated(UUID eventId,
                                     UUID orderId,
                                     UUID popupId,
                                     boolean hasReservation,
                                     boolean hasGoods,
                                     LocalDateTime appliedAt) {
        int reservationDelta = hasReservation ? 1 : 0;
        int goodsDelta = hasGoods ? 1 : 0;
        if (reservationDelta == 0 && goodsDelta == 0) {
            log.debug("ORDER_CREATED summary update skipped: no eligible item types (orderId={}, popupId={})",
                    orderId, popupId);
            return false;
        }
        return apply(eventId,
                EventType.ORDER_CREATED,
                orderId,
                popupId,
                reservationDelta, 0, 0,
                goodsDelta, 0, 0,
                0,
                appliedAt);
    }

    public boolean applyOrderPaid(UUID eventId,
                                  UUID orderId,
                                  UUID popupId,
                                  boolean hasReservation,
                                  boolean hasGoods,
                                  LocalDateTime appliedAt) {
        int reservationPaidDelta = hasReservation ? 1 : 0;
        int goodsPaidDelta = hasGoods ? 1 : 0;
        if (reservationPaidDelta == 0 && goodsPaidDelta == 0) {
            log.debug("ORDER_PAID summary update skipped: no eligible item types (orderId={}, popupId={})",
                    orderId, popupId);
            return false;
        }
        return apply(eventId,
                EventType.PAYMENT_BECAME_PAID,
                orderId,
                popupId,
                0, reservationPaidDelta, 0,
                0, goodsPaidDelta, 0,
                0,
                appliedAt);
    }

    public boolean applyOrderCancelled(UUID eventId,
                                       UUID orderId,
                                       UUID popupId,
                                       boolean hasReservation,
                                       boolean hasGoods,
                                       LocalDateTime appliedAt) {
        int reservationCancelled = hasReservation ? 1 : 0;
        int goodsCancelled = hasGoods ? 1 : 0;
        if (reservationCancelled == 0 && goodsCancelled == 0) {
            log.debug("ORDER_CANCELLED summary update skipped: no eligible item types (orderId={}, popupId={})",
                    orderId, popupId);
            return false;
        }
        return apply(eventId,
                EventType.ORDER_BECAME_CANCELLED,
                orderId,
                popupId,
                0, 0, reservationCancelled,
                0, 0, goodsCancelled,
                0,
                appliedAt);
    }

    public boolean applyCheckInCreated(UUID eventId,
                                       UUID orderId,
                                       UUID popupId,
                                       LocalDateTime appliedAt) {
        if (popupId == null || eventId == null) {
            log.debug("CHECKIN_CREATED summary update skipped: missing identifiers");
            return false;
        }
        return apply(eventId,
                EventType.CHECKIN_CREATED,
                orderId,
                popupId,
                0, 0, 0,
                0, 0, 0,
                1,
                appliedAt);
    }

    private boolean apply(UUID eventId,
                          EventType eventType,
                          UUID orderId,
                          UUID popupId,
                          int deltaReservationTotal,
                          int deltaReservationPaid,
                          int deltaReservationCancelled,
                          int deltaGoodsTotal,
                          int deltaGoodsPaid,
                          int deltaGoodsCancelled,
                          int deltaCheckedIn,
                          LocalDateTime appliedAt) {
        if (eventId == null || eventType == null || popupId == null) {
            log.debug("Summary update skipped: invalid event metadata (eventId={}, eventType={}, popupId={})",
                    eventId, eventType, popupId);
            return false;
        }

        if (summaryAppliedLogRepository.existsByEventIdAndEventType(eventId, eventType)) {
            log.debug("Summary delta already applied: eventId={}, eventType={}", eventId, eventType);
            return false;
        }

        OrderSummary summary = orderSummaryRepository.findById(popupId).orElse(null);
        if (summary == null) {
            log.warn("Summary update skipped: summary not found for popupId={} (eventId={}, eventType={})",
                    popupId, eventId, eventType);
            return false;
        }

        summary.applyDeltas(deltaReservationTotal, deltaReservationPaid, deltaReservationCancelled,
                deltaGoodsTotal, deltaGoodsPaid, deltaGoodsCancelled,
                deltaCheckedIn, appliedAt);
        orderSummaryRepository.save(summary);

        SummaryAppliedLog logEntry = SummaryAppliedLog.of(
                eventId,
                eventType,
                popupId,
                orderId,
                deltaReservationTotal,
                deltaReservationPaid,
                deltaReservationCancelled,
                deltaGoodsTotal,
                deltaGoodsPaid,
                deltaGoodsCancelled,
                deltaCheckedIn,
                appliedAt);

        summaryAppliedLogRepository.save(logEntry);
        return true;
    }
}
