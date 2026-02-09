package com.popcorn.order.kafka.consumer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.OrderComparator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.constants.EventConstants;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.service.core.OrderCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final OrderCommandService orderCommandService;

    @KafkaListener(topics = "payment-events", groupId = "order-payment-cg")
    public void storeReserveConsumer(String kafkaMessage){
        log.info("카프카 메세지-PaymentEventConsumer:{}",kafkaMessage);

        Map<Object, Object> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            map = mapper.readValue(kafkaMessage, new TypeReference<Map<Object, Object>>() {
            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        log.info("카프카 orderId={} , eventType={}",(String)map.get("orderId"),(String)map.get("eventType"));

        String eventType = (String)map.get("eventType");
        String orderIdRaw = (String)map.get("orderId");

        /*if (orderIdRaw  == null || orderIdRaw .isBlank()) {
            return null;
        }*/

        UUID orderId = UUID.fromString(orderIdRaw);
        

        switch (eventType) {
                case "PAYMENT_CREATED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                            "결제 생성 이벤트 수신");
                    break;
                case "PAYMENT_APPROVED":
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.PAID.name(),
                            "결제 승인 이벤트 수신"); // 수신하고 paymentId 넣기
                    orderCommandService.requestStockDeduction(orderId);
                    orderCommandService.requestScheduleConfirmation(orderId);
                    break;
                default:
                    log.info("Unhandled store eventType: {}", eventType);
        }
    }
}
