package com.popcorn.order.event.order;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.popcorn.order.constants.EventConstants;
import com.popcorn.common.event.BaseEvent;

import lombok.Getter;

/**
 * 주문 도메인 이벤트의 기본 클래스 - 초보자도 이해하기 쉽게!
 *
 * [초보자를 위한 설명]
 * 이벤트란? 시스템에서 중요한 일이 일어났을 때 다른 부분들에게 알려주는 메시지입니다.
 * 예를 들어: "주문이 생성되었어요!", "주문이 취소되었어요!" 같은 것들이죠.
 *
 * 왜 이벤트가 필요할까?
 * - 주문이 생성되면 → 재고를 빼고, 알림을 보내고, 포인트를 적립하고... 여러 일을 해야 해요
 * - 이런 일들을 주문 생성 코드에 다 넣으면 너무 복잡해져요
 * - 대신 "주문 생성됨" 이벤트를 발생시키고, 각 기능들이 이벤트를 듣고 알아서 처리해요
 *
 * 이벤트 시스템의 장점:
 * - 코드가 깔끔해져요
 * - 새로운 기능 추가가 쉬워져요
 * - 문제가 생겨도 다른 기능에 영향을 안 줘요
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = EventConstants.EventTypes.ORDER_CREATED),
    @JsonSubTypes.Type(value = OrderStatusChangedEvent.class, name = EventConstants.EventTypes.ORDER_STATUS_CHANGED),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = EventConstants.EventTypes.ORDER_CANCELLED),
    @JsonSubTypes.Type(value = OrderCompletedEvent.class, name = EventConstants.EventTypes.ORDER_COMPLETED),
    @JsonSubTypes.Type(value = OrderPaidEvent.class, name = "order-paid")
})
@Getter
public abstract class BaseOrderEvent extends BaseEvent {

    /**
     * 주문 이벤트 생성자
     *
     * [초보자를 위한 설명]
     * 주문과 관련된 모든 이벤트는 이 기본 클래스를 상속받아요.
     * 공통으로 필요한 정보들(주문 ID, 사용자 ID 등)을 여기서 관리해요.
     */
    protected BaseOrderEvent(UUID orderId, String eventType, Long userId, Map<String, Object> metadata) {
        super(orderId, "Order", eventType, userId, metadata);
    }

    protected BaseOrderEvent(UUID orderId, String eventType, Long userId) {
        this(orderId, eventType, userId, null);
    }

    // ================ 주문 도메인 전용 메서드들 ================

    /**
     * 주문 ID 조회하기
     *
     * [초보자를 위한 설명]
     * getAggregateId()라는 복잡한 이름 대신,
     * getOrderId()라는 이해하기 쉬운 이름으로 사용할 수 있어요.
     */
    public UUID getOrderId() {
        return getAggregateId();  // 부모 클래스의 메서드를 호출
    }

    /**
     * 이벤트가 주문과 관련된 것인지 확인
     */
    public boolean isOrderEvent() {
        return "Order".equals(getAggregateType());
    }

}