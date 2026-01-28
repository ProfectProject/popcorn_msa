package com.popcorn.store.event.inventory;

import com.popcorn.store.domain.goods.entity.GoodsOrderReservation;
import com.popcorn.store.domain.goods.entity.ReservationStatus;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.popup.service.PopupService;
import com.popcorn.store.domain.popup.service.ScheduleInventoryApiService;
import com.popcorn.store.event.order.OrderPaidEvent;
import com.popcorn.store.event.order.StockReservedEvent;
import com.popcorn.store.event.order.StockReservationFailedEvent;
import com.popcorn.store.event.payment.InventoryConfirmationRequestedEvent;
import com.popcorn.store.inventory.redis.InventoryEventIdempotencyService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 팝업 스케줄 예약은 Redis HOLD를 기준으로 동시성 제어하고,
 * 결제 성공 시에만 DB 스케줄 수용량을 실제로 차감한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PopupScheduleInventorySagaListener {

    private static final String ORDER_PAID_SCHEDULE_SCOPE = "order-paid-schedule";
    private static final String INVENTORY_CONFIRMATION_SCHEDULE_SCOPE = "inventory-confirmation-schedule";
    private static final String SCHEDULE_KEY_FORMAT = "schedule_avail:{%s}:%s";

    private final PopupService popupService;
    private final GoodsOrderReservationService reservationService;
    private final StoreInventoryEventPublisher eventPublisher;
    private final InventoryRedisHoldService inventoryHoldService;
    private final ScheduleInventoryApiService scheduleInventoryApiService;
    private final InventoryEventIdempotencyService idempotencyService;

    /**
     * 코레오그래피 Saga로 전달된 OrderPaidEvent를 Redis Lua HOLD로 먼저 처리하고 예약 로그에 HELD 상태로 기록한다.
     */
    @EventListener
    @Transactional
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("order-paid 예약 처리 비활성화 - orderId: {}, eventId: {}", event.getOrderId(), event.getEventId());
        return;
    }

    /**
     * Payment 서비스의 재고 확정/복구 이벤트에 따라 COMMIT 또는 RELEASE 흐름을 실행한다.
     */
    @EventListener
    @Transactional
    public void handleInventoryConfirmation(InventoryConfirmationRequestedEvent event) {
        if (!shouldProcessInventoryConfirmation(event)) {
            return;
        }

        List<GoodsOrderReservation> reservations =
                reservationService.findByOrderIdAndType(event.getOrderId(), ReservationType.SCHEDULE);
        if (reservations.isEmpty()) {
            log.warn("예약 정보 없음 - inventory event orderId={}", event.getOrderId());
            return;
        }

        UUID orderId = event.getOrderId();
        String orderNo = reservations.stream().findFirst().map(GoodsOrderReservation::getOrderNo).orElse(null);
        UUID popupId = reservations.stream().findFirst().map(GoodsOrderReservation::getPopupId).orElse(null);

        if (event.isConfirmAction()) {
            List<String> details = new ArrayList<>();
            try {
                for (GoodsOrderReservation reservation : reservations) {
                    if (ReservationStatus.HELD != reservation.getStatus()) {
                        continue;
                    }
                    popupService.completePopupScheduleReservation(
                            reservation.getScheduleId(),
                            reservation.getQuantity()
                    );
                    reservationService.updateStatus(reservation, ReservationStatus.COMMITTED, null);
                    details.add(formatDetail(reservation));
                }
                log.info("[SCHEDULE_COMMIT] orderId={} eventId={}", orderId, event.getEventId());
                eventPublisher.publishStockDeductionSuccessEvent(orderId, orderNo, popupId,
                        String.join(", ", details));
            } catch (Exception e) {
                log.error("팝업 스케줄 재고 확정 실패 - orderId={}, error={}", orderId, e.getMessage(), e);
                reservations.stream()
                        .filter(res -> ReservationStatus.HELD == res.getStatus())
                        .forEach(res -> reservationService.updateStatus(res, ReservationStatus.FAILED, e.getMessage()));
                eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                        "재고 차감 실패: " + e.getMessage(), "SYSTEM_ERROR", e.getMessage());
                throw new RuntimeException("팝업 스케줄 재고 확정 실패", e);
            }
        } else if (event.isRestoreAction()) {
            log.info("[SCHEDULE_RELEASE] orderId={}, eventId={}", orderId, event.getEventId());
            try {
                HoldResult releaseResult = inventoryHoldService.releaseHold(orderId);
                if (releaseResult.isAlreadyHeld()) {
                    log.info("[SCHEDULE_RELEASE] Redis HOLD 정보 없음 - orderId={}", orderId);
                } else {
                    log.info("[SCHEDULE_RELEASE] Redis HOLD 데이터 삭제 완료 - orderId={}", orderId);
                }
            } catch (Exception e) {
                log.error("Redis HOLD 복구 중 오류 - orderId={}, error={}", orderId, e.getMessage(), e);
            }
            reservations.stream()
                    .filter(res -> ReservationStatus.HELD == res.getStatus())
                    .forEach(res -> reservationService.updateStatus(res, ReservationStatus.RELEASED, event.getReason()));
            eventPublisher.publishStockDeductionFailedEvent(orderId, orderNo, popupId,
                    "재고 복구 - " + event.getReason(), "PAYMENT_RESTORE",
                    "restore requested by payment event");
        }
    }

    private StockReservedEvent.ReservedStockItem buildReservationRecord(OrderPaidEvent.OrderItemInfo item,
                                                                        Integer remainingCapacity) {
        return StockReservedEvent.ReservedStockItem.builder()
                .scheduleId(item.getSessionId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .reservationCategory(StockReservedEvent.ReservedStockItem.ReservationCategory.SCHEDULE)
                .reservationName(buildScheduleName(item.getSessionId()))
                .reservationDetails(remainingCapacity)
                .build();
    }

    private void rollbackScheduleReservations(UUID orderId, List<GoodsOrderReservation> reservations) {
        if (orderId != null) {
            try {
                inventoryHoldService.releaseHold(orderId);
            } catch (Exception e) {
                log.error("HOLD 복구 실패 - orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }
        for (GoodsOrderReservation reservation : reservations) {
            reservationService.updateStatus(reservation, ReservationStatus.FAILED,
                    "rollback after failure");
        }
    }

    private List<StockReservationFailedEvent.FailedStockItem> buildFailedScheduleItems(
            List<OrderPaidEvent.OrderItemInfo> scheduleItems, String failureReason) {
        List<StockReservationFailedEvent.FailedStockItem> failedItems = new ArrayList<>();
        for (OrderPaidEvent.OrderItemInfo item : scheduleItems) {
            if (!item.isReservationItem() || item.getSessionId() == null || item.getQuantity() == null) {
                continue;
            }
            failedItems.add(StockReservationFailedEvent.FailedStockItem.create(
                    null,
                    item.getQuantity(),
                    0,
                    buildScheduleName(item.getSessionId()),
                    failureReason
            ));
        }
        return failedItems;
    }

    private String buildScheduleName(UUID scheduleId) {
        return "팝업 스케줄 " + scheduleId;
    }

    private String formatDetail(GoodsOrderReservation reservation) {
        return String.format("scheduleId=%s qty=%d", reservation.getScheduleId(), reservation.getQuantity());
    }


    private boolean shouldProcessOrderPaid(OrderPaidEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), ORDER_PAID_SCHEDULE_SCOPE
        );
        if (!registered) {
            log.info("중복 OrderPaidEvent 스케줄 처리 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }

    private boolean shouldProcessInventoryConfirmation(InventoryConfirmationRequestedEvent event) {
        boolean registered = idempotencyService.registerEvent(
                event.getEventId(), event.getOrderId(), INVENTORY_CONFIRMATION_SCHEDULE_SCOPE
        );
        if (!registered) {
            log.info("중복 InventoryConfirmation 이벤트 스케줄 처리 스킵 - orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        }
        return registered;
    }
}
