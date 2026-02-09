package com.popcorn.order.kafka.event.store_requests;

import com.popcorn.order.kafka.event.MetaEvent;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScheduleCONFIRMATIONRequestEvent {
    private MetaEvent meta;

    private UUID orderId;
    //reservedSessions[]
    //requestedAt
}
