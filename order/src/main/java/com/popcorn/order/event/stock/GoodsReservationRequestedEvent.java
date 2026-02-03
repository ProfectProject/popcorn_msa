package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;

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
public class GoodsReservationRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 예약 요청 항목들 */
    private final List<ReservationItem> reservationItems;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private GoodsReservationRequestedEvent(UUID orderId, String orderNo, UUID popupId, List<ReservationItem> reservationItems,
                                         LocalDateTime requestedAt, Long userId) {
        super(orderId, "goods-reservation-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.reservationItems = reservationItems;
        this.requestedAt = requestedAt;
    }

    public static GoodsReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                        List<ReservationItem> reservationItems) {
        return create(orderId, orderNo, popupId, reservationItems, null);
    }

    public static GoodsReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                        List<ReservationItem> reservationItems, Long userId) {
        return new GoodsReservationRequestedEvent(orderId, orderNo, popupId, reservationItems, LocalDateTime.now(), userId);
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("popupId", popupId.toString());
        payload.put("reservationItems", reservationItems);
        payload.put("requestedAt", requestedAt.toString());
        return payload;
    }

    @Getter
    public static class ReservationItem {
        private final UUID goodsId;
        private final Integer quantity;

        public ReservationItem(UUID goodsId, Integer quantity) {
            this.goodsId = goodsId;
            this.quantity = quantity;
        }

        public static ReservationItem create(UUID goodsId, Integer quantity) {
            return new ReservationItem(goodsId, quantity);
        }
    }
}
