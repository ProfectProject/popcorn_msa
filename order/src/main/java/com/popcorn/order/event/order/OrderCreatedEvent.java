package com.popcorn.order.event.order;

import java.util.Map;
import java.util.UUID;

import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.entity.ItemType;

import lombok.Getter;

/**
 * 주문 생성 이벤트 - 새로운 주문이 만들어졌을 때 발생!
 *
 * [초보자를 위한 설명]
 * 이 이벤트는 언제 발생하나요?
 * - 고객이 주문하기 버튼을 누르고
 * - 주문 정보가 검증되고
 * - 데이터베이스에 주문이 저장되었을 때!
 *
 * 이 이벤트를 듣고 있는 다른 서비스들이 할 일:
 * - 재고 서비스: "아, 재고를 빼야겠다!"
 * - 알림 서비스: "고객에게 주문 확인 알림을 보내야겠다!"
 * - 포인트 서비스: "포인트를 적립해야겠다!"
 * - 결제 서비스: "결제 준비를 해야겠다!"
 */
@Getter
public class OrderCreatedEvent extends BaseOrderEvent {

    // 이벤트에 포함할 주요 정보들
    private final String orderNo;              // 주문 번호 (O20260120-000001 형태)
    private final ItemType orderType;         // 주문 타입 (예약/구매)
    private final OrderStatus orderStatus;     // 주문 상태
    private final Integer totalAmount;         // 총 주문 금액
    private final Integer itemCount;           // 주문 항목 개수
    private final String idempotencyKey;      // 중복 방지용 키
    private final java.time.LocalDateTime createdAt;

    /**
     * 주문 생성 이벤트 만들기
     *
     * @param order 생성된 주문
     * @param idempotencyKey 중복 방지용 키 (같은 요청이 여러 번 와도 한 번만 처리)
     */
    public OrderCreatedEvent(Order order, Integer itemCount, String idempotencyKey) {
        // 부모 클래스에 이벤트 기본 정보 전달
        super(
            order.getId(),                      // 주문 ID
            EventConstants.EventTypes.ORDER_CREATED,  // 이벤트 타입 이름
            order.getCustomerId(),              // 주문한 고객 ID
            createEventMetadata(order, itemCount, idempotencyKey)  // 추가 메타데이터
        );

        // 이 이벤트만의 정보들 저장
        this.orderNo = order.getOrderNo();
        this.orderType = order.getOrderType();
        this.orderStatus = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.itemCount = itemCount != null ? itemCount : 0;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = order.getCreatedAt();
    }

    /**
     * 이벤트 메타데이터 생성하기
     *
     * [초보자를 위한 설명]
     * 메타데이터란? 데이터에 대한 데이터예요.
     * 예를 들어, 주문 정보가 실제 데이터라면,
     * "이 주문이 언제 만들어졌는지", "어떤 타입인지" 같은 부가 정보가 메타데이터예요.
     */
    private static Map<String, Object> createEventMetadata(Order order, Integer itemCount, String idempotencyKey) {
        return Map.of(
            "orderType", order.getOrderType().name(),           // "RESERVATION" 또는 "PURCHASE"
            "status", order.getStatus().name(),                 // "REQUESTED" 등
            "totalAmount", order.getTotalAmount(),              // 총 금액
            "itemCount", itemCount != null ? itemCount : 0,     // 항목 개수
            "idempotencyKey", idempotencyKey != null ? idempotencyKey : ""  // 중복 방지 키
        );
    }

    // ================ 이벤트 전용 메서드들 ================

    /**
     * 이벤트를 JSON으로 변환할 때 포함할 정보들
     *
     * [초보자를 위한 설명]
     * 이벤트가 다른 서비스로 전송될 때는 JSON 형태로 변환돼요.
     * 이 메서드에서 어떤 정보들을 포함할지 정해요.
     */
    @Override
    public Map<String, Object> getEventPayload() {
        return Map.of(
            "orderId", getOrderId(),
            "orderNo", orderNo,
            "userId", getUserId(),
            "orderType", orderType.name(),
            "status", orderStatus.name(),
            "totalAmount", totalAmount,
            "itemCount", itemCount,
            "idempotencyKey", idempotencyKey != null ? idempotencyKey : "",
            "createdAt", createdAt
        );
    }

    /**
     * 이벤트 상세 설명 생성 (로그용)
     *
     * [초보자를 위한 설명]
     * 로그에 남길 때 사람이 읽기 쉬운 형태로 만들어 줘요.
     */
    public String getDetailedDescription() {
        return String.format(
            "새 주문이 생성되었습니다! [주문번호=%s, 고객ID=%s, 타입=%s, 금액=%d원, 항목=%d개]",
            orderNo, getUserId(), orderType.name(), totalAmount, itemCount
        );
    }

    /**
     * 비즈니스 중요도 판단 (모니터링용)
     *
     * [초보자를 위한 설명]
     * 주문 금액에 따라 중요도를 매겨요.
     * 큰 금액 주문은 더 주의 깊게 모니터링해야 하거든요.
     */
    public String getBusinessPriority() {
        if (totalAmount >= 100000) return "HIGH";      // 10만원 이상: 높음
        if (totalAmount >= 50000) return "MEDIUM";     // 5만원 이상: 중간
        return "LOW";                                   // 그 외: 낮음
    }

    /**
     * 예약형 주문인지 확인
     */
    public boolean isReservationOrder() {
        return ItemType.RESERVATION.equals(orderType);
    }

    /**
     * 구매형 주문인지 확인
     */
    public boolean isPurchaseOrder() {
        return ItemType.GOODS.equals(orderType);
    }

}
