package com.popcorn.order.event.schedule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.event.order.BaseOrderEvent;
import lombok.Getter;

/**
 * 스케줄 예약 취소 요청 이벤트
 *
 * [역할]
 * - Order 서비스에서 Store 서비스로 스케줄 예약 취소 요청
 * - 주문 취소 시 임시 예약된 좌석들을 해제
 * - 결제 실패, 재고 부족 등으로 인한 보상 트랜잭션
 *
 * [Store 서비스에서 처리할 내용]
 * - 해당 예약 토큰으로 임시 예약된 좌석들 해제
 * - 좌석을 다시 예약 가능 상태로 복원
 * - 취소 완료 응답 이벤트 발행
 */
@Getter
public class ScheduleReservationCancelRequestedEvent extends BaseOrderEvent {

    private final String orderNo;
    private final UUID popupId;
    private final String reservationToken;      // 예약 시 발급된 토큰
    private final List<CancelItem> cancelItems;
    private final String cancelReason;
    private final LocalDateTime cancelRequestedAt;

    private ScheduleReservationCancelRequestedEvent(UUID orderId, String orderNo, UUID popupId, String reservationToken,
                                                    List<CancelItem> cancelItems, String cancelReason,
                                                    LocalDateTime cancelRequestedAt, Long userId) {
        super(orderId, "schedule-reservation-cancel-requested", userId);
        this.orderNo = orderNo;
        this.popupId = popupId;
        this.reservationToken = reservationToken;
        this.cancelItems = cancelItems;
        this.cancelReason = cancelReason;
        this.cancelRequestedAt = cancelRequestedAt;
    }

    @Getter
    public static class CancelItem {
        private final UUID sessionOptionId;    // 취소할 세션 ID
        private final Integer quantity;        // 취소할 좌석 수
        private final String sessionName;     // 세션명 (로그용)
        private final LocalDateTime sessionTime; // 세션 시간 (로그용)
        private final String reservationCode; // 예약 코드

        public CancelItem(UUID sessionOptionId, Integer quantity, String sessionName,
                         LocalDateTime sessionTime, String reservationCode) {
            this.sessionOptionId = sessionOptionId;
            this.quantity = quantity;
            this.sessionName = sessionName;
            this.sessionTime = sessionTime;
            this.reservationCode = reservationCode;
        }

        public static CancelItem create(UUID sessionOptionId, Integer quantity,
                                      String sessionName, LocalDateTime sessionTime,
                                      String reservationCode) {
            return new CancelItem(sessionOptionId, quantity,
                    sessionName != null ? sessionName : "알 수 없는 세션",
                    sessionTime, reservationCode);
        }
    }

    public static ScheduleReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                               String reservationToken,
                                                               List<CancelItem> cancelItems,
                                                               String cancelReason) {
        return create(orderId, orderNo, popupId, reservationToken, cancelItems, cancelReason, null);
    }

    public static ScheduleReservationCancelRequestedEvent create(UUID orderId, String orderNo, UUID popupId,
                                                               String reservationToken,
                                                               List<CancelItem> cancelItems,
                                                               String cancelReason, Long userId) {
        return new ScheduleReservationCancelRequestedEvent(
                orderId, orderNo, popupId, reservationToken, cancelItems,
                cancelReason != null ? cancelReason : "주문 취소",
                LocalDateTime.now(), userId);
    }

    @Override
    public Map<String, Object> getEventPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", getOrderId().toString());
        payload.put("popupId", popupId.toString());
        payload.put("reservationToken", reservationToken);
        payload.put("cancelItems", cancelItems);
        payload.put("cancelReason", cancelReason);
        payload.put("cancelRequestedAt", cancelRequestedAt.toString());
        payload.put("cancelItemCount", getCancelItemCount());
        payload.put("totalCancelQuantity", getTotalCancelQuantity());
        return payload;
    }

    /**
     * 취소할 세션 수
     */
    public int getCancelItemCount() {
        return cancelItems != null ? cancelItems.size() : 0;
    }

    /**
     * 총 취소할 좌석 수
     */
    public int getTotalCancelQuantity() {
        return cancelItems != null
                ? cancelItems.stream().mapToInt(CancelItem::getQuantity).sum()
                : 0;
    }

    /**
     * 이벤트 설명 (로그용)
     */
    public String getDescription() {
        return String.format(
                "스케줄 예약 취소 요청 [주문번호=%s, 팝업ID=%s, 취소세션=%d개, 총좌석=%d개, 사유=%s]",
                orderNo, popupId, getCancelItemCount(), getTotalCancelQuantity(), cancelReason
        );
    }

    /**
     * 긴급 취소인지 확인 (결제 완료 후 취소)
     */
    public boolean isUrgentCancel() {
        return cancelReason != null && (
                cancelReason.contains("결제 완료") ||
                cancelReason.contains("PAID") ||
                cancelReason.contains("긴급")
        );
    }

    /**
     * 보상 트랜잭션인지 확인 (재고 부족, 결제 실패 등)
     */
    public boolean isCompensationCancel() {
        return cancelReason != null && (
                cancelReason.contains("재고") ||
                cancelReason.contains("결제 실패") ||
                cancelReason.contains("보상") ||
                cancelReason.contains("롤백")
        );
    }
}