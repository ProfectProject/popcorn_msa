package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * 스케줄 예약 요청 이벤트
 *
 * [역할]
 * - 주문에서 Store 서비스로 스케줄 예약 가능 여부 확인 요청
 * - 팝업 스케줄의 잔여 좌석 확인 및 임시 예약 처리
 *
 * [Store 서비스에서 처리할 내용]
 * - 해당 스케줄의 잔여 좌석 확인
 * - 임시 예약 처리 (일정 시간 동안 좌석 홀드)
 * - 성공/실패 응답 이벤트 발행
 */
@Getter
@Builder
public class ScheduleReservationRequestedEvent {

    private final String eventId;
    private final UUID orderId;
    private final String orderNo;
    private final UUID popupId;
    private final List<ReservationItem> reservationItems;
    private final LocalDateTime requestedAt;

    @Getter
    @Builder
    public static class ReservationItem {
        private final UUID sessionOptionId;  // 스케줄 세션 ID
        private final Integer quantity;      // 예약할 좌석 수
        private final String sessionName;   // 세션명 (로그용)
        private final LocalDateTime sessionTime; // 세션 시간 (로그용)

        public static ReservationItem create(UUID sessionOptionId, Integer quantity, String sessionName, LocalDateTime sessionTime) {
            return ReservationItem.builder()
                    .sessionOptionId(sessionOptionId)
                    .quantity(quantity)
                    .sessionName(sessionName != null ? sessionName : "알 수 없는 세션")
                    .sessionTime(sessionTime)
                    .build();
        }
    }

    public static ScheduleReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId, List<ReservationItem> reservationItems) {
        return ScheduleReservationRequestedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .orderNo(orderNo)
                .popupId(popupId)
                .reservationItems(reservationItems)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 예약 요청 항목 수
     */
    public int getReservationItemCount() {
        return reservationItems != null ? reservationItems.size() : 0;
    }

    /**
     * 총 예약 좌석 수
     */
    public int getTotalQuantity() {
        return reservationItems != null
                ? reservationItems.stream().mapToInt(ReservationItem::getQuantity).sum()
                : 0;
    }

    /**
     * 이벤트 설명 (로그용)
     */
    public String getDescription() {
        return String.format(
                "스케줄 예약 요청 [주문번호=%s, 팝업ID=%s, 세션수=%d, 총좌석=%d]",
                orderNo, popupId, getReservationItemCount(), getTotalQuantity()
        );
    }
}