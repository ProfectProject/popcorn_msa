package com.popcorn.store.event.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleCapacity;
import com.popcorn.store.domain.popup.repository.PopupScheduleReservationRepository;
import com.popcorn.store.event.inventory.StoreInventoryEventPublisher;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreRequestsKafkaListener {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final PopupScheduleReservationRepository scheduleReservationRepository;
    private final StoreInventoryEventPublisher eventPublisher;
    private final InventoryRedisHoldService inventoryHoldService;
    private final GoodsService goodsService;
    private final GoodsOrderReservationService reservationService;

    @Value("${popcorn.kafka.topics.storeRequests:store-requests}")
    private String storeRequestsTopic;

    @KafkaListener(topics = "${popcorn.kafka.topics.storeRequests:store-requests}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        try {
            Map<String, Object> envelope = objectMapper.readValue(rawMessage, new TypeReference<>() {});
            String eventType = asString(envelope.get("eventType"));
            if (eventType == null) {
                log.warn("⚠️ [StoreRequestsKafkaListener] eventType 누락 - message={}", rawMessage);
                return;
            }
            switch (eventType) {
                case EventConstants.EventTypes.SCHEDULE_CONFIRMATION_REQUESTED -> handleScheduleConfirmation(envelope);
                case EventConstants.EventTypes.SCHEDULE_RELEASE_REQUESTED -> handleScheduleRelease(envelope);
                case EventConstants.EventTypes.SCHEDULE_RESERVATION_CANCEL_REQUESTED -> handleScheduleReservationCancel(envelope);
                case EventConstants.EventTypes.GOODS_RESERVATION_CANCEL_REQUESTED -> handleGoodsReservationCancellation(envelope);
                case EventConstants.EventTypes.STOCK_DEDUCTION_REQUESTED -> handleStockDeduction(envelope);
                case EventConstants.EventTypes.STOCK_RELEASE_REQUESTED -> handleStockRelease(envelope);
                default -> log.debug("ℹ️ [StoreRequestsKafkaListener] 처리 대상 없음 - eventType={}", eventType);
            }
        } catch (Exception e) {
            log.error("🚨 [StoreRequestsKafkaListener] 메시지 처리 실패 - error={}", e.getMessage(), e);
        }
    }

    private void handleGoodsReservationCancellation(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        if (orderId == null) {
            log.warn("⚠️ [GoodsReservationCancel] orderId 누락");
            return;
        }
        UUID goodsId = firstNotNullUUID(envelope.get("goodsVariantId"), envelope.get("goodsId"));
        if (goodsId == null) {
            log.warn("⚠️ [GoodsReservationCancel] goodsVariantId 누락 - orderId={}", orderId);
            return;
        }
        Integer qty = asInteger(envelope.get("qty"));
        if (qty == null || qty <= 0) {
            qty = asInteger(envelope.get("quantity"));
        }

        Optional<GoodsOrderReservation> reservationOpt =
                reservationService.findLatestGoodsReservation(orderId, goodsId);
        if (reservationOpt.isEmpty()) {
            log.warn("⚠️ [GoodsReservationCancel] 예약 정보 없음 - orderId={}, goodsId={}", orderId, goodsId);
            releaseHoldWithRetry(orderId, "goods-reservation-cancel");
            return;
        }
        GoodsOrderReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != ReservationStatus.HELD) {
            log.info("ℹ️ [GoodsReservationCancel] 이미 처리된 상태 - orderId={}, status={}",
                    orderId, reservation.getStatus());
            releaseHoldWithRetry(orderId, "goods-reservation-cancel");
            return;
        }
        int releaseQty = qty != null && qty > 0 ? qty : reservation.getQuantity();
        boolean success = releaseGoodsReservation(reservation, releaseQty, "goods-reservation-cancel");
        if (success) {
            log.info("✅ [GoodsReservationCancel] 예약 취소 완료 - orderId={}, goodsId={}", orderId, goodsId);
        } else {
            log.error("💀 [GoodsReservationCancel] 예약 취소 실패 - orderId={}, goodsId={}", orderId, goodsId);
        }
        releaseHoldWithRetry(orderId, "goods-reservation-cancel");
    }

    private void handleStockDeduction(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        String providedOrderNo = asString(envelope.get("orderNo"));
        List<Map<String, Object>> deductionItems = asListOfMaps(envelope.get("deductionItems"));
        List<GoodsOrderReservation> existingReservations =
                orderId != null ? reservationService.findByOrderId(orderId) : List.of();
        UUID popupId = resolvePopupId(orderId, asUUID(envelope.get("popupId")), existingReservations, deductionItems);
        String orderNo = resolveOrderNo(existingReservations, providedOrderNo);
        if (orderId == null || popupId == null) {
            log.warn("⚠️ [StockDeduction] orderId 또는 popupId 누락 - derived orderId={}, popupId={}", orderId, popupId);
            return;
        }
        if (deductionItems.isEmpty()) {
            log.warn("⚠️ [StockDeduction] deductionItems 없음 - orderId={}", orderId);
            return;
        }
        if (!inventoryHoldService.hasHold(orderId)) {
            if (hasTerminalReservations(existingReservations, ReservationType.GOODS)) {
                log.info("ℹ️ [StockDeduction] HOLD 없음 + 이미 종결된 예약 상태, 재발행 생략 - orderId={}", orderId);
                return;
            }
            if (!hasHeldReservations(existingReservations, ReservationType.GOODS)) {
                log.info("ℹ️ [StockDeduction] HOLD 없음 + 처리 대상 HELD 예약 없음, 재발행 생략 - orderId={}", orderId);
                return;
            }
            markHeldReservationsAsFailed(existingReservations, ReservationType.GOODS, "hold missing");
            log.warn("⚠️ [StockDeduction] Redis HOLD 없음 - orderId={}", orderId);
            eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                    "Redis HOLD 없음", "HOLD_MISSING", "reservation key missing");
            return;
        }
        List<String> details = new ArrayList<>();
        for (Map<String, Object> item : deductionItems) {
            UUID goodsId = firstNotNullUUID(item.get("goodsVariantId"), item.get("goodsId"));
            Integer qty = asInteger(item.get("quantity"));
            if (qty == null || qty <= 0) {
                qty = asInteger(item.get("qty"));
            }
            if (goodsId == null || qty == null || qty <= 0) {
                continue;
            }
            int finalQty = qty;
            UUID finalGoodsId = goodsId;
            Optional<GoodsOrderReservation> reservationOpt = getOrCreateGoodsReservation(
                    orderId, orderNo, popupId, finalGoodsId, finalQty);
            if (reservationOpt.isEmpty()) {
                log.warn("⚠️ [StockDeduction] 예약 정보 준비 불가 - orderId={}, goodsId={}", orderId, finalGoodsId);
                continue;
            }
            GoodsOrderReservation reservation = reservationOpt.get();
            if (reservation.getStatus() == ReservationStatus.COMMITTED) {
                details.add(String.format("goodsId=%s qty=%d (already committed)", goodsId, reservation.getQuantity()));
                continue;
            }
            if (reservation.getStatus() != ReservationStatus.HELD) {
                log.info("ℹ️ [StockDeduction] 처리할 상태 아님 - orderId={}, status={}",
                        orderId, reservation.getStatus());
                continue;
            }
            boolean itemSuccess = runWithRetry("stock-deduction goodsVariantId=" + goodsId,
                    () -> {
                        goodsService.completeReservationGoods(popupId, finalGoodsId, finalQty);
                        return true;
                    });
            if (!itemSuccess) {
                reservationService.updateStatus(reservation, ReservationStatus.FAILED,
                        "stock deduction failed");
                eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                        "굿즈 재고 차감 실패", "DB_ERROR", "goodsId=" + goodsId);
                releaseHoldWithRetry(orderId, "stock-deduction");
                return;
            }
            reservationService.updateStatus(reservation, ReservationStatus.COMMITTED, null);
            details.add(String.format("goodsId=%s qty=%d", goodsId, qty));
        }
        releaseHoldWithRetry(orderId, "stock-deduction");
        if (details.isEmpty()) {
            eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                    "차감할 항목 없음", "NO_ITEMS", "items empty");
            return;
        }
        eventPublisher.publishStockDeductionSuccessEvent(orderId, orderNo, popupId, String.join(", ", details));
    }

    private void handleStockRelease(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        UUID popupId = asUUID(envelope.get("popupId"));
        List<Map<String, Object>> releaseItems = asListOfMaps(envelope.get("releaseItems"));
        if (releaseItems.isEmpty()) {
            releaseItems = asListOfMaps(envelope.get("releaseSessions"));
        }
        boolean anySuccess = false;
        for (Map<String, Object> item : releaseItems) {
            UUID goodsId = firstNotNullUUID(item.get("goodsVariantId"), item.get("goodsId"));
            Integer qty = asInteger(item.get("quantity"));
            if (qty == null || qty <= 0) {
                qty = asInteger(item.get("qty"));
            }
            if (goodsId == null || qty == null || qty <= 0) {
                continue;
            }
            Optional<GoodsOrderReservation> reservationOpt =
                    reservationService.findLatestGoodsReservation(orderId, goodsId);
            if (reservationOpt.isEmpty()) {
                log.warn("⚠️ [StockRelease] 예약 정보 없음 - orderId={}, goodsId={}", orderId, goodsId);
                continue;
            }
            GoodsOrderReservation reservation = reservationOpt.get();
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                anySuccess = true;
                log.info("ℹ️ [StockRelease] 이미 릴리즈된 상태 - orderId={}, goodsId={}", orderId, goodsId);
                continue;
            }
            if (reservation.getStatus() != ReservationStatus.HELD) {
                log.info("ℹ️ [StockRelease] 처리할 상태 아님 - orderId={}, status={}", orderId, reservation.getStatus());
                continue;
            }
            int releaseQty = qty != null && qty > 0 ? qty : reservation.getQuantity();
            if (releaseGoodsReservation(reservation, releaseQty, "stock-release")) {
                anySuccess = true;
            }
        }
        releaseHoldWithRetry(orderId, "stock-release");
        if (anySuccess) {
            eventPublisher.publishStockReleasedEvent(orderId, LocalDateTime.now());
        } else {
            log.error("💀 [StockRelease] 재고 복구 실패 - orderId={} (DLQ/알람 필요)", orderId);
        }
    }

    private void handleScheduleConfirmation(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        String orderNo = asString(envelope.get("orderNo"));
        UUID popupId = asUUID(envelope.get("popupId"));
        List<Map<String, Object>> sessions = asListOfMaps(envelope.get("reservedSessions"));
        if (sessions.isEmpty()) {
            sessions = asListOfMaps(envelope.get("sessions"));
        }
        if (sessions.isEmpty()) {
            log.warn("⚠️ [ScheduleConfirmation] reservedSessions 없음 - orderId={}", orderId);
            return;
        }
        if (!inventoryHoldService.hasHold(orderId)) {
            List<GoodsOrderReservation> existingReservations =
                    orderId != null ? reservationService.findByOrderId(orderId) : List.of();
            if (hasTerminalReservations(existingReservations, ReservationType.SCHEDULE)) {
                log.info("ℹ️ [ScheduleConfirmation] HOLD 없음 + 이미 종결된 예약 상태, 재발행 생략 - orderId={}", orderId);
                return;
            }
            if (!hasHeldReservations(existingReservations, ReservationType.SCHEDULE)) {
                log.info("ℹ️ [ScheduleConfirmation] HOLD 없음 + 처리 대상 HELD 예약 없음, 재발행 생략 - orderId={}", orderId);
                return;
            }
            markHeldReservationsAsFailed(existingReservations, ReservationType.SCHEDULE, "hold missing");
            log.warn("⚠️ [ScheduleConfirmation] Redis HOLD 없음 - orderId={}", orderId);
            eventPublisher.publishScheduleConfirmationFailedEvent(orderId, orderNo, popupId,
                    "Redis HOLD 없음");
            return;
        }
        List<String> details = new ArrayList<>();
        boolean anySuccess = false;
        for (Map<String, Object> session : sessions) {
            UUID scheduleId = asUUID(session.get("scheduleId"));
            Integer qty = asInteger(session.get("qty"));
            if (qty == null || qty <= 0) {
                qty = asInteger(session.get("quantity"));
            }
            if (scheduleId == null || qty == null || qty <= 0) {
                continue;
            }
            int finalQty = qty;
            UUID finalScheduleId = scheduleId;
            Optional<GoodsOrderReservation> reservationOpt = getOrCreateScheduleReservation(
                    orderId, orderNo, popupId, finalScheduleId, finalQty);
            if (reservationOpt.isEmpty()) {
                log.warn("⚠️ [ScheduleConfirmation] 예약 정보 누락 - orderId={}, scheduleId={}", orderId, finalScheduleId);
                continue;
            }
            GoodsOrderReservation reservation = reservationOpt.get();
            if (reservation.getStatus() == ReservationStatus.COMMITTED) {
                details.add(String.format("scheduleId=%s qty=%d (already committed)", scheduleId, reservation.getQuantity()));
                anySuccess = true;
                continue;
            }
            if (reservation.getStatus() != ReservationStatus.HELD) {
                log.info("ℹ️ [ScheduleConfirmation] 처리할 상태 아님 - orderId={}, status={}",
                        orderId, reservation.getStatus());
                continue;
            }
            boolean success;
            success = scheduleReservationRepository.completeCapacity(finalScheduleId, finalQty) != null;
            if (!success) {
                reservationService.updateStatus(reservation, ReservationStatus.FAILED,
                        "schedule confirmation failed");
                eventPublisher.publishScheduleConfirmationFailedEvent(orderId, orderNo, popupId,
                        "스케줄 차감 실패");
                releaseHoldWithRetry(orderId, "schedule-confirmation");
                return;
            }
            reservationService.updateStatus(reservation, ReservationStatus.COMMITTED, null);
            details.add(String.format("scheduleId=%s qty=%d", scheduleId, qty));
            anySuccess = true;
        }
        releaseHoldWithRetry(orderId, "schedule-confirmation");
        if (anySuccess) {
            eventPublisher.publishScheduleConfirmationSuccessEvent(orderId, orderNo, popupId, String.join(", ", details));
        } else {
            eventPublisher.publishScheduleConfirmationFailedEvent(orderId, orderNo, popupId,
                    "처리할 예약 없음");
        }
    }

    private void handleScheduleRelease(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        List<Map<String, Object>> sessions = asListOfMaps(envelope.get("releaseSessions"));
        if (sessions.isEmpty()) {
            sessions = asListOfMaps(envelope.get("sessions"));
        }
        boolean anySuccess = false;
        for (Map<String, Object> session : sessions) {
            UUID scheduleId = asUUID(session.get("scheduleId"));
            Integer qty = asInteger(session.get("qty"));
            if (qty == null || qty <= 0) {
                qty = asInteger(session.get("quantity"));
            }
            if (scheduleId == null || qty == null || qty <= 0) {
                continue;
            }
            int finalQty = qty;
            UUID finalScheduleId = scheduleId;
            Optional<GoodsOrderReservation> reservationOpt =
                    reservationService.findLatestScheduleReservation(orderId, finalScheduleId);
            if (reservationOpt.isEmpty()) {
                log.warn("⚠️ [ScheduleRelease] 예약 정보 없음 - orderId={}, scheduleId={}", orderId, finalScheduleId);
                continue;
            }
            GoodsOrderReservation reservation = reservationOpt.get();
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                anySuccess = true;
                log.info("ℹ️ [ScheduleRelease] 이미 릴리즈된 상태 - orderId={}, scheduleId={}", orderId, scheduleId);
                continue;
            }
            boolean success = runWithRetry("schedule-release scheduleId=" + scheduleId,
                    () -> scheduleReservationRepository.cancelCapacity(finalScheduleId, finalQty) != null);
            if (success) {
                reservationService.updateStatus(reservation, ReservationStatus.RELEASED, "schedule-release");
                anySuccess = true;
            } else {
                reservationService.updateStatus(reservation, ReservationStatus.FAILED,
                        "schedule release failed");
            }
        }
        if (anySuccess) {
            releaseHoldWithRetry(orderId, "schedule-release");
            eventPublisher.publishScheduleReleasedEvent(orderId, LocalDateTime.now());
        } else {
            log.error("💀 [ScheduleRelease] 스케줄 릴리즈 실패 - orderId={} (DLQ/알람 필요)", orderId);
        }
    }

    private void handleScheduleReservationCancel(Map<String, Object> envelope) {
        UUID orderId = asUUID(envelope.get("orderId"));
        UUID scheduleId = firstNotNullUUID(envelope.get("scheduleId"), envelope.get("sessionOptionId"));
        Integer qty = asInteger(envelope.get("quantity"));
        if (qty == null || qty <= 0) {
            qty = asInteger(envelope.get("qty"));
        }
        if (orderId == null || scheduleId == null || qty == null || qty <= 0) {
            log.warn("⚠️ [ScheduleReservationCancel] payload 누락 - orderId={}", orderId);
            return;
        }
        int finalQty = qty;
        Optional<GoodsOrderReservation> reservationOpt =
                reservationService.findLatestScheduleReservation(orderId, scheduleId);
        if (reservationOpt.isEmpty()) {
            log.warn("⚠️ [ScheduleReservationCancel] 예약 정보 없음 - orderId={}, scheduleId={}", orderId, scheduleId);
            releaseHoldWithRetry(orderId, "schedule-reservation-cancel");
            return;
        }
        GoodsOrderReservation reservation = reservationOpt.get();
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("ℹ️ [ScheduleReservationCancel] 이미 릴리즈된 상태 - orderId={}, scheduleId={}", orderId, scheduleId);
            releaseHoldWithRetry(orderId, "schedule-reservation-cancel");
            eventPublisher.publishScheduleReleasedEvent(orderId, LocalDateTime.now());
            return;
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            log.info("ℹ️ [ScheduleReservationCancel] 처리할 상태 아님 - orderId={}, status={}",
                    orderId, reservation.getStatus());
            releaseHoldWithRetry(orderId, "schedule-reservation-cancel");
            return;
        }
        boolean success = runWithRetry("schedule-reservation-cancel scheduleId=" + scheduleId,
                () -> scheduleReservationRepository.cancelCapacity(scheduleId, finalQty) != null);
        if (success) {
            reservationService.updateStatus(reservation, ReservationStatus.RELEASED, "schedule-reservation-cancel");
            log.info("✅ [ScheduleReservationCancel] 예약 취소 성공 - orderId={}, scheduleId={}", orderId, scheduleId);
        } else {
            reservationService.updateStatus(reservation, ReservationStatus.FAILED,
                    "schedule reservation cancel failed");
            log.error("💀 [ScheduleReservationCancel] 예약 취소 실패 - orderId={}, scheduleId={}", orderId, scheduleId);
        }
        releaseHoldWithRetry(orderId, "schedule-reservation-cancel");
        if (success) {
            eventPublisher.publishScheduleReleasedEvent(orderId, LocalDateTime.now());
        }
    }

    private Optional<GoodsOrderReservation> getOrCreateGoodsReservation(UUID orderId,
                                                                       String orderNo,
                                                                       UUID popupId,
                                                                       UUID goodsId,
                                                                       int quantity) {
        if (orderId == null || goodsId == null) {
            return Optional.empty();
        }
        Optional<GoodsOrderReservation> existing = reservationService.findLatestGoodsReservation(orderId, goodsId);
        if (existing.isPresent()) {
            return existing;
        }
        if (popupId == null || quantity <= 0) {
            log.warn("⚠️ [GoodsReservation] 생성 불가 - orderId={}, goodsId={}", orderId, goodsId);
            return Optional.empty();
        }
        return Optional.of(reservationService.createGoodsReservation(orderId, orderNo, popupId, goodsId, quantity));
    }

    private Optional<GoodsOrderReservation> getOrCreateScheduleReservation(UUID orderId,
                                                                           String orderNo,
                                                                           UUID popupId,
                                                                           UUID scheduleId,
                                                                           int quantity) {
        if (orderId == null || scheduleId == null) {
            return Optional.empty();
        }
        Optional<GoodsOrderReservation> existing = reservationService.findLatestScheduleReservation(orderId, scheduleId);
        if (existing.isPresent()) {
            return existing;
        }
        if (popupId == null || quantity <= 0) {
            log.warn("⚠️ [ScheduleReservation] 생성 불가 - orderId={}, scheduleId={}", orderId, scheduleId);
            return Optional.empty();
        }
        return Optional.of(reservationService.createScheduleReservation(orderId, orderNo, popupId, scheduleId, quantity));
    }

    private boolean releaseGoodsReservation(GoodsOrderReservation reservation, int quantity, String context) {
        if (reservation == null || reservation.getGoodsId() == null || quantity <= 0) {
            return false;
        }
        UUID popupId = reservation.getPopupId();
        if (popupId == null) {
            log.warn("⚠️ [{}] popupId 누락 - reservation={}", context, reservation.getId());
            return false;
        }
        boolean success = runWithRetry(context + " goodsId=" + reservation.getGoodsId(),
                () -> {
                    goodsService.cancelReservationGoods(popupId, reservation.getGoodsId(), quantity);
                    return true;
                });
        if (success) {
            reservationService.updateStatus(reservation, ReservationStatus.RELEASED, context);
            return true;
        }
        reservationService.updateStatus(reservation, ReservationStatus.FAILED, context + " failed");
        return false;
    }

    private boolean releaseHoldWithRetry(UUID orderId, String context) {
        if (orderId == null) {
            return false;
        }
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                HoldResult result = inventoryHoldService.releaseHold(orderId);
                if (result != null && result.isSuccess()) {
                    log.debug("🧹 [{}] Redis HOLD 해제 성공 - orderId={} attempt={}", context, orderId, attempt);
                    return true;
                }
                log.warn("⚠️ [{}] Redis HOLD 해제 실패 - orderId={} attempt={} detail={}",
                        context, orderId, attempt, result != null ? result.getDetail() : "null");
            } catch (Exception e) {
                log.warn("⚠️ [{}] Redis HOLD 해제 오류 - attempt={}, error={}", context, attempt, e.getMessage());
            }
        }
        log.error("💀 [{}] Redis HOLD 해제 최대 재시도 실패 - orderId={} (DLQ/알람 필요)", context, orderId);
        return false;
    }

    private boolean runWithRetry(String context, BooleanSupplier action) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (action.getAsBoolean()) {
                    return true;
                }
                log.warn("⚠️ [{}] 처리 실패 - attempt={} (retry)", context, attempt);
            } catch (Exception e) {
                log.warn("⚠️ [{}] 처리 중 오류 - attempt={} error={}", context, attempt, e.getMessage());
            }
        }
        log.error("💀 [{}] 최대 재시도 실패 (DLQ/알람 필요)", context);
        return false;
    }

    private boolean hasTerminalReservations(List<GoodsOrderReservation> reservations, ReservationType type) {
        if (reservations == null || reservations.isEmpty()) {
            return false;
        }
        return reservations.stream()
                .filter(r -> r != null && r.getReservationType() == type)
                .anyMatch(r -> r.getStatus() == ReservationStatus.COMMITTED
                        || r.getStatus() == ReservationStatus.FAILED
                        || r.getStatus() == ReservationStatus.RELEASED);
    }

    private boolean hasHeldReservations(List<GoodsOrderReservation> reservations, ReservationType type) {
        if (reservations == null || reservations.isEmpty()) {
            return false;
        }
        return reservations.stream()
                .filter(r -> r != null && r.getReservationType() == type)
                .anyMatch(r -> r.getStatus() == ReservationStatus.HELD);
    }

    private void markHeldReservationsAsFailed(List<GoodsOrderReservation> reservations,
                                              ReservationType type,
                                              String reason) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }
        for (GoodsOrderReservation reservation : reservations) {
            if (reservation == null || reservation.getReservationType() != type) {
                continue;
            }
            if (reservation.getStatus() == ReservationStatus.HELD) {
                reservationService.updateStatus(reservation, ReservationStatus.FAILED, reason);
            }
        }
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                result.add(typed);
            }
        }
        return result;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private UUID asUUID(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID firstNotNullUUID(Object... candidates) {
        for (Object candidate : candidates) {
            UUID uuid = asUUID(candidate);
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private UUID resolvePopupId(UUID orderId,
                                UUID providedPopupId,
                                List<GoodsOrderReservation> existingReservations,
                                List<Map<String, Object>> deductionItems) {
        if (providedPopupId != null) {
            return providedPopupId;
        }
        for (GoodsOrderReservation reservation : existingReservations) {
            if (reservation != null && reservation.getPopupId() != null) {
                return reservation.getPopupId();
            }
        }
        for (Map<String, Object> item : deductionItems) {
            UUID goodsId = firstNotNullUUID(item.get("goodsVariantId"), item.get("goodsId"));
            if (goodsId == null) {
                continue;
            }
            try {
                UUID resolved = goodsService.resolvePopupId(goodsId);
                if (resolved != null) {
                    return resolved;
                }
            } catch (Exception e) {
                log.debug("⚠️ [StockDeduction] popupId lookup 실패 - goodsVariantId={} orderId={} error={}",
                        goodsId, orderId, e.getMessage());
            }
        }
        return null;
    }

    private String resolveOrderNo(List<GoodsOrderReservation> existingReservations, String providedOrderNo) {
        if (StringUtils.hasText(providedOrderNo)) {
            return providedOrderNo;
        }
        for (GoodsOrderReservation reservation : existingReservations) {
            if (reservation == null) {
                continue;
            }
            String candidate = reservation.getOrderNo();
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
