package com.popcorn.order.kafka.event;

import com.popcorn.common.event.BaseEvent;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderItem;

import lombok.*;

import java.util.List;
import java.util.UUID;

import javax.sound.sampled.Line;

import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderCreateEvent {
    private MetaEvent meta;

    private UUID orderId;
    private String orderNo;
    private Long userId;
    private ItemType orderType;     // "RESERVATION/GOODS/MIXED"
    private UUID popupId;
    private boolean hasReservation;
    private boolean hasGoods;
    //private List<OrderI> lines;
    private List<OrderItem> orderItems;
    private Integer totalAmount;
    //private createdAt

}
