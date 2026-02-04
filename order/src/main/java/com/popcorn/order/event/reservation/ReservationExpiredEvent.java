package com.popcorn.order.event.reservation;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;
import lombok.ToString;

/**
 * 예약 만료 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 예약이 만료되었을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 예약 만료 처리 및 주문 취소
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - popupId: 팝업 ID
 * - orderId: 주문 ID
 * - reservationType: 예약 타입 (SCHEDULE, GOODS, MIXED)
 * - reservationIds[]: 만료된 예약 ID들
 * - expiredAt: 만료 시간
 */
@Getter
@ToString(callSuper = true)
public class ReservationExpiredEvent extends BaseOrderEvent {

    /** 팝업 ID */
    private final UUID popupId;

    /** 예약 타입 */
    private final String reservationType;

    /** 만료된 예약 ID들 */
    private final List<String> reservationIds;

    /** 만료 시간 */
    private final LocalDateTime expiredAt;

    private ReservationExpiredEvent(UUID orderId, UUID popupId, String reservationType,
                                  List<String> reservationIds, LocalDateTime expiredAt, Long userId) {
        super(orderId, EventConstants.EventTypes.RESERVATION_EXPIRED, userId);
        this.popupId = popupId;
        this.reservationType = reservationType;
        this.reservationIds = reservationIds;
        this.expiredAt = expiredAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("reservationType", reservationType);
        payload.put("reservationIdCount", reservationIds != null ? reservationIds.size() : 0);
        payload.put("expiredAt", expiredAt != null ? expiredAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ReservationExpiredEvent create(UUID orderId, UUID popupId, String reservationType,
                                               List<String> reservationIds, LocalDateTime expiredAt) {
        return create(orderId, popupId, reservationType, reservationIds, expiredAt, null);
    }

    public static ReservationExpiredEvent create(UUID orderId, UUID popupId, String reservationType,
                                               List<String> reservationIds, LocalDateTime expiredAt, Long userId) {
        return new ReservationExpiredEvent(orderId, popupId, reservationType, reservationIds, expiredAt, userId);
    }

    /**
     * 스케줄 예약 만료 이벤트 생성
     */
    public static ReservationExpiredEvent createScheduleExpired(UUID orderId, UUID popupId,
                                                              List<String> reservationIds, LocalDateTime expiredAt) {
        return create(orderId, popupId, "SCHEDULE", reservationIds, expiredAt, null);
    }

    /**
     * 굿즈 예약 만료 이벤트 생성
     */
    public static ReservationExpiredEvent createGoodsExpired(UUID orderId, UUID popupId,
                                                           List<String> reservationIds, LocalDateTime expiredAt) {
        return create(orderId, popupId, "GOODS", reservationIds, expiredAt, null);
    }

    /**
     * 혼합 예약 만료 이벤트 생성
     */
    public static ReservationExpiredEvent createMixedExpired(UUID orderId, UUID popupId,
                                                           List<String> reservationIds, LocalDateTime expiredAt) {
        return create(orderId, popupId, "MIXED", reservationIds, expiredAt, null);
    }
}
