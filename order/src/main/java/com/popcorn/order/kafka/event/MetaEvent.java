package com.popcorn.order.kafka.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MetaEvent {
    private UUID eventId;
    private UUID correlationId;
    private LocalDateTime timestamp;
    private String eventType;      // UPPER_SNAKE_CASE 추천
    private String eventVersion;   // "1.0"
    private String producer;       // "order-service"
    //private UUID aggregateId;      // orderId
    private String aggregateType;  // "Order"
    private Map<String, Object> metadata;
}
