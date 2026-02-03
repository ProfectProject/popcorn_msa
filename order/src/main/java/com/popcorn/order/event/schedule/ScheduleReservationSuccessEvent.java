package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * 스케줄 예약 성공 이벤트
 *
 * [역할]
 * - Store 서비스에서 Order 서비스로 스케줄 예약 성공 알림
 * - 임시 예약된 좌석 정보 전달
 * - Order에서 다음 단계 (결제 대기 또는 굿즈 재고 예약) 진행
 *
 * [Order 서비스에서 처리할 내용]
 * - 주문 상태를 RESERVED로 변경
 * - 굿즈가 있으면 굿즈 재고 예약 요청
 * - 굿즈가 없으면 결제 대기 상태로 전환
 */
@Getter
@Builder
public class ScheduleReservationSuccessEvent {

    private final String eventId;
    private final UUID orderId;
    private final String orderNo;
    private final UUID popupId;
    private final List<ReservedSession> reservedSessions;
    private final String reservationToken;      // 예약 확인용 토큰
    private final LocalDateTime reservedAt;
    private final LocalDateTime expiresAt;      // 예약 만료 시간 (결제 완료 전까지 유효)

    @Getter
    @Builder
    public static class ReservedSession {
        private final UUID sessionOptionId;    // 예약된 세션 ID
        private final Integer reservedQuantity; // 예약된 좌석 수
        private final String sessionName;      // 세션명
        private final LocalDateTime sessionTime; // 세션 시간
        private final Integer remainingSeats;  // 예약 후 남은 좌석 수
        private final String reservationCode;  // 예약 코드

        public static ReservedSession create(UUID sessionOptionId, Integer reservedQuantity,
                                           String sessionName, LocalDateTime sessionTime,
                                           Integer remainingSeats, String reservationCode) {
            return ReservedSession.builder()
                    .sessionOptionId(sessionOptionId)
                    .reservedQuantity(reservedQuantity)
                    .sessionName(sessionName)
                    .sessionTime(sessionTime)
                    .remainingSeats(remainingSeats)
                    .reservationCode(reservationCode)
                    .build();
        }
    }

    public static ScheduleReservationSuccessEvent create(UUID orderId, String orderNo, UUID popupId,
                                                       List<ReservedSession> reservedSessions,
                                                       String reservationToken) {
        return ScheduleReservationSuccessEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .reservedSessions(reservedSessions)
                .reservationToken(reservationToken)
                .reservedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30)) // 30분 후 만료
                .build();
    }

    /**
     * 예약된 세션 수
     */
    public int getReservedSessionCount() {
        return reservedSessions != null ? reservedSessions.size() : 0;
    }

    /**
     * 총 예약 좌석 수
     */
    public int getTotalReservedQuantity() {
        return reservedSessions != null
                ? reservedSessions.stream().mapToInt(ReservedSession::getReservedQuantity).sum()
                : 0;
    }

    /**
     * 예약이 만료되었는지 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 이벤트 설명 (로그용)
     */
    public String getDescription() {
        return String.format(
                "스케줄 예약 성공 [주문번호=%s, 팝업ID=%s, 예약세션=%d개, 총좌석=%d개, 만료시간=%s]",
                orderNo, popupId, getReservedSessionCount(), getTotalReservedQuantity(), expiresAt
        );
    }
}