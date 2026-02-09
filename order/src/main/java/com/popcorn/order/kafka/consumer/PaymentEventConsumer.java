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
        log.info("🔔 카프카 메세지 수신 - PaymentEventConsumer: {}", kafkaMessage);

        Map<Object, Object> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            map = mapper.readValue(kafkaMessage, new TypeReference<Map<Object, Object>>() {
            });
            log.info("🔍 파싱된 메시지 맵: {}", map);
        } catch (JsonProcessingException e) {
            log.error("❌ JSON 파싱 실패: {}", e.getMessage(), e);
            return;
        }

        String eventType = (String)map.get("eventType");
        String orderIdRaw = (String)map.get("orderId");

        log.info("📋 추출된 값들 - orderId: '{}' (null={}), eventType: '{}' (null={})",
                orderIdRaw, orderIdRaw == null, eventType, eventType == null);

        if (orderIdRaw == null || orderIdRaw.isBlank()) {
            log.warn("⚠️ Kafka 메시지에서 orderId가 null 또는 비어있음 - eventType: {}, 전체 맵: {}", eventType, map);
            return;
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdRaw);
            log.info("✅ orderId UUID 파싱 성공: {}", orderId);
        } catch (IllegalArgumentException e) {
            log.error("❌ orderId UUID 파싱 실패: '{}' - error: {}", orderIdRaw, e.getMessage());
            return;
        }
        

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
