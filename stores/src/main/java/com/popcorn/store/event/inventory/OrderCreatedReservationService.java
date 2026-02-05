package com.popcorn.store.event.inventory;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.event.kafka.KafkaPublisher;
import com.popcorn.store.event.order.OrderCreatedEvent;
import com.popcorn.store.event.standard.EventLineItem;
import com.popcorn.store.inventory.redis.InventoryEventIdempotencyService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.GoodsHoldItem;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import com.popcorn.store.event.StoreRedisEventPublisher;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ORDER_CREATED 이벤트를 직접 처리하여
 * - Redis/DB 기반 굿즈 HOLD 생성
 * - 예약 결과(Store 이벤트) 전파
 * - TTL 만료시 예약 만료 이벤트 발행
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedReservationService {

    private static final String ORDER_CREATED_SCOPE = "order-created-goods";
    private static final String RESERVATION_TYPE_GOODS = "GOODS";

    private final InventoryEventIdempotencyService idempotencyService;
    private final InventoryRedisHoldService inventoryHoldService;
    private final GoodsOrderReservationService reservationService;
    private final GoodsService goodsService;
    private final StoreRedisEventPublisher redisEventPublisher;
    private final KafkaPublisher kafkaPublisher;
    private final TaskScheduler taskScheduler;

    public void reserveForOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("ORDER_CREATED 이벤트 필수 정보 누락");
            return;
        }
        if (!Boolean.TRUE.equals(event.getHasGoods())) {
            log.debug("ORDER_CREATED에 goods 항목 없음 - orderId={}", event.getOrderId());
            return;
        }
        if (!idempotencyService.registerEvent(event.getEventId(), event.getOrderId(), ORDER_CREATED_SCOPE)) {
            log.info("중복 ORDER_CREATED 이벤트 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
            return;
        }

        List<EventLineItem> goodsLines = filterGoodsItems(event.getLines());
        if (goodsLines.isEmpty()) {
            log.debug("goods 항목 리스트 비어있음 - orderId={}", event.getOrderId());
            return;
        }

        UUID popupId = resolvePopupId(event, goodsLines);
        if (popupId == null) {
            log.warn("팝업 정보 확보 실패 - orderId={}", event.getOrderId());
            return;
        }

        Map<UUID, Integer> aggregated = aggregateQuantities(goodsLines);
        if (aggregated.isEmpty()) {
            log.debug("goods 항목 수량 없음 - orderId={}", event.getOrderId());
            return;
        }

        List<GoodsHoldItem> holdItems = aggregated.entrySet().stream()
                .map(entry -> new GoodsHoldItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        initializeAvailabilityKeys(popupId, holdItems);
        try {
            HoldResult holdResult = inventoryHoldService.holdGoods(
                    event.getOrderId(), popupId, holdItems
            );
            if (!holdResult.isSuccess()) {
                handleHoldFailure(event, aggregated, popupId, holdResult.getDetail());
                return;
            }

            handleHoldSuccess(event, popupId, aggregated, holdItems);

        } catch (Exception e) {
            log.error("ORDER_CREATED 재고 HOLD 실패 - orderId={} error={}", event.getOrderId(), e.getMessage(), e);
            handleHoldFailure(event, aggregated, popupId, e.getMessage());
        }
    }

    private void handleHoldSuccess(OrderCreatedEvent event,
                                   UUID popupId,
                                   Map<UUID, Integer> aggregated,
                                   List<GoodsHoldItem> holdItems) {

        LocalDateTime expiresAt = LocalDateTime.now().plus(inventoryHoldService.getHoldDuration());
        List<GoodsOrderReservation> reservations = new ArrayList<>();

        for (GoodsHoldItem item : holdItems) {
            GoodsOrderReservation reservation = reservationService.createGoodsReservation(
                    event.getOrderId(), event.getOrderNo(), popupId, item.getGoodsId(), item.getQuantity()
            );
            reservations.add(reservation);

            redisEventPublisher.publishGoodsReservedEvent(
                    event.getOrderId(),
                    event.getOrderNo(),
                    popupId,
                    item.getGoodsId(),
                    item.getQuantity()
            );

            kafkaPublisher.publishGoodsReservationSucceeded(
                    event.getOrderId(),
                    item.getGoodsId(),
                    item.getQuantity(),
                    expiresAt
            );
        }

        scheduleExpiration(event.getOrderId(), popupId, reservations);
    }

    private void handleHoldFailure(OrderCreatedEvent event,
                                   Map<UUID, Integer> aggregated,
                                   UUID popupId,
                                   String reason) {
        inventoryHoldService.releaseHold(event.getOrderId());

        aggregated.forEach((goodsId, qty) -> {
            redisEventPublisher.publishGoodsReservationFailedEvent(
                    event.getOrderId(),
                    popupId,
                    goodsId,
                    qty,
                    0,
                    reason
            );
            kafkaPublisher.publishGoodsReservationFailed(event.getOrderId(), goodsId, reason);
        });
    }

    private void scheduleExpiration(UUID orderId, UUID popupId, List<GoodsOrderReservation> reservations) {
        Duration ttl = inventoryHoldService.getHoldDuration();
        if (ttl == null || ttl.isZero() || reservations.isEmpty()) {
            return;
        }

        List<String> reservationIds = reservations.stream()
                .map(res -> res.getId().toString())
                .toList();

        taskScheduler.schedule(
                () -> publishExpiration(orderId, popupId, reservationIds),
                Instant.now().plus(ttl)
        );
    }

    private void publishExpiration(UUID orderId, UUID popupId, List<String> reservationIds) {
        List<GoodsOrderReservation> heldReservations = reservationService.findByOrderIdAndType(orderId, ReservationType.GOODS).stream()
                .filter(reservation -> ReservationStatus.HELD == reservation.getStatus())
                .collect(Collectors.toList());

        if (heldReservations.isEmpty()) {
            log.debug("만료 처리 대상 없음 - orderId={}", orderId);
            return;
        }

        inventoryHoldService.releaseHold(orderId);
        heldReservations.forEach(res -> reservationService.updateStatus(res, ReservationStatus.RELEASED, "TTL expired"));

        kafkaPublisher.publishReservationExpired(orderId, popupId, RESERVATION_TYPE_GOODS, reservationIds, LocalDateTime.now());
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
}
