package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
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
public class ScheduleReservationRequestedEvent extends BaseOrderEvent {

    private final String orderNo;
    private final UUID popupId;
    private final List<ReservationItem> reservationItems;
    private final LocalDateTime requestedAt;

    private ScheduleReservationRequestedEvent(UUID orderId, String orderNo, UUID popupId, List<ReservationItem> reservationItems,
                                            LocalDateTime requestedAt, Long userId) {
        super(orderId, "schedule-reservation-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.reservationItems = reservationItems;
        this.requestedAt = requestedAt;
    }

    @Getter
    public static class ReservationItem {
        private final UUID sessionOptionId;  // 스케줄 세션 ID
        private final Integer quantity;      // 예약할 좌석 수
        private final String sessionName;   // 세션명 (로그용)
        private final LocalDateTime sessionTime; // 세션 시간 (로그용)

        public ReservationItem(UUID sessionOptionId, Integer quantity, String sessionName, LocalDateTime sessionTime) {
            this.sessionOptionId = sessionOptionId;
            this.quantity = quantity;
            this.sessionName = sessionName;
            this.sessionTime = sessionTime;
        }

        public static ReservationItem create(UUID sessionOptionId, Integer quantity, String sessionName, LocalDateTime sessionTime) {
            return new ReservationItem(sessionOptionId, quantity,
                    sessionName != null ? sessionName : "알 수 없는 세션", sessionTime);
        }
    }

    public static ScheduleReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId, List<ReservationItem> reservationItems) {
        return create(orderId, orderNo, popupId, reservationItems, null);
    }

    public static ScheduleReservationRequestedEvent create(UUID orderId, String orderNo, UUID popupId, List<ReservationItem> reservationItems, Long userId) {
        return new ScheduleReservationRequestedEvent(orderId, orderNo, popupId, reservationItems, LocalDateTime.now(), userId);
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("popupId", popupId.toString());
        payload.put("reservationItems", reservationItems);
        payload.put("requestedAt", requestedAt.toString());
        payload.put("reservationItemCount", getReservationItemCount());
        payload.put("totalQuantity", getTotalQuantity());
        return payload;
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