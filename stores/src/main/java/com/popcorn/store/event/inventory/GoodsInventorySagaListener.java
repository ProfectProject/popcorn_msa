package com.popcorn.store.event.inventory;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.entity.GoodsVariant;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.domain.goods.repository.GoodsVariantRepository;
import com.popcorn.store.event.order.OrderPaidEvent;
import com.popcorn.store.event.order.StockDeductionFailedEvent;
import com.popcorn.store.event.order.StockDeductionSuccessEvent;
import com.popcorn.store.event.order.StockReservedEvent;
import com.popcorn.store.event.order.StockReservationFailedEvent;
import com.popcorn.store.event.payment.InventoryConfirmationRequestedEvent;
import com.popcorn.store.inventory.redis.InventoryEventIdempotencyService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.GoodsHoldItem;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoodsInventorySagaListener {

    private static final String ORDER_PAID_GOODS_SCOPE = "order-paid-goods";
    private static final String INVENTORY_CONFIRMATION_GOODS_SCOPE = "inventory-confirmation-goods";

    private final GoodsService goodsService;
    private final GoodsVariantRepository goodsVariantRepository;
    private final GoodsOrderReservationService reservationService;
    private final StoreInventoryEventPublisher eventPublisher;
    private final InventoryRedisHoldService inventoryHoldService;
    private final InventoryEventIdempotencyService idempotencyService;

    /**
     * OrderPaidEvent가 도착하면 Redis HOLD를 먼저 시도하고 예약 테이블에 HELD 상태로 기록한다.
     * 이미 처리된 eventId/주문이면 중복 처리를 막고, Redis HOLD 실패 시 기존 HELD를 rollback한 뒤 실패 이벤트를 발행한다.
     */
    @EventListener
    @Transactional
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("order-paid 예약 처리 비활성화 - orderId: {}, eventId: {}", event.getOrderId(), event.getEventId());
        return;
    }

    private void initializeGoodsAvailabilityKeys(UUID popupId, List<OrderPaidEvent.OrderItemInfo> goodsItems) {
        for (OrderPaidEvent.OrderItemInfo item : goodsItems) {
            if (item.getGoodsId() == null) {
                continue;
            }
            GoodsVariant variant = goodsVariantRepository.findById(item.getGoodsId()).orElse(null);
            if (variant == null) {
                continue;
            }
            int available = Math.max(0, variant.getStock() - variant.getReservationStock());
            inventoryHoldService.ensureGoodsAvailabilityKey(popupId, item.getGoodsId(), available);
        }
    }

    private void handleReservationFailure(OrderPaidEvent event,
                                          List<OrderPaidEvent.OrderItemInfo> goodsItems,
                                          List<GoodsOrderReservation> createdReservations,
                                          String reason) {
        log.error("재고 예약 실패 - orderId: {}, error: {}", event.getOrderId(), reason);
        inventoryHoldService.releaseHold(event.getOrderId());
        createdReservations.forEach(reservation -> reservationService.updateStatus(
                reservation, ReservationStatus.FAILED, reason));

        List<StockReservationFailedEvent.FailedStockItem> failedItems = buildFailedItems(goodsItems, reason);
        eventPublisher.publishStockReservationFailedEvent(event, failedItems, reason);
    }

    /**
     * Payment로부터 재고 COMMIT/RELEASE 요청을 받으면 HELD 상태를 기준으로 DB 차감 또는 Redis RELEASE를 수행한다.
     */
    @EventListener
    @Transactional
    public void handleInventoryConfirmation(InventoryConfirmationRequestedEvent event) {
        if (!shouldProcessInventoryConfirmation(event)) {
            return;
        }
        List<GoodsOrderReservation> reservations = reservationService.findByOrderIdAndType(event.getOrderId(), ReservationType.GOODS);
        if (reservations.isEmpty()) {
            log.warn("예약 정보 없음 - inventory event: {}", event.getOrderId());
            return;
        }

        UUID orderId = event.getOrderId();
        String orderNo = reservations.stream()
                .findFirst()
                .map(GoodsOrderReservation::getOrderNo)
                .orElse(null);
        UUID popupId = reservations.stream()
                .findFirst()
                .map(GoodsOrderReservation::getPopupId)
                .orElse(null);

        if (event.isConfirmAction()) {
            List<String> details = new ArrayList<>();
            try {
                for (GoodsOrderReservation reservation : reservations) {
                    if (ReservationStatus.HELD != reservation.getStatus()) {
                        continue;
                    }
                    goodsService.completeReservationGoods(reservation.getPopupId(),
                            reservation.getGoodsId(), reservation.getQuantity());
                    reservationService.updateStatus(reservation, ReservationStatus.COMMITTED, null);
                    details.add(formatDetail(reservation));
                }
                eventPublisher.publishStockDeductionSuccessEvent(orderId, orderNo, popupId, String.join(", ", details));
            } catch (Exception e) {
                log.error("재고 차감 확정 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
                reservations.stream()
                        .filter(res -> ReservationStatus.HELD == res.getStatus())
                        .forEach(res -> reservationService.updateStatus(res, ReservationStatus.FAILED, e.getMessage()));
                eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                        "재고 차감 실패: " + e.getMessage(), "SYSTEM_ERROR", e.getMessage());
                throw new RuntimeException("재고 차감 확정 처리 실패", e);
            }
        } else if (event.isRestoreAction()) {
            // 결제 실패/만료 시 HOLD된 수량을 Redis에서 복구하고 예약 상태를 RELEASED로 갱신
            inventoryHoldService.releaseHold(orderId);
            reservations.stream()
                    .filter(res -> ReservationStatus.HELD == res.getStatus())
                    .forEach(res -> reservationService.updateStatus(res, ReservationStatus.RELEASED, event.getReason()));
            eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                    "재고 복구 - " + event.getReason(), "PAYMENT_RESTORE",
                    "restore requested by payment event");
        }
    }

    private List<StockReservationFailedEvent.FailedStockItem> buildFailedItems(
            List<OrderPaidEvent.OrderItemInfo> goodsItems, String failureReason) {
        List<StockReservationFailedEvent.FailedStockItem> failedItems = new ArrayList<>();
        for (OrderPaidEvent.OrderItemInfo item : goodsItems) {
            if (item.getGoodsId() == null || item.getQuantity() == null) {
                continue;
            }
            failedItems.add(StockReservationFailedEvent.FailedStockItem.create(
                    item.getGoodsId(),
                    item.getQuantity(),
                    0,
                    resolveProductName(item),
                    failureReason));
        }
        return failedItems;
    }

    private String resolveProductName(OrderPaidEvent.OrderItemInfo item) {
        return "굿즈 상품";
    }

    private String formatDetail(GoodsOrderReservation reservation) {
        return String.format("goodsId=%s qty=%d", reservation.getGoodsId(), reservation.getQuantity());
    }

    private UUID resolvePopupIdForGoods(OrderPaidEvent event, List<GoodsHoldItem> goodsHoldItems) {
        UUID popupId = event.getPopupId();
        if (popupId != null) {
            return popupId;
        }
        if (!goodsHoldItems.isEmpty()) {
            return goodsService.resolvePopupId(goodsHoldItems.get(0).getGoodsId());
        }
        throw new IllegalArgumentException("팝업 정보 또는 굿즈 ID가 필요합니다.");
    }

    private List<GoodsHoldItem> buildGoodsHoldItems(List<OrderPaidEvent.OrderItemInfo> goodsItems) {
        Map<UUID, Integer> aggregated = new LinkedHashMap<>();
        for (OrderPaidEvent.OrderItemInfo item : goodsItems) {
            if (item.getGoodsId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            aggregated.merge(item.getGoodsId(), item.getQuantity(), Integer::sum);
        }
        return aggregated.entrySet().stream()
                .map(entry -> new GoodsHoldItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private boolean shouldProcessOrderPaid(OrderPaidEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), ORDER_PAID_GOODS_SCOPE
        );
        if (!registered) {
            log.info("중복 OrderPaidEvent 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }

    private boolean shouldProcessInventoryConfirmation(InventoryConfirmationRequestedEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), INVENTORY_CONFIRMATION_GOODS_SCOPE
        );
        if (!registered) {
            log.info("중복 InventoryConfirmation 이벤트 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }
}
