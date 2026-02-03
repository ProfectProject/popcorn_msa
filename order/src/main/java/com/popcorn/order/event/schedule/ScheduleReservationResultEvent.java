package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 스케줄 예약 결과 이벤트 (성공/실패 통합)
 *
 * [통합 이벤트의 장점]
 * - 성공/실패 이벤트를 하나로 관리하여 코드 중복 제거
 * - status 필드로 결과 분기 처리
 * - 리스너 코드 단순화
 */
@Getter
@ToString(callSuper = true)
public class ScheduleReservationResultEvent extends BaseOrderEvent {

    /** 예약 결과 상태 */
    private final ResultStatus status;

    /** 주문 번호 (선택적) */
    private final Optional<String> orderNo;

    /** 팝업 ID */
    private final UUID popupId;

    /** 예약 토큰 (성공 시) */
    private final String reservationToken;

    /** 예약된 세션들 (성공 시) */
    private final List<ReservedSession> reservedSessions;

    /** 실패한 세션들 (실패 시) */
    private final List<FailedSession> failedSessions;

    /** 실패 사유 (실패 시) */
    private final String failureReason;

    /** 처리 완료 시간 */
    private final LocalDateTime processedAt;

    /**
     * 생성자 (orderNo Optional 처리)
     */
    public ScheduleReservationResultEvent(UUID orderId, Long customerId, ResultStatus status,
                                        Optional<String> orderNo, UUID popupId, String reservationToken,
                                        List<ReservedSession> reservedSessions,
                                        List<FailedSession> failedSessions,
                                        String failureReason, LocalDateTime processedAt) {
        super(orderId, status == ResultStatus.SUCCESS ? "schedule-reservation-success" : "schedule-reservation-failed", customerId);
        this.status = status;
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.reservationToken = reservationToken;
        this.reservedSessions = reservedSessions;
        this.failedSessions = failedSessions;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
    }

    /**
     * BaseEvent에서 요구하는 getEventPayload() 메서드 구현
     */
    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        orderNo.ifPresent(no -> payload.put("orderNo", no));
        payload.put("popupId", popupId != null ? popupId.toString() : null);
        payload.put("processedAt", processedAt != null ? processedAt.toString() : null);

        if (status == ResultStatus.SUCCESS) {
            payload.put("reservationToken", reservationToken);
            payload.put("reservedSessionCount", reservedSessions != null ? reservedSessions.size() : 0);
        } else {
            payload.put("failureReason", failureReason);
            payload.put("failedSessionCount", failedSessions != null ? failedSessions.size() : 0);
        }

        return payload;
    }

    /**
     * 성공 결과 팩토리 메서드 (orderNo Optional)
     */
    public static ScheduleReservationResultEvent success(UUID orderId, Long customerId, Optional<String> orderNo,
                                                        UUID popupId, String reservationToken,
                                                        List<ReservedSession> reservedSessions) {
        return new ScheduleReservationResultEvent(orderId, customerId, ResultStatus.SUCCESS,
                orderNo, popupId, reservationToken, reservedSessions, null, null, LocalDateTime.now());
    }

    /**
     * 실패 결과 팩토리 메서드 (orderNo Optional)
     */
    public static ScheduleReservationResultEvent failure(UUID orderId, Long customerId, Optional<String> orderNo,
                                                         UUID popupId, String failureReason,
                                                         List<FailedSession> failedSessions) {
        return new ScheduleReservationResultEvent(orderId, customerId, ResultStatus.FAILED,
                orderNo, popupId, null, null, failedSessions, failureReason, LocalDateTime.now());
    }

    /**
     * 성공/실패 편의 메서드들
     */
    public boolean isSuccess() {
        return status == ResultStatus.SUCCESS;
    }

    public boolean isFailure() {
        return status == ResultStatus.FAILED;
    }

    /**
     * 주문 번호 조회 (Optional에서 값 추출)
     */
    public String getOrderNo() {
        return orderNo.orElse(null);
    }

    /**
     * 결과 상태 Enum
     */
    public enum ResultStatus {
        SUCCESS, FAILED
    }

    /**
     * 예약된 세션 정보 (성공 시)
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class ReservedSession {
        private UUID sessionOptionId;
        private Integer reservedQuantity;
        private String sessionName;
        private LocalDateTime sessionTime;
        private Integer remainingSeats;
        private String reservationCode;

        public static ReservedSession create(UUID sessionOptionId, Integer reservedQuantity,
                                           String sessionName, LocalDateTime sessionTime,
                                           Integer remainingSeats, String reservationCode) {
            return new ReservedSession(sessionOptionId, reservedQuantity, sessionName,
                                     sessionTime, remainingSeats, reservationCode);
        }
    }

    /**
     * 실패한 세션 정보 (실패 시)
     */
    @Getter
    @AllArgsConstructor
    @Builder
    @ToString
    public static class FailedSession {
        private UUID sessionOptionId;
        private Integer requestedQuantity;
        private Integer availableQuantity;
        private String sessionName;
        private LocalDateTime sessionTime;
        private String failureReason;

        public static FailedSession create(UUID sessionOptionId, Integer requestedQuantity,
                                         Integer availableQuantity, String sessionName,
                                         LocalDateTime sessionTime, String failureReason) {
            return new FailedSession(sessionOptionId, requestedQuantity, availableQuantity,
                                   sessionName, sessionTime, failureReason);
        }
    }
}