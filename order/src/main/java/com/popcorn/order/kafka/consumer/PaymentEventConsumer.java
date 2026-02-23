package com.popcorn.order.kafka.consumer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.kafka.producer.StoreRequestsProducer;
import com.popcorn.order.service.core.OrderCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final OrderCommandService orderCommandService;
    private final StoreRequestsProducer storeRequestsProducer;

    @KafkaListener(
            topics = "payment-events",
            groupId = "${kafka.consumer.groups.payment:order-payment-cg}"
    )
    public void storeReserveConsumer(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment acknowledgment
    ){
        try {
            log.info("[KAFKA_CONSUME] topic={}, partition={}, offset={}, key={}", topic, partition, offset, key);
            log.info("PaymentEventConsumer payload={}", payload);

            String eventType = normalizeEventType(payload);
            String orderIdRaw = Objects.toString(payload.get("orderId"), null);

            log.info("추출된 값들 - orderId='{}', eventType='{}'", orderIdRaw, eventType);

            if (orderIdRaw == null || orderIdRaw.isBlank()) {
                log.warn("Kafka 메시지에서 orderId 누락 - eventType={}, payload={}", eventType, payload);
                acknowledgment.acknowledge();
                return;
            }

            UUID orderId = UUID.fromString(orderIdRaw);

            if (eventType == null || eventType.isBlank()) {
                log.warn("eventType 누락 메시지 무시 - orderId={}, payload={}", orderId, payload);
                acknowledgment.acknowledge();
                return;
            }

            switch (eventType) {
                    case "PAYMENT_CREATED":
                        if (!canApplyPaymentCreated(orderId)) {
                            log.info("PAYMENT_CREATED 이벤트 무시 - orderId={}, status={}", orderId,
                                    orderCommandService.getOrderStatus(orderId));
                            break;
                        }
                        orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                                "결제 생성 이벤트 수신");
                        break;
                    case "PAYMENT_APPROVED":
                        if (!canApplyPaymentApproved(orderId)) {
                            log.info("PAYMENT_APPROVED 이벤트 무시 - orderId={}, status={}", orderId,
                                    orderCommandService.getOrderStatus(orderId));
                            break;
                        }
                        String paymentId = Objects.toString(payload.get("paymentId"), null);
                        orderCommandService.updateOrderStatus(orderId, OrderStatus.PAID.name(),
                                "결제 승인 이벤트 수신", paymentId);
                        orderCommandService.requestStockDeduction(orderId);
                        orderCommandService.requestScheduleConfirmation(orderId);
                        break;
                    case "PAYMENT_FAILED":
                    case "PAYMENT_USER_CANCELLED":
                        orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                                "결제 실패/취소 이벤트 수신");
                        orderCommandService.cancelStockReservationsForOrder(orderId);
                        orderCommandService.cancelScheduleReservationsCfororder(orderId);
                        break;
                    case "PAYMENT_CANCEL_SUCCEEDED":
                        orderCommandService.releaseScheduleReservationsForOrder(orderId);
                        orderCommandService.realeaseGoodsReservationsForOrder(orderId);
                        break;
                    case "PAYMENT_CANCEL_FAILED":
                        log.error("Payment cancel failed for orderId={}, payload={}", orderId, payload);
                        break;
                    default:
                        log.info("Unhandled payment eventType: {}", eventType);
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA_CONSUME_FAIL] topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);
            throw e;
        }
    }

    private boolean canApplyPaymentCreated(UUID orderId) {
        OrderStatus current = orderCommandService.getOrderStatus(orderId);
        if (current == null) {
            return true;
        }
        return switch (current) {
            case REQUESTED, RESERVED -> true;
            default -> false;
        };
    }

    private boolean canApplyPaymentApproved(UUID orderId) {
        OrderStatus current = orderCommandService.getOrderStatus(orderId);
        if (current == null) {
            return true;
        }
        return switch (current) {
            case REQUESTED, RESERVED, PAYMENT_PENDING -> true;
            default -> false;
        };
    }

    private String normalizeEventType(Map<String, Object> map) {
        String eventType = Objects.toString(map.get("eventType"), null);
        if (eventType != null && !eventType.isBlank()) {
            return eventType;
        }

        // eventType 없이 actionType만 오는 내부 메시지를 보정.
        String actionType = Objects.toString(map.get("actionType"), null);
        if ("CONFIRM".equalsIgnoreCase(actionType)) {
            return "PAYMENT_APPROVED";
        }

        return null;
    }
}
