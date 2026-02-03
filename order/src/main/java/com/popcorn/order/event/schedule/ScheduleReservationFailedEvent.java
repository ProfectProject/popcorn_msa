package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 예약 실패 이벤트
 *
 * [이벤트 발행 시점]
 * - Store 서비스에서 스케줄 세션 예약이 실패했을 때
 * - Redis Stream을 통해 Order 서비스로 전송
 *
 * [이벤트 수신자]
 * - Order 모듈: 스케줄 예약 실패 처리 및 주문 취소 또는 보상 트랜잭션
 *
 * [Payload 정보]
 * - eventId: 이벤트 고유 ID
 * - orderId: 주문 ID
 * - failedSessions[]: 실패한 세션들
 * - reason: 실패 사유
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReservationFailedEvent extends BaseOrderEvent {

    /** 실패한 세션들 */
    private final List<FailedSession> failedSessions;

    /** 전체 실패 사유 */
    private final String reason;

    /** 실패 시간 */
    private final LocalDateTime failedAt;

    private ScheduleReservationFailedEvent(UUID orderId, List<FailedSession> failedSessions,
                                         String reason, LocalDateTime failedAt, Long userId) {
        super(orderId, "schedule-reservation-failed", userId);
        this.failedSessions = failedSessions;
        this.reason = reason;
        this.failedAt = failedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("failedSessionCount", failedSessions != null ? failedSessions.size() : 0);
        payload.put("reason", reason);
        payload.put("failedAt", failedAt != null ? failedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReservationFailedEvent create(UUID orderId, List<FailedSession> failedSessions,
                                                       String reason) {
        return create(orderId, failedSessions, reason, null);
    }

    public static ScheduleReservationFailedEvent create(UUID orderId, List<FailedSession> failedSessions,
                                                       String reason, Long userId) {
        return new ScheduleReservationFailedEvent(orderId, failedSessions, reason, LocalDateTime.now(), userId);
    }

    /**
     * 실패한 세션 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class FailedSession {
        /** 세션 옵션 ID */
        private UUID sessionOptionId;

        /** 요청된 수량 */
        private Integer requestedQuantity;

        /** 사용 가능한 수량 */
        private Integer availableQuantity;

        /** 세션명 */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        /** 개별 실패 사유 */
        private String failureReason;

        /** 실패 시간 */
        private LocalDateTime failedAt;

        public static FailedSession create(UUID sessionOptionId, Integer requestedQuantity,
                                         Integer availableQuantity, String sessionName,
                                         LocalDateTime sessionTime, String failureReason) {
            return FailedSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .requestedQuantity(requestedQuantity)
                    .availableQuantity(availableQuantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .failureReason(failureReason)
                    .failedAt(LocalDateTime.now())
                    .build();
        }
    }
}