package com.popcorn.order.event.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class StockReservationFailedEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 주문 번호 */
    private String orderNo;

    /** 팝업 ID */
    private UUID popupId;

    /** 고객 ID */
    private Long customerId;

    /** 실패한 재고 항목들 */
    private List<FailedStockItem> failedItems;

    /** 실패 이유 */
    private String failureReason;

    /** 이벤트 발생 시간 */
    private LocalDateTime eventTime;

    /**
     * 재고 예약 실패 이벤트 생성 팩토리 메서드
     */
    public static StockReservationFailedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                   Long customerId, List<FailedStockItem> failedItems,
                                                   String failureReason) {
        return StockReservationFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .customerId(customerId)
                .failedItems(failedItems)
                .failureReason(failureReason)
                .eventTime(LocalDateTime.now())
                .build();
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
}