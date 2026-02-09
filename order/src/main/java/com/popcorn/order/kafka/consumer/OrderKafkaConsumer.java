/* /package com.popcorn.order.kafka.consumer;

import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.service.core.OrderCommandService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderKafkaConsumer {

    private final OrderCommandService orderCommandService;

    @KafkaListener(topics = "store-events", groupId = "order-fulfillment-cg")
    public void onStoreEvent(@Payload Map<String, Object> payload, Acknowledgment ack) {
        try {
            String eventType = normalize(getString(payload, "eventType"));
            UUID orderId = getUuid(payload, "orderId");
            log.info("카프카 메세지:{}",payload);
            if (orderId == null) {
                log.warn("store-events received without orderId: {}", payload);
                ack.acknowledge();
                return;
            }

            log.info("카프카 orderId={} , eventType={}",(String)payload.get("orderId"),(String)payload.get("eventType"));

            switch (eventType) {
                case "GOODS_RESERVATION_SUCCEEDED":
                case "SCHEDULE_RESERVATION_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.RESERVED.name(),
                            "!! OrderKafkaConsumer : 예약 성공 이벤트 수신");
                    // 결제 요청 이벤트 발행 -> ordercommand 에서 ?
                    // 결제 생성 요청 이벤트 발행
                    orderCommandService.publishPaymentCreateRequestedEvent(orderId);
                    
                    break;
                case "GOODS_RESERVATION_FAILED":
                case "SCHEDULE_RESERVATION_FAILED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.REJECTED.name(),
                            "예약 실패 이벤트 수신");
                    /*
                    orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                        "재고 부족 - 주문 실패: " + (reason != null ? reason : "재고 부족"));
                     */
                    /*/break;
                /*case "RESERVATION_EXPIRED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.EXPIRED.name(),
                            "예약 만료 이벤트 수신");
                    break;*/
                /*/case "STOCK_DEDUCTION_SUCCEEDED":
                case "SCHEDULE_CONFIRMATION_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.COMPLETED.name(),
                            "차감/확정 성공 이벤트 수신");
                    orderCommandService.publishOrderCompletedEvent(orderId);
                    break;
                case "STOCK_DEDUCTION_FAILED":
                case "SCHEDULE_CONFIRMATION_FAILED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "차감/확정 실패 이벤트 수신");
                    orderCommandService.cancelPaymentForOrder(orderId, getString(payload, "paymentId"),
                            "차감/확정 실패로 인한 결제 취소");
                    break;
                case "STOCK_RELEASED":
                case "SCHEDULE_RELEASED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "재고/스케줄 해제 이벤트 수신");
                    break;
                default:
                    log.info("Unhandled store eventType: {}", eventType);
            }
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-payment-cg")
    public void onPaymentEvent(@Payload Map<String, Object> payload, Acknowledgment ack) {
        try {
            String eventType = normalize(getString(payload, "eventType"));
            UUID orderId = getUuid(payload, "orderId");
            if (orderId == null) {
                log.warn("payment-events received without orderId: {}", payload);
                ack.acknowledge();
                return;
            }

            switch (eventType) {
                case "PAYMENT_CREATED":
                //case "payment-url-created":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                            "결제 생성 이벤트 수신");
                    break;
                case "PAYMENT_APPROVED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAID.name(),
                            "결제 승인 이벤트 수신");
                    orderCommandService.requestStockDeduction(orderId);
                    orderCommandService.requestScheduleConfirmation(orderId);
                    break;
                case "PAYMENT_FAILED":
                case "PAYMENT_USER_CANCELLED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "결제 실패/취소 이벤트 수신");
                    orderCommandService.cancelStockReservationsForOrder(orderId);
                    break;
                case "PAYMENT_CANCEL_SUCCEEDED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "결제 취소 성공 이벤트 수신");
                    break;
                case "PAYMENT_CANCEL_FAILED":
                    log.error("Payment cancel failed for orderId={}, payload={}", orderId, payload);
                    break;
                default:
                    log.info("Unhandled payment eventType: {}", eventType);
            }
        } finally {
            ack.acknowledge();
        }
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private UUID getUuid(Map<String, Object> payload, String key) {
        String value = getString(payload, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}*/