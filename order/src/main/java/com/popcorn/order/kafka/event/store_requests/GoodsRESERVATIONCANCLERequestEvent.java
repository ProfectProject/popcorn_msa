package com.popcorn.order.kafka.event.store_requests;

import com.popcorn.order.kafka.event.MetaEvent;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoodsRESERVATIONCANCLERequestEvent {
    private MetaEvent meta;

    private UUID orderId;
    private UUID goodsId;
    //quantity
    //reason
}
