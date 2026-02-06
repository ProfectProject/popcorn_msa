package com.popcorn.order.kafka.event.order_events;

import com.popcorn.common.event.BaseEvent;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.kafka.event.MetaEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDateTime;

import lombok.*;

import java.util.List;
import java.util.UUID;

import javax.sound.sampled.Line;

import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderStatusUpdatedEvent {
    //private MetaEvent meta;
    private UUID eventId;
    private UUID correlationId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String eventType;      // "ORDER_CREATED"
    private String eventVersion;   // "1.0"
    private String producer;       // "order-service"

    private UUID orderId;
    private UUID popupId;

    private OrderStatus fromStatus;      // 변경 전 상태
    private OrderStatus toStatus;        // 변경 후 상태

    private boolean hasReservation;
    private boolean hasGoods;

    //private LocalDateTime updatedAt;
    private Instant updatedAt;
}
