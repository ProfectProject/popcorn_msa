package com.popcorn.order.kafka.event.order_events;

import java.time.LocalDateTime;
import java.util.UUID;

import com.popcorn.order.kafka.event.MetaEvent;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderCOMPLETEDEvent {
    private MetaEvent meta;

    private UUID orderId;
    private UUID popupId;
    private boolean hasReservation;
    private boolean hasGoods;
    //private UUID paymentId; // payment event 에서 수신후 listner에서 넘기고 추가
    private Integer totalAmount;
    private LocalDateTime completedAt;

}
