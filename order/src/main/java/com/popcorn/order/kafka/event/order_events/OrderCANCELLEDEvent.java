package com.popcorn.order.kafka.event.order_events;

import java.time.LocalDateTime;
import java.util.UUID;

import com.popcorn.order.kafka.event.MetaEvent;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderCANCELLEDEvent {
    private MetaEvent meta;

    private UUID orderId;
    private UUID popupId;
    private boolean hasReservation;
    private boolean hasGoods;
    private Integer totalAmount;
    private LocalDateTime cancelledAt;

}
