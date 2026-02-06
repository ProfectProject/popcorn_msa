package com.popcorn.order.kafka.event.order_events;

import java.time.LocalDateTime;
import java.util.UUID;

import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.kafka.event.MetaEvent;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderStatusUpdatedEvent {
    private MetaEvent meta;

    private UUID orderId;
    private UUID popupId;
    private OrderStatus fromStatus;      // 변경 전 상태
    private OrderStatus toStatus;        // 변경 후 상태
    private boolean hasReservation;
    private boolean hasGoods;
    private LocalDateTime updatedAt;
}
