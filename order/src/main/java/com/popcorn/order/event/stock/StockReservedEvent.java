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
 * 재고 예약 성공 이벤트
 *
 * [발행 시점]
 * - Order 모듈에서 Store 모듈에 재고 예약 요청 성공 시
 *
 * [수신자]
 * - Payment 모듈: 결제 타임아웃 타이머 시작
 * - Analytics 모듈: 재고 예약 통계
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class StockReservedEvent {

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

    /** 예약된 재고 항목들 */
    private List<ReservedStockItem> reservedItems;

    /** 예약 만료 시간 (30분 후) */
    private LocalDateTime reservationExpiresAt;

    /** 이벤트 발생 시간 */
    private LocalDateTime eventTime;

    /**
     * 재고 예약 성공 이벤트 생성 팩토리 메서드
     */
    public static StockReservedEvent create(UUID orderId, String orderNo, UUID popupId,
                                          Long customerId, List<ReservedStockItem> reservedItems) {
        return StockReservedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .customerId(customerId)
                .reservedItems(reservedItems)
                .reservationExpiresAt(LocalDateTime.now().plusMinutes(30))
                .eventTime(LocalDateTime.now())
                .build();
    }

    /**
     * 예약된 재고 항목 정보
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReservedStockItem {
        /** 굿즈 변형 ID */
        private UUID goodsId;

        /** 예약 수량 */
        private Integer quantity;

        /** 단가 */
        private Integer unitPrice;

        /** 상품명 */
        private String productName;

        public static ReservedStockItem create(UUID goodsId, Integer quantity,
                                             Integer unitPrice, String productName) {
            return ReservedStockItem.builder()
                    .goodsId(goodsId)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .productName(productName)
                    .build();
        }
    }
}