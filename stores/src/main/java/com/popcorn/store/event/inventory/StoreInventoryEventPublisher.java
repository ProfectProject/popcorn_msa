package com.popcorn.store.event.inventory;

import com.popcorn.store.event.order.*;
import com.popcorn.store.event.StoreRedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreInventoryEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final StoreRedisEventPublisher redisEventPublisher;

    public void publishStockReservedEvent(OrderPaidEvent event,
                                          List<StockReservedEvent.ReservedStockItem> reservedItems) {
        if (reservedItems == null || reservedItems.isEmpty()) {
            return;
        }
        StockReservedEvent stockReservedEvent = StockReservedEvent.create(
                event.getOrderId(),
                event.getOrderNo(),
                event.getPopupId(),
                event.getCustomerId(),
                reservedItems
        );
        log.info("📦 [STORES] 재고 예약 성공 이벤트 발행 - orderId={}, items={}", event.getOrderId(), reservedItems.size());

        // 1. Spring Events로 내부 발행
        applicationEventPublisher.publishEvent(stockReservedEvent);

        // 2. Redis로 굿즈 예약 성공 이벤트 발행 (각 항목별로)
        for (StockReservedEvent.ReservedStockItem item : reservedItems) {
            if (item.getGoodsId() == null || item.getQuantity() == null) {
                continue;
            }
            redisEventPublisher.publishGoodsReservedEvent(
                    event.getOrderId(),
                    event.getOrderNo(),
                    event.getPopupId(),
                    item.getGoodsId(),
                    item.getQuantity()
            );
        }
    }

    public void publishStockReservationFailedEvent(OrderPaidEvent event,
                                                   List<StockReservationFailedEvent.FailedStockItem> failedItems,
                                                   String failureReason) {
        if (failedItems == null || failedItems.isEmpty()) {
            return;
        }
        StockReservationFailedEvent failedEvent = StockReservationFailedEvent.create(
                event.getOrderId(),
                event.getOrderNo(),
                event.getPopupId(),
                event.getCustomerId(),
                failedItems,
                failureReason
        );
        log.warn("재고 예약 실패 이벤트 발행 - orderId={}, reason={}", event.getOrderId(), failureReason);
        applicationEventPublisher.publishEvent(failedEvent);
    }

    public void publishStockDeductionSuccessEvent(UUID orderId, String orderNo, UUID popupId, String stockDetails) {
        StockDeductionSuccessEvent event = StockDeductionSuccessEvent.create(
                orderId,
                orderNo,
                popupId,
                stockDetails
        );
        log.info("📦 [STORES] 재고 차감 성공 이벤트 발행 - orderId={}", orderId);

        // 1. Spring Events로 내부 발행
        applicationEventPublisher.publishEvent(event);

        // 2. Redis로 외부 마이크로서비스들에게 발행
        redisEventPublisher.publishStockDeductionSuccessEvent(event);
    }

    public void publishStockDeductionFailedEvent(UUID orderId, String orderNo, UUID popupId,
                                                 String reason, String failureCode, String details) {
        StockDeductionFailedEvent event = StockDeductionFailedEvent.create(
                orderId,
                orderNo,
                popupId,
                reason,
                failureCode,
                details
        );
        log.warn("⚠️ [STORES] 재고 차감 실패 이벤트 발행 - orderId={}, reason={}", orderId, reason);

        // 1. Spring Events로 내부 발행
        applicationEventPublisher.publishEvent(event);

        // 2. Redis로 외부 마이크로서비스들에게 발행
        redisEventPublisher.publishStockDeductionFailedEvent(event);
    }
}
