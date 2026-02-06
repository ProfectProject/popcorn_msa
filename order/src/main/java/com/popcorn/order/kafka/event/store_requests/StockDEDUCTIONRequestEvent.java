package com.popcorn.order.kafka.event.store_requests;

import com.popcorn.order.kafka.event.MetaEvent;
import java.time.LocalDateTime;
import java.util.UUID;

import com.popcorn.order.kafka.event.MetaEvent;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class StockDEDUCTIONRequestEvent {
    private MetaEvent meta;

    private UUID orderId;
    //deductionItems[],
    //requestedAt
}
