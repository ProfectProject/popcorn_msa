package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 예약 취소 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 주문 취소 시 기존 스케줄 예약을 취소할 때
 * - Store 서비스로 스케줄 예약 취소 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 스케줄 예약 취소 및 좌석 복구 처리
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReservationCancelRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 세션 ID */
    private final UUID sessionId;

    /** 취소할 수량 */
    private final Integer quantity;

    /** 취소 사유 */
    private final String reason;

    /** 예약 토큰 (있는 경우) */
    private final String reservationToken;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private ScheduleReservationCancelRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                                   UUID sessionId, Integer quantity, String reason,
                                                   String reservationToken, LocalDateTime requestedAt,
                                                   Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_RESERVATION_CANCEL_REQUESTED, userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.sessionId = sessionId;
        this.quantity = quantity;
        this.reason = reason;
        this.reservationToken = reservationToken;
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
        payload.put("sessionId", sessionId != null ? sessionId.toString() : null);
        payload.put("quantity", quantity);
        payload.put("reason", reason);
        payload.put("reservationToken", reservationToken);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                               UUID sessionId, Integer quantity, String reason) {
        return create(orderId, orderNo, popupId, sessionId, quantity, reason, null, null);
    }

    public static ScheduleReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                               UUID sessionId, Integer quantity, String reason,
                                                               String reservationToken) {
        return create(orderId, orderNo, popupId, sessionId, quantity, reason, reservationToken, null);
    }

    public static ScheduleReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                               UUID sessionId, Integer quantity, String reason,
                                                               String reservationToken, Long userId) {
        return new ScheduleReservationCancelRequestedEvent(orderId, orderNo, popupId, sessionId, quantity,
                                                          reason, reservationToken, LocalDateTime.now(), userId);
    }
}
