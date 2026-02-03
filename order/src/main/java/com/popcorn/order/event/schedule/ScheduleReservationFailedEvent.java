package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * 스케줄 예약 실패 이벤트
 *
 * [역할]
 * - Store 서비스에서 Order 서비스로 스케줄 예약 실패 알림
 * - 실패한 세션들의 상세 정보 전달
 * - Order에서 주문 취소 또는 대안 처리 진행
 *
 * [Order 서비스에서 처리할 내용]
 * - 주문 상태를 CANCELLED로 변경
 * - 이미 예약된 굿즈 재고가 있다면 롤백 처리
 * - 고객에게 예약 실패 알림 발송
 */
@Getter
@Builder
public class ScheduleReservationFailedEvent {

    private final String eventId;
    private final UUID orderId;
    private final String orderNo;
    private final UUID popupId;
    private final List<FailedSession> failedSessions;
    private final String failureReason;
    private final LocalDateTime failedAt;

    @Getter
    @Builder
    public static class FailedSession {
        private final UUID sessionOptionId;    // 실패한 세션 ID
        private final Integer requestedQuantity; // 요청했던 좌석 수
        private final Integer availableQuantity; // 실제 사용 가능한 좌석 수
        private final String sessionName;      // 세션명
        private final LocalDateTime sessionTime; // 세션 시간
        private final String failureReason;    // 실패 사유

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
                    .build();
        }
    }

    public static ScheduleReservationFailedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                      List<FailedSession> failedSessions,
                                                      String failureReason) {
        return ScheduleReservationFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .failedSessions(failedSessions)
                .failureReason(failureReason)
                .failedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 실패한 세션 수
     */
    public int getFailedSessionCount() {
        return failedSessions != null ? failedSessions.size() : 0;
    }

    /**
     * 총 요청했던 좌석 수
     */
    public int getTotalRequestedQuantity() {
        return failedSessions != null
                ? failedSessions.stream().mapToInt(FailedSession::getRequestedQuantity).sum()
                : 0;
    }

    /**
     * 총 사용 가능한 좌석 수
     */
    public int getTotalAvailableQuantity() {
        return failedSessions != null
                ? failedSessions.stream().mapToInt(FailedSession::getAvailableQuantity).sum()
                : 0;
    }

    /**
     * 완전 매진인지 확인 (모든 세션의 사용 가능 좌석이 0)
     */
    public boolean isCompleteSoldOut() {
        return failedSessions != null && failedSessions.stream()
                .allMatch(session -> session.getAvailableQuantity() == 0);
    }

    /**
     * 이벤트 설명 (로그용)
     */
    public String getDescription() {
        return String.format(
                "스케줄 예약 실패 [주문번호=%s, 팝업ID=%s, 실패세션=%d개, 요청좌석=%d개, 사유=%s]",
                orderNo, popupId, getFailedSessionCount(), getTotalRequestedQuantity(), failureReason
        );
    }

    /**
     * 고객용 실패 메시지 생성
     */
    public String getCustomerMessage() {
        if (isCompleteSoldOut()) {
            return "선택하신 세션이 매진되었습니다. 다른 시간대를 선택해 주세요.";
        } else if (getTotalAvailableQuantity() > 0) {
            return String.format(
                    "요청하신 %d좌석 중 %d좌석만 예약 가능합니다. 좌석 수를 조정해 주세요.",
                    getTotalRequestedQuantity(), getTotalAvailableQuantity()
            );
        } else {
            return "예약 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";
        }
    }
}