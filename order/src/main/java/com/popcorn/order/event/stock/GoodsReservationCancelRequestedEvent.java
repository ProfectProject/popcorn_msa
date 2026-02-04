package com.popcorn.order.event.stock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 굿즈 예약 취소 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 주문 취소 시 기존 굿즈 예약을 취소할 때
 * - Store 서비스로 굿즈 예약 취소 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 굿즈 예약 취소 및 재고 복구 처리
 */
@Getter
@ToString(callSuper = true)
public class GoodsReservationCancelRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 굿즈 ID */
    private final UUID goodsId;

    /** 취소할 수량 */
    private final Integer quantity;

    /** 취소 사유 */
    private final String reason;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private GoodsReservationCancelRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                                UUID goodsId, Integer quantity, String reason,
                                                LocalDateTime requestedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.GOODS_RESERVATION_CANCEL_REQUESTED, userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.reason = reason;
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
        payload.put("reason", reason);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static GoodsReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                            UUID goodsId, Integer quantity, String reason) {
        return create(orderId, orderNo, popupId, goodsId, quantity, reason, null);
    }

    public static GoodsReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                            UUID goodsId, Integer quantity, String reason,
                                                            Long userId) {
        return new GoodsReservationCancelRequestedEvent(orderId, orderNo, popupId, goodsId, quantity,
                                                       reason, LocalDateTime.now(), userId);
    }
}
