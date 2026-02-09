package com.popcorn.order.kafka.event.store_requests;

import java.util.UUID;
import com.popcorn.order.kafka.event.MetaEvent;
import java.time.LocalDateTime;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScheduleRESERVATIONCANCELRequestEvent {
    private MetaEvent meta;

    private UUID orderId;
    private UUID sessionId;
    //reason

}
