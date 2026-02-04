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
 * 스케줄 예약 성공 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 스케줄 세션 예약이 성공했을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 스케줄 예약 성공 처리 및 다음 단계 진행
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - reservedSessions[]: 예약된 세션들
 * - token: 예약 토큰
 * - expiresAt: 예약 만료 시간
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReservationSucceededEvent extends BaseOrderEvent {

    /** 예약된 세션들 */
    private final List<ReservedSession> reservedSessions;

    /** 예약 토큰 */
    private final String token;

    /** 예약 만료 시간 */
    private final LocalDateTime expiresAt;

    /** 예약 성공 시간 */
    private final LocalDateTime succeededAt;

    private ScheduleReservationSucceededEvent(UUID orderId, List<ReservedSession> reservedSessions,
                                            String token, LocalDateTime expiresAt,
                                            LocalDateTime succeededAt, Long userId) {
        super(orderId, EventConstants.EventTypes.SCHEDULE_RESERVATION_SUCCEEDED, userId);
        this.reservedSessions = reservedSessions;
        this.token = token;
        this.expiresAt = expiresAt;
        this.succeededAt = succeededAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservedSessionCount", reservedSessions != null ? reservedSessions.size() : 0);
        payload.put("token", token);
        payload.put("expiresAt", expiresAt != null ? expiresAt.toString() : null);
        payload.put("succeededAt", succeededAt != null ? succeededAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReservationSucceededEvent create(UUID orderId, List<ReservedSession> reservedSessions,
                                                         String token, LocalDateTime expiresAt) {
        return create(orderId, reservedSessions, token, expiresAt, null);
    }

    public static ScheduleReservationSucceededEvent create(UUID orderId, List<ReservedSession> reservedSessions,
                                                         String token, LocalDateTime expiresAt, Long userId) {
        return new ScheduleReservationSucceededEvent(orderId, reservedSessions, token, expiresAt,
                                                    LocalDateTime.now(), userId);
    }

    /**
     * 예약된 세션 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReservedSession {
        /** 세션 옵션 ID */
        private UUID sessionOptionId;

        /** 예약된 수량 */
        private Integer reservedQuantity;

        /** 세션명 */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        /** 잔여 좌석 */
        private Integer remainingSeats;

        /** 예약 시간 */
        private LocalDateTime reservedAt;

        public static ReservedSession create(UUID sessionOptionId, Integer reservedQuantity,
                                           String sessionName, LocalDateTime sessionTime,
                                           Integer remainingSeats) {
            return ReservedSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .reservedQuantity(reservedQuantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .remainingSeats(remainingSeats)
                    .reservedAt(LocalDateTime.now())
                    .build();
        }
    }
}
