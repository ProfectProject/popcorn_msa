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
 * 굿즈 재고 예약 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 주문 생성 직후 재고 예약이 필요할 때
 * - HTTP 동기 호출 대신 비동기 이벤트로 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 굿즈 재고 예약 및 예약 레코드 생성
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GoodsReservationRequestedEvent {

    /** 이벤트 ID (추적용) */
    private String eventId;

    /** 주문 ID */
    private UUID orderId;

    /** 주문 번호 */
    private String orderNo;

    /** 팝업 ID */
    private UUID popupId;

    /** 예약 요청 항목들 */
    private List<ReservationItem> reservationItems;

    /** 요청 시간 */
    private LocalDateTime requestedAt;

    public static GoodsReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                        List<ReservationItem> reservationItems) {
        return GoodsReservationRequestedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .reservationItems(reservationItems)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReservationItem {
        private UUID goodsId;
        private Integer quantity;

        public static ReservationItem create(UUID goodsId, Integer quantity) {
            return ReservationItem.builder()
                    .goodsId(goodsId)
                    .quantity(quantity)
                    .build();
        }
    }
}
