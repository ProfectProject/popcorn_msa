package com.popcorn.order.kafka.event.payment_requests;

import com.popcorn.order.kafka.event.MetaEvent;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Builder
public class PaymentCANCELRequestEvent {
    private MetaEvent meta;

    private UUID paymentId;
    private UUID orderId;
    private String cancelReason;
    private LocalDateTime requestedAt;

}
