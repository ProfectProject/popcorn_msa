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
 * 스케줄 확정 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 결제 완료 후 예약 상태를 확정 상태로 변경할 때
 * - Store 서비스로 스케줄 확정 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 예약 → 확정 상태 변경 처리
 */
@Getter
@ToString(callSuper = true)
public class ScheduleConfirmationRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 확정 요청할 예약된 세션들 */
    private final List<ReservedSession> reservedSessions;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private ScheduleConfirmationRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                             List<ReservedSession> reservedSessions,
                                             LocalDateTime requestedAt, Long userId) {
        super(orderId, "schedule-confirmation-requested", userId);
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
    public static ScheduleConfirmationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                          List<ReservedSession> reservedSessions) {
        return create(orderId, orderNo, popupId, reservedSessions, null);
    }

    public static ScheduleConfirmationRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                          List<ReservedSession> reservedSessions, Long userId) {
        return new ScheduleConfirmationRequestedEvent(orderId, orderNo, popupId, reservedSessions,
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

        /** 세션명 (로그용) */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        /** 예약 토큰 */
        private String reservationToken;

        public static ReservedSession create(UUID sessionOptionId, Integer reservedQuantity,
                                           String sessionName, LocalDateTime sessionTime,
                                           String reservationToken) {
            return ReservedSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .reservedQuantity(reservedQuantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .reservationToken(reservationToken)
                    .build();
        }
    }
}