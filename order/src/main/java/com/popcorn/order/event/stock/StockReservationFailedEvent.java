package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 재고 예약 실패 이벤트
 *
 * [발행 시점]
 * - Order 모듈에서 Store 모듈에 재고 예약 요청 실패 시
 *
 * [수신자]
 * - Notification 모듈: 재고 부족 알림
 * - Analytics 모듈: 재고 부족 통계
 */
@Getter
public class StockReservationFailedEvent extends BaseOrderEvent {

    /** 팝업 ID */
    private final UUID popupId;

    /** 실패한 재고 항목들 */
    private final List<FailedStockItem> failedItems;

    /** 실패 이유 */
    private final String failureReason;

    /** 이벤트 발생 시간 */
    private final LocalDateTime eventTime;

    private StockReservationFailedEvent(UUID orderId, UUID popupId, List<FailedStockItem> failedItems,
                                      String failureReason, LocalDateTime eventTime, Long userId) {
        super(orderId, "stock-reservation-failed", userId);
        this.popupId = popupId;
        this.failedItems = failedItems;
        this.failureReason = failureReason;
        this.eventTime = eventTime;
    }

    /**
     * 재고 예약 실패 이벤트 생성 팩토리 메서드
     */
    public static StockReservationFailedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                   Long customerId, List<FailedStockItem> failedItems,
                                                   String failureReason) {
        return new StockReservationFailedEvent(orderId, popupId, failedItems, failureReason,
                                             LocalDateTime.now(), customerId);
    }

    /**
     * 실패한 재고 항목 정보
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class FailedStockItem {
        /** 굿즈 변형 ID */
        private UUID goodsId;

        /** 요청 수량 */
        private Integer requestedQuantity;

        /** 사용 가능한 수량 */
        private Integer availableQuantity;

        /** 상품명 */
        private String productName;

        /** 실패 이유 */
        private String failureReason;

        public static FailedStockItem create(UUID goodsId, Integer requestedQuantity,
                                           Integer availableQuantity, String productName,
                                           String failureReason) {
            return FailedStockItem.builder()
                    .goodsId(goodsId)
                    .requestedQuantity(requestedQuantity)
                    .availableQuantity(availableQuantity)
                    .productName(productName)
                    .failureReason(failureReason)
                    .build();
        }
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("popupId", popupId.toString());
        payload.put("failureReason", failureReason);
        payload.put("eventTime", eventTime.toString());
        payload.put("failedItemCount", failedItems != null ? failedItems.size() : 0);
        payload.put("totalRequestedQuantity", getTotalRequestedQuantity());
        payload.put("totalAvailableQuantity", getTotalAvailableQuantity());
        return payload;
    }

    /**
     * 총 요청 수량 계산
     */
    public int getTotalRequestedQuantity() {
        return failedItems != null ?
            failedItems.stream().mapToInt(FailedStockItem::getRequestedQuantity).sum() : 0;
    }

    /**
     * 총 가용 수량 계산
     */
    public int getTotalAvailableQuantity() {
        return failedItems != null ?
            failedItems.stream().mapToInt(FailedStockItem::getAvailableQuantity).sum() : 0;
    }
}