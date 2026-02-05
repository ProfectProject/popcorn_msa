package com.popcorn.store.event.order;

import com.popcorn.store.event.standard.EventLineItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

/**
 * ORDER_CREATED payload 를 Store 서비스 내부로 전달하기 위한 DTO.
 * Kafka에서 내려온 이벤트를 ApplicationEvent로 재포장하여 다른 컴포넌트에서 사용할 수 있다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrderCreatedEvent {

    private String eventId;
    private UUID orderId;
    private String orderNo;
    private Long userId;
    private String orderType;
    private UUID popupId;
    private UUID storeId;
    private Boolean hasReservation;
    private Boolean hasGoods;
    private List<EventLineItem> lines;
    private Integer totalAmount;
    private String createdAt;
}
