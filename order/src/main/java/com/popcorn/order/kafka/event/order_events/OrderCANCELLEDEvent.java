package com.popcorn.order.kafka.event.order_events;

import com.popcorn.common.event.BaseEvent;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.kafka.event.MetaEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDateTime;

import lombok.*;

import java.util.List;
import java.util.UUID;

import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderCANCELLEDEvent {
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
    private UUID storeId;
    private boolean hasReservation;
    private boolean hasGoods;
    private List<OrderLine> lines;
    private LocalDateTime cancelledAt;

}
