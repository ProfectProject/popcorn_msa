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
                    
                    /*// 1-2. 멱등성 키 무효화 (주문 생성 중복 방지 키 해제)
                    try {
                        var order = orderRepository.findById(orderId).orElse(null);
                        if (order != null && order.getCustomerId() != null && order.getPopupId() != null) {
                            String idempotencyKey = "order:create:" + order.getCustomerId() + ":" + order.getPopupId();
                            orderIdempotencyService.invalidateKey(idempotencyKey, "결제 승인 완료");
                            log.info("🔑 [ORDER] 멱등성 키 무효화 완료 - orderId: {}, key: {}", orderId, idempotencyKey);
                        }
                    } catch (Exception idempEx) {
                        log.warn("⚠️ [ORDER] 멱등성 키 무효화 실패 - orderId: {}, error: {}", orderId, idempEx.getMessage());
                    }*/

                    orderCommandService.requestStockDeduction(orderId);
                    orderCommandService.requestScheduleConfirmation(orderId);
                    break;
                case "PAYMENT_FAILED":
                case "PAYMENT_USER_CANCELLED":
                    String reason = (String)map.get("reason");
                    orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "결제 실패/취소 이벤트 수신");
                    orderCommandService.cancelStockReservationsForOrder(orderId); // 재고 예약 취소
                    orderCommandService.cancelScheduleReservationsCfororder(orderId); // 스케줄 예약 취소
                    break;
                case "PAYMENT_CANCEL_SUCCEEDED":
                    /*orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                            "결제 취소 성공 이벤트 수신");*/
                    // 재고 예약 해제 발행 --> 비즈니스 로직 없음 바로 발행
                    orderCommandService.releaseScheduleReservationsForOrder(orderId);
                    orderCommandService.realeaseGoodsReservationsForOrder(orderId);
                    break;
                case "PAYMENT_CANCEL_FAILED":
                    log.error("Payment cancel failed for orderId={}, payload={}", orderId, map);
                    break;
                default:
                    log.info("Unhandled store eventType: {}", eventType);
        }
    }
}
