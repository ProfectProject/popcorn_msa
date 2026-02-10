package com.popcorn.store.event.inventory;

import com.popcorn.store.event.order.*;
import com.popcorn.store.event.kafka.KafkaPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreInventoryEventPublisher {

    private final KafkaPublisher kafkaPublisher;
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

        // 2. Kafka로 굿즈 예약 성공 이벤트 전파
        LocalDateTime reservedExpiresAt = LocalDateTime.now().plusMinutes(10);
        for (StockReservedEvent.ReservedStockItem item : reservedItems) {
            if (item.getGoodsId() == null || item.getQuantity() == null) {
                continue;
            }
            kafkaPublisher.publishGoodsReservationSucceeded(
                    event.getOrderId(),
                    item.getGoodsId(),
                    item.getQuantity(),
                    reservedExpiresAt
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
        // 실패 항목별로 Kafka store-events에도 실패 정보를 전송
        for (StockReservationFailedEvent.FailedStockItem item : failedItems) {
            if (item.getGoodsId() == null) {
                continue;
            }
            String detailReason = item.getFailureReason() != null ? item.getFailureReason() : failureReason;
            kafkaPublisher.publishGoodsReservationFailed(event.getOrderId(), item.getGoodsId(), detailReason);
        }
    }

    public void publishStockDeductionSuccessEvent(UUID orderId, String orderNo, UUID popupId, String stockDetails) {
        StockDeductionSuccessEvent event = StockDeductionSuccessEvent.create(
                orderId,
                orderNo,
                popupId,
                stockDetails
        );
        log.info("📦 [STORES] 재고 차감 성공 이벤트 발행 - orderId={}", orderId);

        // 2. Kafka로 store-events에 전파
        kafkaPublisher.publishStockDeductionSucceeded(orderId, event.getStockDetails(), event.getSucceededAt());
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

        // 2. Kafka로 store-events에 전파
        kafkaPublisher.publishStockDeductionFailed(orderId, event.getReason(),
                isRetryable(event.getFailureCode()), event.getFailedAt());
    }

    public void publishScheduleConfirmationSuccessEvent(UUID orderId, String orderNo,
                                                       UUID popupId, String details) {
        log.info("📅 [STORES] 스케줄 확정 성공 이벤트 발행 - orderId={}", orderId);
        kafkaPublisher.publishScheduleConfirmationSucceeded(orderId, details, LocalDateTime.now());
    }

    public void publishScheduleConfirmationFailedEvent(UUID orderId, String orderNo,
                                                      UUID popupId, String reason) {
        log.warn("📅 [STORES] 스케줄 확정 실패 이벤트 발행 - orderId={}, reason={}", orderId, reason);
        kafkaPublisher.publishScheduleConfirmationFailed(orderId, reason, true, LocalDateTime.now());
    }

    public void publishStockReleasedEvent(UUID orderId, LocalDateTime releasedAt) {
        log.info("♻️ [STORES] 재고 릴리즈 이벤트 발행 - orderId={}", orderId);
        kafkaPublisher.publishStockReleased(orderId, releasedAt);
    }

    public void publishScheduleReleasedEvent(UUID orderId, LocalDateTime releasedAt) {
        log.info("♻️ [STORES] 스케줄 릴리즈 이벤트 발행 - orderId={}", orderId);
        kafkaPublisher.publishScheduleReleased(orderId, releasedAt);
    }

    private boolean isRetryable(String failureCode) {
        return failureCode == null || !"INSUFFICIENT_STOCK".equals(failureCode);
    }
}
