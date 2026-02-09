package com.popcorn.order.kafka.event.order_events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.popcorn.order.kafka.event.MetaEvent;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderCOMPLETEDEvent {
    //private MetaEvent meta;

    private UUID eventId;
    private UUID correlationId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String eventType;      // "ORDER_CANCELLED"
    private String eventVersion;   // "1.0"
    private String producer;       // "order-service"

    private UUID orderId;
    private UUID popupId;
    private boolean hasReservation;
    private boolean hasGoods;
    private Integer totalAmount;
}
