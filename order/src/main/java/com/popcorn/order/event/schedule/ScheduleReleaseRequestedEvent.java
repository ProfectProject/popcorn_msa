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
 * 스케줄 해제 요청 이벤트
 *
 * [이벤트 발행 시점]
 * - Order 서비스에서 주문 취소나 환불 시 확정된 스케줄을 해제할 때
 * - Store 서비스로 스케줄 해제(복구) 요청
 *
 * [이벤트 수신자]
 * - Store 모듈: 확정된 스케줄 해제 및 좌석 복구 처리
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReleaseRequestedEvent extends BaseOrderEvent {

    /** 주문 번호 */
    private final String orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 해제할 스케줄 세션들 */
    private final List<ReleaseSession> releaseSessions;

    /** 해제 사유 */
    private final String reason;

    /** 요청 시간 */
    private final LocalDateTime requestedAt;

    private ScheduleReleaseRequestedEvent(UUID orderId, String orderNo, UUID popupId,
                                        List<ReleaseSession> releaseSessions, String reason,
                                        LocalDateTime requestedAt, Long userId) {
        super(orderId, "schedule-release-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.releaseSessions = releaseSessions;
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
        payload.put("releaseSessionCount", releaseSessions != null ? releaseSessions.size() : 0);
        payload.put("reason", reason);
        payload.put("requestedAt", requestedAt != null ? requestedAt.toString() : null);
        return payload;
    }

    /**
     * 팩토리 메서드
     */
    public static ScheduleReleaseRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                     List<ReleaseSession> releaseSessions, String reason) {
        return create(orderId, orderNo, popupId, releaseSessions, reason, null);
    }

    public static ScheduleReleaseRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                     List<ReleaseSession> releaseSessions, String reason,
                                                     Long userId) {
        return new ScheduleReleaseRequestedEvent(orderId, orderNo, popupId, releaseSessions, reason,
                                               LocalDateTime.now(), userId);
    }

    /**
     * 해제할 스케줄 세션 정보
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReleaseSession {
        /** 세션 옵션 ID */
        private UUID sessionOptionId;

        /** 해제할 수량 */
        private Integer quantity;

        /** 세션명 (로그용) */
        private String sessionName;

        /** 세션 시간 */
        private LocalDateTime sessionTime;

        /** 원래 확정된 시점 */
        private LocalDateTime originalConfirmedAt;

        /** 확정 토큰 (있는 경우) */
        private String confirmationToken;

        public static ReleaseSession create(UUID sessionOptionId, Integer quantity, String sessionName,
                                          LocalDateTime sessionTime, LocalDateTime originalConfirmedAt,
                                          String confirmationToken) {
            return ReleaseSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .quantity(quantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .originalConfirmedAt(originalConfirmedAt)
                    .confirmationToken(confirmationToken)
                    .build();
        }
    }
}