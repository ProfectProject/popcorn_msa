package com.popcorn.order.event.goods;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 굿즈 예약 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 굿즈 재고 예약을 요청할 때
 * - Redis Stream을 통해 Store 서비스로 전송
 *
 * [이벤트 수신자]
 * - Store 모듈: 굿즈 재고 예약 처리
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - orderNo: 주문 번호
 * - popupId: 팝업 ID
 * - goodsId: 굿즈 ID
 * - quantity: 예약할 수량
 */
@Getter
@ToString(callSuper = true)
public class GoodsReservationRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 굿즈 ID */
    private final UUID goodsId;

    /** 예약할 수량 */
    private final Integer quantity;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private GoodsReservationRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                         UUID goodsId, Integer quantity, LocalDateTime requestedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.GOODS_RESERVATION_REQUESTED, userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.requestedAt = requestedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderNo", orderNo);
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("goodsId", goodsId != null ? goodsId.toString() : null);
        payload.put("quantity", quantity);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static GoodsReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                       UUID goodsId, Integer quantity) {
        return create(orderId, orderNo, popupId, goodsId, quantity, null);
    }

    public static GoodsReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                       UUID goodsId, Integer quantity, Long userId) {
        return new GoodsReservationRequestedEvent(orderId, orderNo, popupId, goodsId, quantity,
                                                 LocalDateTime.now(), userId);
    }
}
