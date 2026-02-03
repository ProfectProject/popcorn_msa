package com.popcorn.order.event.order;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.entity.OrderStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * 주문 취소 이벤트 - 주문이 취소되었을 때 발생!
 *
 * [초보자를 위한 설명]
 * 주문이 언제 취소되나요?
 * - 고객이 직접 취소 버튼을 눌렀을 때
 * - 점주가 주문을 거절했을 때
 * - 결제가 실패했을 때
 * - 시스템에서 자동으로 취소했을 때 (예: 재고 부족)
 *
 * 이 이벤트를 듣고 있는 서비스들이 할 일:
 * - 재고 서비스: "예약했던 재고를 다시 풀어주자!"
 * - 결제 서비스: "결제가 이미 됐다면 환불 처리하자!"
 * - 알림 서비스: "고객에게 취소 알림을 보내자!"
 * - 포인트 서비스: "적립했던 포인트를 취소하자!"
 */
@Getter
@Builder
public class OrderCancelledEvent extends BaseOrderEvent {

    // 취소에 대한 정보들
    private final OrderStatus previousStatus;  // 취소되기 전 상태
    private final String cancelReason;         // 취소 이유
    private final String cancelledBy;          // 누가 취소했는지
    private final Integer refundAmount;        // 환불 금액 (결제가 됐었다면)

    /**
     * 주문 취소 이벤트 만들기
     *
     * @param orderId 취소된 주문 ID
     * @param userId 주문한 고객 ID
     * @param previousStatus 취소되기 전 상태
     * @param cancelReason 취소 이유
     * @param cancelledBy 취소 주체 (CUSTOMER, OWNER, SYSTEM 등)
     * @param refundAmount 환불할 금액
     */
    public OrderCancelledEvent(UUID orderId, Long userId, OrderStatus previousStatus,
                             String cancelReason, String cancelledBy, Integer refundAmount) {
        super(
            orderId,
            "order_cancelled",
            userId,
            createEventMetadata(previousStatus, cancelReason, cancelledBy, refundAmount)
        );

        this.previousStatus = previousStatus;
        this.cancelReason = cancelReason;
        this.cancelledBy = cancelledBy;
        this.refundAmount = refundAmount;
    }

    /**
     * 취소 메타데이터 생성
     */
    private static Map<String, Object> createEventMetadata(OrderStatus previousStatus, String cancelReason,
                                                          String cancelledBy, Integer refundAmount) {
        return Map.of(
            "previousStatus", previousStatus.name(),
            "cancelReason", cancelReason != null ? cancelReason : "이유 없음",
            "cancelledBy", cancelledBy != null ? cancelledBy : "SYSTEM",
            "refundAmount", refundAmount != null ? refundAmount : 0
        );
    }

    /**
     * 빌더 패턴을 위한 정적 팩토리 메서드
     */
    public static OrderCancelledEventBuilder builder() {
        return new OrderCancelledEventBuilder();
    }

    /**
     * OrderCancelledEvent 빌더 클래스
     */
    public static class OrderCancelledEventBuilder {
        private String eventId;
        private UUID orderId;
        private Long userId;
        private OrderStatus previousStatus;
        private String cancelReason;
        private String cancelledBy;
        private Integer refundAmount;

        public OrderCancelledEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public OrderCancelledEventBuilder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public OrderCancelledEventBuilder orderNo(String orderNo) {
            // orderNo는 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCancelledEventBuilder popupId(UUID popupId) {
            // popupId는 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCancelledEventBuilder customerId(Long customerId) {
            this.userId = customerId; // customerId를 userId로 매핑
            return this;
        }

        public OrderCancelledEventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public OrderCancelledEventBuilder previousStatus(OrderStatus previousStatus) {
            this.previousStatus = previousStatus;
            return this;
        }

        public OrderCancelledEventBuilder cancelReason(String cancelReason) {
            this.cancelReason = cancelReason;
            return this;
        }

        public OrderCancelledEventBuilder cancelledBy(String cancelledBy) {
            this.cancelledBy = cancelledBy;
            return this;
        }

        public OrderCancelledEventBuilder refundAmount(Integer refundAmount) {
            this.refundAmount = refundAmount;
            return this;
        }

        public OrderCancelledEventBuilder reason(String reason) {
            this.cancelReason = reason; // reason을 cancelReason으로 매핑
            return this;
        }

        public OrderCancelledEventBuilder cancelledAt(LocalDateTime cancelledAt) {
            // cancelledAt은 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCancelledEventBuilder eventTime(LocalDateTime eventTime) {
            // eventTime은 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCancelledEvent build() {
            return new OrderCancelledEvent(orderId, userId, previousStatus, cancelReason, cancelledBy, refundAmount);
        }
    }

    // ================ 이벤트 전용 메서드들 ================

    @Override
    public Map<String, Object> getEventPayload() {
        return Map.of(
            "orderId", getOrderId(),
            "userId", getUserId(),
            "previousStatus", previousStatus.name(),
            "cancelReason", cancelReason,
            "cancelledBy", cancelledBy,
            "refundAmount", refundAmount != null ? refundAmount : 0,
            "timestamp", getTimestamp()
        );
    }

    /**
     * 취소 설명 (사람이 읽기 쉬운 형태)
     */
    public String getDetailedDescription() {
        String refundInfo = (refundAmount != null && refundAmount > 0)
            ? String.format(", 환불금액=%d원", refundAmount)
            : "";

        return String.format(
            "주문이 취소되었습니다! [주문ID=%s, 이전상태=%s, 이유=%s, 취소자=%s%s]",
            getOrderId(), previousStatus.name(), cancelReason, cancelledBy, refundInfo
        );
    }

    /**
     * 환불이 필요한 취소인지 확인
     */
    public boolean needsRefund() {
        return refundAmount != null && refundAmount > 0;
    }

    /**
     * 고객에 의한 취소인지 확인
     */
    public boolean isCancelledByCustomer() {
        return "CUSTOMER".equals(cancelledBy);
    }

    /**
     * 점주에 의한 취소인지 확인 (주문 거절)
     */
    public boolean isCancelledByOwner() {
        return "OWNER".equals(cancelledBy) || "MANAGER".equals(cancelledBy);
    }

    /**
     * 시스템에 의한 자동 취소인지 확인
     */
    public boolean isCancelledBySystem() {
        return "SYSTEM".equals(cancelledBy);
    }

    /**
     * 결제 후 취소인지 확인
     */
    public boolean isPostPaymentCancellation() {
        return previousStatus == OrderStatus.PAID || previousStatus == OrderStatus.COMPLETED;
    }

    /**
     * 취소 우선순위 (환불이 필요한 경우 높은 우선순위)
     */
    public String getCancellationPriority() {
        if (needsRefund()) return "HIGH";           // 환불 필요시 높음
        if (isPostPaymentCancellation()) return "MEDIUM"; // 결제 후 취소는 중간
        return "LOW";                               // 그 외는 낮음
    }

    /**
     * 취소 사유 반환 (OrderEventPublisher에서 사용)
     */
    public String getReason() {
        return this.cancelReason;
    }

}