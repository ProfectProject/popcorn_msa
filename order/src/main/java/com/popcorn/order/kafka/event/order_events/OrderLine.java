package com.popcorn.order.kafka.event.order_events;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLine {

    private UUID itemId;       // orderItems.id
    private String itemType;       // GOODS / RESERVATION
    private UUID goodsId;   // 굿즈/상품 ID 
    private UUID scheduleId;       // orderItems.sessionOptionId
    //private LocalDateTime startAt;
    //private LocalDateTime endAt; // 현재 creatAt 밖에 없음 endAt 필요
    private Integer qty;
    private Integer unitPrice;
    private Integer lineAmount;
}
