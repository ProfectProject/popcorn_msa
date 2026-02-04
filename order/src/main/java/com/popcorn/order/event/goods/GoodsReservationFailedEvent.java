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
 * 굿즈 예약 실패 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 굿즈 재고 예약이 실패했을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 굿즈 예약 실패 처리 및 주문 취소 또는 보상 트랜잭션
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - goodsId: 굿즈 ID
 * - reason: 실패 사유
 */
@Getter
@ToString(callSuper = true)
public class GoodsReservationFailedEvent extends BaseOrderEvent {

    /** 굿즈 ID */
    private final UUID goodsId;

    /** 실패 사유 */
    private final String reason;

    /** 실패 시간 */
    private final LocalDateTime failedAt;

    private GoodsReservationFailedEvent(UUID orderId, UUID goodsId, String reason,
                                      LocalDateTime failedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.GOODS_RESERVATION_FAILED, userId);
        this.goodsId = goodsId;
        this.reason = reason;
        this.failedAt = failedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("goodsId", goodsId != null ? goodsId.toString() : null);
        payload.put("reason", reason);
        payload.put("failedAt", failedAt != null ? failedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static GoodsReservationFailedEvent create(UUID orderId, UUID goodsId, String reason) {
        return create(orderId, goodsId, reason, null);
    }

    public static GoodsReservationFailedEvent create(UUID orderId, UUID goodsId, String reason, Long userId) {
        return new GoodsReservationFailedEvent(orderId, goodsId, reason, LocalDateTime.now(), userId);
    }
}
