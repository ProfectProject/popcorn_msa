package com.popcorn.store.event.inventory;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.domain.popup.service.ScheduleInventoryApiService;
import com.popcorn.store.event.kafka.KafkaPublisher;
import com.popcorn.store.event.order.OrderCreatedEvent;
import com.popcorn.store.event.standard.EventLineItem;
import com.popcorn.store.inventory.redis.InventoryEventIdempotencyService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.GoodsHoldItem;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ORDER_CREATED 이벤트를 직접 처리하여
 * - Redis 기반 goods/schedule HOLD 생성
 * - Kafka 기반 예약 결과 전파
 * - TTL 만료시 reservation-expired 이벤트 발행
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedReservationService {

    private static final String ORDER_CREATED_SCOPE = "order-created-goods";
    private static final String ORDER_CREATED_SCHEDULE_SCOPE = "order-created-schedule";
    private static final String RESERVATION_TYPE_GOODS = "GOODS";
    private static final String RESERVATION_TYPE_SCHEDULE = "SCHEDULE";

    private final InventoryEventIdempotencyService idempotencyService;
    private final InventoryRedisHoldService inventoryHoldService;
    private final GoodsOrderReservationService reservationService;
    private final GoodsService goodsService;
    private final ScheduleInventoryApiService scheduleInventoryApiService;
    private final KafkaPublisher kafkaPublisher;
    private final TaskScheduler taskScheduler;

    public void reserveForOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("ORDER_CREATED 이벤트 필수 정보 누락");
            return;
        }

        List<EventLineItem> goodsLines = filterGoodsItems(event.getLines());
        List<EventLineItem> scheduleLines = filterScheduleItems(event.getLines());
        boolean hasGoods = !goodsLines.isEmpty();
        boolean hasSchedule = !scheduleLines.isEmpty();

        if (!hasGoods && !hasSchedule) {
            log.debug("ORDER_CREATED에 goods/schedule 항목 없음 - orderId={}", event.getOrderId());
            return;
        }

        UUID popupId = event.getPopupId();
        if (popupId == null && hasGoods) {
            popupId = resolvePopupId(event, goodsLines);
        }

        if (hasGoods) {
            handleGoodsOnly(event, popupId, goodsLines);
        }
        if (hasSchedule) {
            handleScheduleOnly(event, popupId, scheduleLines);
        }
    }

    private void handleGoodsOnly(OrderCreatedEvent event, UUID popupId, List<EventLineItem> goodsLines) {
        if (!registerGoodsEvent(event)) {
            return;
        }
        if (popupId == null) {
            log.warn("ORDER_CREATED goods 처리 중 팝업 정보 누락 - orderId={}", event.getOrderId());
            return;
        }

        log.info("ORDER_CREATED goods 처리 시작 - orderId={}, popupId={}, lines={}", event.getOrderId(), popupId, goodsLines.size());

        Map<UUID, Integer> aggregated = aggregateQuantities(goodsLines);
        if (aggregated.isEmpty()) {
            log.debug("goods 항목 수량 없음 - orderId={}", event.getOrderId());
            return;
        }

        Map<UUID, Integer> deduped = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : aggregated.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            UUID goodsId = entry.getKey();
            Optional<GoodsOrderReservation> existing = reservationService.findExistingGoodsReservation(
                    event.getOrderId(), popupId, goodsId);
            if (existing.isPresent()) {
                log.info("중복 GOODS 예약 발견(사전) - orderId={}, popupId={}, goodsId={}",
                        event.getOrderId(), popupId, goodsId);
                continue;
            }
            deduped.put(goodsId, entry.getValue());
        }
        if (deduped.isEmpty()) {
            log.info("모든 goods 항목이 이미 예약됨 - orderId={}", event.getOrderId());
            return;
        }

        List<GoodsHoldItem> holdItems = deduped.entrySet().stream()
                .map(entry -> new GoodsHoldItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        initializeAvailabilityKeys(popupId, holdItems);

        try {
            HoldResult holdResult = inventoryHoldService.holdGoods(event.getOrderId(), popupId, holdItems);
            if (!holdResult.isSuccess()) {
                handleHoldFailure(event, deduped, popupId, holdResult.getDetail());
                return;
            }
            handleHoldSuccess(event, popupId, holdItems);
        } catch (Exception e) {
            log.error("ORDER_CREATED goods HOLD 실패 - orderId={} error={}", event.getOrderId(), e.getMessage(), e);
            handleHoldFailure(event, deduped, popupId, e.getMessage());
        }
    }

    private void handleScheduleOnly(OrderCreatedEvent event, UUID popupId, List<EventLineItem> scheduleLines) {
        if (!registerScheduleEvent(event)) {
            return;
        }
        if (popupId == null) {
            log.warn("ORDER_CREATED schedule 처리 중 팝업 정보 누락 - orderId={}", event.getOrderId());
            return;
        }
        if (scheduleLines.isEmpty()) {
            log.debug("schedule 항목 없음 - orderId={}", event.getOrderId());
            return;
        }
        EventLineItem scheduleLine = scheduleLines.get(0);
        if (scheduleLine == null || scheduleLine.getScheduleId() == null || scheduleLine.getQty() == null
                || scheduleLine.getQty() <= 0) {
            log.warn("ORDER_CREATED schedule 정보 유효하지 않음 - orderId={}", event.getOrderId());
            return;
        }

        UUID scheduleId = scheduleLine.getScheduleId();
        int quantity = scheduleLine.getQty();
        scheduleInventoryApiService.ensureScheduleKey(popupId, scheduleId);

        Optional<GoodsOrderReservation> existingScheduleReservation =
                reservationService.findExistingScheduleReservation(event.getOrderId(), popupId, scheduleId);
        if (existingScheduleReservation.isPresent()) {
            log.info("중복 SCHEDULE 예약 발견(사전) - orderId={}, popupId={}, scheduleId={}",
                    event.getOrderId(), popupId, scheduleId);
            return;
        }

        HoldResult holdResult;
        try {
            holdResult = inventoryHoldService.holdSchedule(event.getOrderId(), popupId, scheduleId, quantity);
        } catch (Exception e) {
            log.error("ORDER_CREATED schedule HOLD 실패 - orderId={} error={}", event.getOrderId(), e.getMessage(), e);
            publishScheduleReservationFailure(event.getOrderId(), scheduleId, safeReason(e));
            return;
        }

        if (!holdResult.isSuccess()) {
            publishScheduleReservationFailure(event.getOrderId(), scheduleId, holdResult.getDetail());
            return;
        }

        GoodsOrderReservation reservation = null;
        try {
            reservation = reservationService.createScheduleReservation(
                    event.getOrderId(), event.getOrderNo(), popupId, scheduleId, quantity
            );

            LocalDateTime expiresAt = LocalDateTime.now().plus(inventoryHoldService.getHoldDuration());
            kafkaPublisher.publishScheduleReservationSucceeded(
                    event.getOrderId(),
                    scheduleId,
                    quantity,
                    expiresAt
            );

            scheduleExpiration(
                    event.getOrderId(),
                    popupId,
                    List.of(reservation),
                    ReservationType.SCHEDULE,
                    RESERVATION_TYPE_SCHEDULE
            );
        } catch (Exception e) {
            log.error("ORDER_CREATED schedule 이벤트 처리 실패 - orderId={} error={}", event.getOrderId(), e.getMessage(), e);
            inventoryHoldService.releaseHold(event.getOrderId());
            if (reservation != null) {
                reservationService.updateStatus(reservation, ReservationStatus.FAILED, e.getMessage());
            }
            publishScheduleReservationFailure(event.getOrderId(), scheduleId, safeReason(e));
        }
    }

    private void handleHoldSuccess(OrderCreatedEvent event,
                                   UUID popupId,
                                   List<GoodsHoldItem> holdItems) {

        LocalDateTime expiresAt = LocalDateTime.now().plus(inventoryHoldService.getHoldDuration());
        List<GoodsOrderReservation> reservations = new ArrayList<>();

        for (GoodsHoldItem item : holdItems) {
            log.debug("Goods hold success - orderId={}, goodsId={}, qty={}", event.getOrderId(), item.getGoodsId(), item.getQuantity());
            GoodsOrderReservation reservation = reservationService.createGoodsReservation(
                    event.getOrderId(), event.getOrderNo(), popupId, item.getGoodsId(), item.getQuantity()
            );
            reservations.add(reservation);

            log.info("Goods reservation created - orderId={}, goodsId={}, reservationId={}",
                    event.getOrderId(), item.getGoodsId(), reservation.getId());

            kafkaPublisher.publishGoodsReservationSucceeded(
                    event.getOrderId(),
                    item.getGoodsId(),
                    item.getQuantity(),
                    expiresAt
            );
            log.info("Goods reservation event published - orderId={}, goodsId={}", event.getOrderId(), item.getGoodsId());
        }

        scheduleExpiration(event.getOrderId(), popupId, reservations, ReservationType.GOODS, RESERVATION_TYPE_GOODS);
    }

    private void handleHoldFailure(OrderCreatedEvent event,
                                   Map<UUID, Integer> aggregated,
                                   UUID popupId,
                                   String reason) {
        inventoryHoldService.releaseHold(event.getOrderId());
        aggregated.forEach((goodsId, qty) ->
                kafkaPublisher.publishGoodsReservationFailed(event.getOrderId(), goodsId, safeReason(reason))
        );
    }

    private void scheduleExpiration(UUID orderId,
                                    UUID popupId,
                                    List<GoodsOrderReservation> reservations,
                                    ReservationType reservationType,
                                    String reservationTypeName) {
        Duration ttl = inventoryHoldService.getHoldDuration();
        if (ttl == null || ttl.isZero() || reservations.isEmpty()) {
            return;
        }

        List<String> reservationIds = reservations.stream()
                .map(res -> res.getId().toString())
                .collect(Collectors.toList());

        taskScheduler.schedule(
                () -> publishExpiration(orderId, popupId, reservationIds, reservationType, reservationTypeName),
                Instant.now().plus(ttl)
        );
    }

    private void publishExpiration(UUID orderId,
                                   UUID popupId,
                                   List<String> reservationIds,
                                   ReservationType reservationType,
                                   String reservationTypeName) {
        List<GoodsOrderReservation> heldReservations = reservationService.findByOrderIdAndType(orderId, reservationType).stream()
                .filter(reservation -> ReservationStatus.HELD == reservation.getStatus())
                .collect(Collectors.toList());

        if (heldReservations.isEmpty()) {
            log.debug("만료 처리 대상 없음 - orderId={}", orderId);
            return;
        }

        inventoryHoldService.releaseHold(orderId);
        heldReservations.forEach(res -> reservationService.updateStatus(res, ReservationStatus.RELEASED, "TTL expired"));

        kafkaPublisher.publishReservationExpired(orderId, popupId, reservationTypeName, reservationIds, LocalDateTime.now());
    }

    private List<EventLineItem> filterGoodsItems(List<EventLineItem> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .filter(line -> line != null && RESERVATION_TYPE_GOODS.equalsIgnoreCase(line.getItemType()))
                .filter(line -> line.getGoodsId() != null && line.getQty() != null && line.getQty() > 0)
                .collect(Collectors.toList());
    }

    private List<EventLineItem> filterScheduleItems(List<EventLineItem> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .filter(line -> line != null && RESERVATION_TYPE_SCHEDULE.equalsIgnoreCase(line.getItemType()))
                .filter(line -> line.getScheduleId() != null && line.getQty() != null && line.getQty() > 0)
                .collect(Collectors.toList());
    }

    private void initializeAvailabilityKeys(UUID popupId, List<GoodsHoldItem> holdItems) {
        for (GoodsHoldItem item : holdItems) {
            try {
                int available = goodsService.calculateAvailableStock(item.getGoodsId());
                inventoryHoldService.ensureGoodsAvailabilityKey(popupId, item.getGoodsId(), available);
            } catch (Exception e) {
                log.warn("재고 키 초기화 실패 - goodsId={}, error={}", item.getGoodsId(), e.getMessage());
            }
        }
    }

    private UUID resolvePopupId(OrderCreatedEvent event, List<EventLineItem> goodsItems) {
        if (event.getPopupId() != null) {
            return event.getPopupId();
        }
        for (EventLineItem line : goodsItems) {
            try {
                UUID popupId = goodsService.resolvePopupId(line.getGoodsId());
                if (popupId != null) {
                    return popupId;
                }
            } catch (Exception e) {
                log.warn("goods 기반 팝업 조회 실패 - goodsId={} error={}", line.getGoodsId(), e.getMessage());
            }
        }
        return null;
    }

    private Map<UUID, Integer> aggregateQuantities(List<EventLineItem> goodsLines) {
        Map<UUID, Integer> aggregated = new LinkedHashMap<>();
        for (EventLineItem line : goodsLines) {
            aggregated.merge(line.getGoodsId(), line.getQty(), Integer::sum);
        }
        return aggregated;
    }

    private boolean registerGoodsEvent(OrderCreatedEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), ORDER_CREATED_SCOPE);
        if (!registered) {
            log.info("중복 ORDER_CREATED goods 이벤트 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }

    private boolean registerScheduleEvent(OrderCreatedEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), ORDER_CREATED_SCHEDULE_SCOPE);
        if (!registered) {
            log.info("중복 ORDER_CREATED schedule 이벤트 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }

    private void publishScheduleReservationFailure(UUID orderId, UUID scheduleId, String reason) {
        if (scheduleId == null) {
            return;
        }
        kafkaPublisher.publishScheduleReservationFailed(orderId, scheduleId, safeReason(reason));
    }

    private String safeReason(Object value) {
        if (value == null) {
            return "unknown";
        }
        return value.toString();
    }
}
