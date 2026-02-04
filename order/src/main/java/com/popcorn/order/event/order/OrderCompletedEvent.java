package com.popcorn.order.event.order;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import lombok.Getter;

/**
 * 주문 완료 이벤트 - 주문이 모든 과정을 거쳐서 완료되었을 때 발생!
 *
 * [초보자를 위한 설명]
 * 주문이 언제 완료되나요?
 * - 예약형 주문: 고객이 팝업스토어에 실제로 방문했을 때
 * - 구매형 주문: 상품이 고객에게 성공적으로 배송되었을 때
 *
 * 즉, 주문의 모든 과정이 끝났다는 뜻이에요!
 *
 * 이 이벤트를 듣고 있는 서비스들이 할 일:
 * - 포인트 서비스: "포인트 적립을 확정하자!"
 * - 리뷰 서비스: "고객에게 리뷰 작성 요청을 보내자!"
 * - 정산 서비스: "점주에게 정산금을 지급하자!"
 * - 통계 서비스: "매출 통계를 업데이트하자!"
 * - 마케팅 서비스: "관련 상품 추천을 하자!"
 */
@Getter
public class OrderCompletedEvent extends BaseOrderEvent {

    // 완료에 대한 정보들
    private final LocalDateTime orderDate;   // 주문한 날짜
    private final String completedBy;        // 누가 완료 처리했는지
    private final Integer finalAmount;       // 최종 결제 금액
    private final Integer itemCount;         // 주문 항목 개수

    /**
     * 주문 완료 이벤트 만들기
     *
     * @param orderId 완료된 주문 ID
     * @param userId 주문한 고객 ID
     * @param orderDate 주문 날짜
     * @param completedBy 완료 처리한 주체
     * @param finalAmount 최종 결제 금액
     * @param itemCount 주문 항목 개수
     */
    public OrderCompletedEvent(UUID orderId, Long userId, LocalDateTime orderDate,
                             String completedBy, Integer finalAmount, Integer itemCount) {
        super(
            orderId,
            EventConstants.EventTypes.ORDER_COMPLETED,
            userId,
            createEventMetadata(orderDate, completedBy, finalAmount, itemCount)
        );

        this.orderDate = orderDate;
        this.completedBy = completedBy;
        this.finalAmount = finalAmount;
        this.itemCount = itemCount;
    }

    /**
     * 완료 메타데이터 생성
     */
    private static Map<String, Object> createEventMetadata(LocalDateTime orderDate,
                                                          String completedBy, Integer finalAmount, Integer itemCount) {
        return Map.of(
            "orderDate", orderDate != null ? orderDate.toString() : "UNKNOWN",
            "completedBy", completedBy != null ? completedBy : "SYSTEM",
            "finalAmount", finalAmount != null ? finalAmount : 0,
            "itemCount", itemCount != null ? itemCount : 0
        );
    }

    /**
     * 빌더 패턴을 위한 정적 팩토리 메서드
     */
    public static OrderCompletedEventBuilder builder() {
        return new OrderCompletedEventBuilder();
    }

    /**
     * OrderCompletedEvent 빌더 클래스
     */
    public static class OrderCompletedEventBuilder {
        private String eventId;
        private UUID orderId;
        private Long userId;
        private LocalDateTime orderDate;
        private String completedBy;
        private Integer finalAmount;
        private Integer itemCount;

        public OrderCompletedEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public OrderCompletedEventBuilder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public OrderCompletedEventBuilder orderNo(String orderNo) {
            // orderNo는 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCompletedEventBuilder popupId(UUID popupId) {
            // popupId는 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCompletedEventBuilder customerId(Long customerId) {
            this.userId = customerId; // customerId를 userId로 매핑
            return this;
        }

        public OrderCompletedEventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public OrderCompletedEventBuilder orderDate(LocalDateTime orderDate) {
            this.orderDate = orderDate;
            return this;
        }

        public OrderCompletedEventBuilder completedBy(String completedBy) {
            this.completedBy = completedBy;
            return this;
        }

        public OrderCompletedEventBuilder finalAmount(Integer finalAmount) {
            this.finalAmount = finalAmount;
            return this;
        }

        public OrderCompletedEventBuilder itemCount(Integer itemCount) {
            this.itemCount = itemCount;
            return this;
        }

        public OrderCompletedEventBuilder completedAt(LocalDateTime completedAt) {
            // completedAt은 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCompletedEventBuilder eventTime(LocalDateTime eventTime) {
            // eventTime은 실제로는 사용하지 않지만 호환성을 위해 추가
            return this;
        }

        public OrderCompletedEvent build() {
            return new OrderCompletedEvent(orderId, userId, orderDate, completedBy, finalAmount, itemCount);
        }
    }

    // ================ 이벤트 전용 메서드들 ================

    @Override
    public Map<String, Object> getEventPayload() {
        return Map.of(
            "orderId", getOrderId(),
            "userId", getUserId(),
            "orderDate", orderDate,
            "completedBy", completedBy,
            "finalAmount", finalAmount,
            "itemCount", itemCount,
            "completedAt", getTimestamp()
        );
    }

    /**
     * 완료 설명 (사람이 읽기 쉬운 형태)
     */
    public String getDetailedDescription() {
        return String.format(
            "주문이 완료되었습니다! [주문ID=%s, 고객ID=%s, 금액=%d원, 항목=%d개, 완료자=%s]",
            getOrderId(), getUserId(), finalAmount, itemCount, completedBy
        );
    }

    /**
     * 고액 주문인지 확인 (특별 관리 대상)
     */
    public boolean isHighValueOrder() {
        return finalAmount != null && finalAmount >= 100000; // 10만원 이상
    }

    /**
     * 대량 주문인지 확인
     */
    public boolean isBulkOrder() {
        return itemCount != null && itemCount >= 5; // 5개 항목 이상
    }

    /**
     * 시스템에 의한 자동 완료인지 확인
     */
    public boolean isAutoCompleted() {
        return "SYSTEM".equals(completedBy);
    }

    /**
     * 점주에 의한 수동 완료인지 확인
     */
    public boolean isManuallyCompleted() {
        return "OWNER".equals(completedBy) || "MANAGER".equals(completedBy);
    }

    /**
     * 완료 우선순위 (정산, 포인트 적립 등의 우선순위)
     */
    public String getCompletionPriority() {
        if (isHighValueOrder()) return "HIGH";     // 고액 주문은 높은 우선순위
        if (isBulkOrder()) return "MEDIUM";        // 대량 주문은 중간 우선순위
        return "LOW";                              // 일반 주문은 낮은 우선순위
    }

    /**
     * 주문 처리 기간 계산 (주문일부터 완료일까지)
     */
    public long getProcessingDays() {
        if (orderDate == null) return 0;
        return java.time.Duration.between(orderDate, getTimestamp()).toDays();
    }

    /**
     * 빠른 처리인지 확인 (1일 이내 완료)
     */
    public boolean isFastProcessing() {
        return getProcessingDays() <= 1;
    }

}
