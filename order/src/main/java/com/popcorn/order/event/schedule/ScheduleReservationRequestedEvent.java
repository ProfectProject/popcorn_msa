package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 예약 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 스케줄 세션 예약을 요청할 때
 * - Redis Stream을 통해 Store 서비스로 전송
 *
 * [이벤트 수신자]
 * - Store 모듈: 스케줄 세션 예약 처리
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - orderNo: 주문 번호
 * - popupId: 팝업 ID
 * - reservedSessions[]: 예약할 세션들
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReservationRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 예약할 세션들 */
    private final List<ReservedSession> reservedSessions;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private ScheduleReservationRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                            List<ReservedSession> reservedSessions,
                                            LocalDateTime requestedAt, Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_RESERVATION_REQUESTED, userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.reservedSessions = reservedSessions;
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
        payload.put("reservedSessionCount", reservedSessions != null ? reservedSessions.size() : 0);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                         List<ReservedSession> reservedSessions) {
        return create(orderId, orderNo, popupId, reservedSessions, null);
    }

    public static ScheduleReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                         List<ReservedSession> reservedSessions, Long userId) {
        return new ScheduleReservationRequestedEvent(orderId, orderNo, popupId, reservedSessions,
                                                    LocalDateTime.now(), userId);
    }

    /**
     * 예약할 세션 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReservedSession {
        /** 세션 옵션 ID */
        private UUID sessionOptionId;

        /** 예약할 수량 */
        private Integer quantity;

        /** 세션명 */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        public static ReservedSession create(UUID sessionOptionId, Integer quantity,
                                           String sessionName, LocalDateTime sessionTime) {
            return ReservedSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .quantity(quantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .build();
        }
    }
}
