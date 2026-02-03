package com.popcorn.order.event.goods;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 굿즈 예약 성공 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 굿즈 재고 예약이 성공했을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 굿즈 예약 성공 처리 및 다음 단계 진행
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - goodsId: 굿즈 ID
 * - quantity: 예약된 수량
 * - expiresAt: 예약 만료 시간
 */
@Getter
@ToString(callSuper = true)
public class GoodsReservationSucceededEvent extends BaseOrderEvent {

    /** 굿즈 ID */
    private final UUID goodsId;

    /** 예약된 수량 */
    private final Integer quantity;

    /** 예약 만료 시간 */
    private final LocalDateTime expiresAt;

    /** 예약 성공 시간 */
    private final LocalDateTime succeededAt;

    private GoodsReservationSucceededEvent(UUID orderId, UUID goodsId, Integer quantity,
                                         LocalDateTime expiresAt, LocalDateTime succeededAt, Long userId) {
        super(orderId, "goods-reservation-succeeded", userId);
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.succeededAt = succeededAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("goodsId", goodsId != null ? goodsId.toString() : null);
        payload.put("quantity", quantity);
        payload.put("expiresAt", expiresAt != null ? expiresAt.toString() : null);
        payload.put("succeededAt", succeededAt != null ? succeededAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static GoodsReservationSucceededEvent create(UUID orderId, UUID goodsId, Integer quantity,
                                                       LocalDateTime expiresAt) {
        return create(orderId, goodsId, quantity, expiresAt, null);
    }

    public static GoodsReservationSucceededEvent create(UUID orderId, UUID goodsId, Integer quantity,
                                                       LocalDateTime expiresAt, Long userId) {
        return new GoodsReservationSucceededEvent(orderId, goodsId, quantity, expiresAt,
                                                 LocalDateTime.now(), userId);
    }
}