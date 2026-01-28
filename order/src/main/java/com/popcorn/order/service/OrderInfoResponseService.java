package com.popcorn.order.service;

import com.popcorn.order.entity.Order;
import com.popcorn.order.entity.OrderItem;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.event.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order 정보 요청에 대한 응답 처리 서비스
 * Payment 서비스 등에서 Order 정보를 요청했을 때 응답하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderInfoResponseService {

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ORDER_INFO_RESPONSES_STREAM = "order-info-responses";

    /**
     * Order 정보 요청 처리 및 응답
     */
    public void handleOrderInfoRequest(Map<String, Object> requestData) {
        String requestId = null;
        try {
            log.info("🔄 [ORDER-INFO] Order 정보 요청 처리 시작 - requestData: {}", requestData);

            requestId = (String) requestData.get("requestId");
            String orderIdStr = (String) requestData.get("requestedOrderId");
            String responseService = (String) requestData.get("responseService");

            // 데이터 정제 (따옴표 제거)
            if (requestId != null) requestId = requestId.trim().replaceAll("^\"|\"$", "");
            if (orderIdStr != null) orderIdStr = orderIdStr.trim().replaceAll("^\"|\"$", "");
            if (responseService != null) responseService = responseService.trim().replaceAll("^\"|\"$", "");

            log.info("🔄 [ORDER-INFO] Order 정보 요청 수신 - requestId: {}, orderId: {}, from: {}",
                    requestId, orderIdStr, responseService);

            if (requestId == null || requestId.isEmpty() || orderIdStr == null || orderIdStr.isEmpty()) {
                log.warn("⚠️ [ORDER-INFO] 필수 정보 누락 - requestId: {}, orderId: {}", requestId, orderIdStr);
                publishOrderInfoResponse(requestId, false, null, "필수 정보 누락");
                return;
            }

            UUID orderId;
            try {
                orderId = UUID.fromString(orderIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ [ORDER-INFO] 잘못된 UUID 형식 - orderId: {}, error: {}", orderIdStr, e.getMessage());
                publishOrderInfoResponse(requestId, false, null, "잘못된 UUID 형식");
                return;
            }

            // Order 조회
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("⚠️ [ORDER-INFO] 주문을 찾을 수 없음 - orderId: {}", orderId);
                publishOrderInfoResponse(requestId, false, null, "주문을 찾을 수 없음");
                return;
            }

            // Order 정보 응답 발행
            publishOrderInfoResponse(requestId, true, order, "성공");

            log.info("✅ [ORDER-INFO] Order 정보 응답 발행 완료 - requestId: {}, orderNo: {}",
                    requestId, order.getOrderNo());

        } catch (Exception e) {
            log.error("❌ [ORDER-INFO] Order 정보 요청 처리 실패 - requestId: {}, error: {}", requestId, e.getMessage(), e);

            // requestId가 없을 경우 원본 데이터에서 다시 추출 시도
            if (requestId == null) {
                try {
                    requestId = (String) requestData.get("requestId");
                    if (requestId != null) {
                        requestId = requestId.trim().replaceAll("^\"|\"$", "");
                    }
                } catch (Exception ex) {
                    log.error("❌ [ORDER-INFO] requestId 추출도 실패: {}", ex.getMessage());
                }
            }

            publishOrderInfoResponse(requestId, false, null, "처리 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * Order 정보 응답 이벤트 발행
     */
    private void publishOrderInfoResponse(String requestId, boolean success, Order order, String message) {
        try {
            Map<String, String> responseData = new HashMap<>();

            // 기본 응답 정보
            responseData.put("requestId", requestId != null ? requestId : "");
            responseData.put("success", Boolean.toString(success));
            responseData.put("message", message);
            responseData.put("respondedAt", LocalDateTime.now().toString());

            if (success && order != null) {
                // 성공 시 실제 Order 정보 포함
                responseData.put("actualOrderNo", order.getOrderNo());
                responseData.put("actualUserId", order.getCustomerId().toString());
                responseData.put("actualPopupId", order.getPopupId() != null ? order.getPopupId().toString() : "");
                responseData.put("actualHasReservation", Boolean.toString(hasReservation(order)));
                responseData.put("actualHasGoods", Boolean.toString(hasGoods(order)));

                // Lines 정보 JSON 직렬화
                String linesJson = convertOrderItemsToJson(order.getOrderItems());
                responseData.put("actualLines", linesJson);
            }

            // Redis Stream에 응답 발행
            StringRecord record = StreamRecords.string(responseData)
                    .withStreamKey(ORDER_INFO_RESPONSES_STREAM);

            String recordId = redisTemplate.opsForStream().add(record).getValue();

            log.info("📤 [ORDER-INFO] Order 정보 응답 발행 완료 - requestId: {}, success: {}, recordId: {}",
                     requestId, success, recordId);

        } catch (Exception e) {
            log.error("❌ [ORDER-INFO] Order 정보 응답 발행 실패 - requestId: {}, error: {}",
                    requestId, e.getMessage(), e);
        }
    }

    /**
     * 주문에 예약이 포함되어 있는지 확인
     */
    private boolean hasReservation(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item -> ItemType.RESERVATION.equals(item.getOrderItemType()));
    }

    /**
     * 주문에 굿즈가 포함되어 있는지 확인
     */
    private boolean hasGoods(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item -> ItemType.GOODS.equals(item.getOrderItemType()));
    }

    /**
     * OrderItem들을 JSON으로 변환
     */
    private String convertOrderItemsToJson(List<OrderItem> orderItems) {
        try {
            List<Map<String, Object>> items = orderItems.stream()
                    .map(this::convertOrderItemToMap)
                    .collect(Collectors.toList());

            com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.error("OrderItems JSON 변환 실패: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * OrderItem을 Map으로 변환
     */
    private Map<String, Object> convertOrderItemToMap(OrderItem orderItem) {
        Map<String, Object> item = new HashMap<>();
        item.put("orderGoodsId", orderItem.getId().toString());
        item.put("itemType", orderItem.getOrderItemType().toString());
        item.put("scheduleId", orderItem.getSessionOptionId() != null ? orderItem.getSessionOptionId().toString() : null);
        item.put("goodsId", orderItem.getGoodsId() != null ? orderItem.getGoodsId().toString() : null);
        item.put("qty", orderItem.getQty());
        item.put("unitPrice", orderItem.getUnitPrice());
        item.put("linePrice", orderItem.getLineAmount());
        return item;
    }
}