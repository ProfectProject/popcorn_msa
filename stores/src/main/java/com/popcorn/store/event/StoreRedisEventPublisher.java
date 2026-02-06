package com.popcorn.store.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.event.order.StockDeductionFailedEvent;
import com.popcorn.store.event.order.StockDeductionSuccessEvent;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.GoodsHoldItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stores 서비스 Redis Stream 이벤트 발행자
 * 재고 차감 결과 및 가격 조회 응답을 다른 마이크로서비스들에게 전파
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreRedisEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Stream 이름 상수
    private static final String STOCK_EVENTS_STREAM = "stock-events";
    private static final String RESPONSE_EVENTS_STREAM = "response-events";
    private static final String STORE_LOOKUP_RESPONSES_STREAM = "store-lookup-responses";
    private static final String INVENTORY_EVENTS_STREAM = "inventory-events";
    private static final String GOODS_EVENTS_STREAM = "goods-events";
    private static final String MIXED_EVENTS_STREAM = "mixed-events";  // 복합형 예약 결과
    private static final String SCHEDULE_EVENTS_STREAM = "schedule-events";

    /**
     * 재고 차감 성공 이벤트 발행
     */
    public void publishStockDeductionSuccessEvent(StockDeductionSuccessEvent event) {
        try {
            log.info("🚀 [STORES] 재고 차감 성공 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.STOCK_DEDUCTION_SUCCEEDED,
                "eventId", event.getEventId(),
                "orderId", event.getOrderId().toString(),
                "orderNo", event.getOrderNo(),
                "popupId", event.getPopupId().toString(),
                "stockDetails", event.getStockDetails(),
                "succeededAt", event.getSucceededAt().toString(),
                "eventTime", java.time.LocalDateTime.now().toString()
            );

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(STOCK_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("✅ [STORES] 재고 차감 성공 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("❌ [STORES] 재고 차감 성공 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 실패 이벤트 발행
     */
    public void publishStockDeductionFailedEvent(StockDeductionFailedEvent event) {
        try {
            log.info("🚀 [STORES] 재고 차감 실패 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            Map<String, Object> eventData = new java.util.LinkedHashMap<>();
            eventData.put("eventType", EventConstants.EventTypes.STOCK_DEDUCTION_FAILED);
            eventData.put("eventId", event.getEventId());
            eventData.put("orderId", event.getOrderId().toString());
            eventData.put("orderNo", event.getOrderNo() != null ? event.getOrderNo() : "");
            eventData.put("reason", event.getReason() != null ? event.getReason() : "");
            eventData.put("failureCode", event.getFailureCode() != null ? event.getFailureCode() : "");
            eventData.put("failedAt", event.getFailedAt() != null ? event.getFailedAt().toString() : "");
            eventData.put("eventTime", java.time.LocalDateTime.now().toString());

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(STOCK_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("⚠️ [STORES] 재고 차감 실패 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}, reason: {}",
                    event.getOrderId(), event.getEventId(), event.getReason());

        } catch (Exception e) {
            log.error("❌ [STORES] 재고 차감 실패 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
        }
    }


    /**
     * 굿즈 예약 성공 이벤트 발행
     */
    public void publishGoodsReservedEvent(java.util.UUID orderId, String orderNo, java.util.UUID popupId,
                                        java.util.UUID goodsId, int quantity) {
        try {
            log.info("🚀 [STORES] 굿즈 예약 성공 이벤트 Stream 발행 - orderId: {}, goodsId: {}, quantity: {}",
                    orderId, goodsId, quantity);

            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.GOODS_RESERVATION_SUCCEEDED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "orderNo", orderNo != null ? orderNo : "",
                "popupId", popupId.toString(),
                "goodsId", goodsId.toString(),
                "quantity", Integer.toString(quantity),
                "reservedAt", java.time.LocalDateTime.now().toString(),
                "eventTime", java.time.LocalDateTime.now().toString()
            );

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(GOODS_EVENTS_STREAM);

            // 주문 생성 트랜잭션 커밋 이후에 전달되도록 딜레이 추가
            redisTemplate.opsForStream().add(record);

            log.info("✅ [STORES] 굿즈 예약 성공 이벤트 Stream 발행 완료 - eventId: {}", eventId);

        } catch (Exception e) {
            log.error("❌ [STORES] 굿즈 예약 성공 이벤트 Stream 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 굿즈 예약 실패 이벤트 발행
     */
    public void publishGoodsReservationFailedEvent(java.util.UUID orderId, java.util.UUID popupId,
                                                  java.util.UUID goodsId, int requestedQuantity,
                                                  int availableQuantity, String reason) {
        try {
            log.info("🚀 [STORES] 굿즈 예약 실패 이벤트 Stream 발행 - orderId: {}, goodsId: {}, reason: {}",
                    orderId, goodsId, reason);

            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.GOODS_RESERVATION_FAILED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "popupId", popupId != null ? popupId.toString() : "",
                "goodsId", goodsId.toString(),
                "requestedQuantity", Integer.toString(requestedQuantity),
                "availableQuantity", Integer.toString(availableQuantity),
                "reason", reason,
                "failedAt", java.time.LocalDateTime.now().toString(),
                "eventTime", java.time.LocalDateTime.now().toString()
            );

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(GOODS_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("⚠️ [STORES] 굿즈 예약 실패 이벤트 Stream 발행 완료 - eventId: {}, reason: {}", eventId, reason);

        } catch (Exception e) {
            log.error("❌ [STORES] 굿즈 예약 실패 이벤트 Stream 발행 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 가격 조회 응답 이벤트 발행
     */
    public void publishPriceLookupResponseEvent(PriceLookupResponseEventDto event) {
        try {
            log.info("🚀 [STORES] 가격 조회 응답 이벤트 Stream 발행 - correlationId: {}, type: {}",
                    event.getCorrelationId(), event.getRequestType());

            Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("eventType", EventConstants.EventTypes.PRICE_LOOKUP_RESPONSE);
            eventData.put("eventId", event.getEventId());
            eventData.put("correlationId", event.getCorrelationId());
            eventData.put("requestType", event.getRequestType());
            eventData.put("sessionId", event.getSessionId() != null ? event.getSessionId().toString() : "");
            eventData.put("goodsId", event.getGoodsId() != null ? event.getGoodsId().toString() : "");
            eventData.put("price", event.getPrice() != null ? event.getPrice().toString() : "");
            eventData.put("stockQuantity", event.getStockQuantity() != null ? event.getStockQuantity().toString() : "");
            eventData.put("success", Boolean.toString(event.isSuccess()));
            eventData.put("message", event.getMessage() != null ? event.getMessage() : "");
            eventData.put("respondedAt", event.getRespondedAt().toString());
            eventData.put("eventTime", event.getEventTime().toString());

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(RESPONSE_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("✅ [STORES] 가격 조회 응답 이벤트 Stream 발행 완료 - correlationId: {}, success: {}",
                    event.getCorrelationId(), event.isSuccess());

        } catch (Exception e) {
            log.error("❌ [STORES] 가격 조회 응답 이벤트 Stream 발행 실패 - correlationId: {}, error: {}",
                    event.getCorrelationId(), e.getMessage(), e);
        }
    }

    /**
     * 팝업 정보 조회 응답 이벤트 발행
     */
    public void publishPopupInfoLookupResponseEvent(Map<String, Object> eventData) {
        try {
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            stringEventData.put("eventTime", LocalDateTime.now().toString());

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(STORE_LOOKUP_RESPONSES_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("🏬 [STORES] 팝업 정보 조회 응답 이벤트 Stream 발행 완료 - correlationId: {}",
                    stringEventData.get("correlationId"));

        } catch (Exception e) {
            log.error("🏬 [STORES] 팝업 정보 조회 응답 이벤트 발행 실패 - error: {}", e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 성공 이벤트 발행 (스케줄 + 굿즈)
     */
    public void publishMixedReservationSuccessEvent(
            java.util.UUID orderId, java.util.UUID popupId, java.util.UUID scheduleId,
            Integer scheduleQty, java.util.List<GoodsHoldItem> goodsHoldItems) {
        try {
            log.info("🔗✅ [STORE→ORDER] 복합형 예약 성공 이벤트 발행 시작 - orderId: {}", orderId);

            // 굿즈 항목들을 JSON으로 변환
            String goodsItemsJson = objectMapper.writeValueAsString(goodsHoldItems);

            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.MIXED_RESERVATION_SUCCEEDED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "popupId", popupId != null ? popupId.toString() : "",
                "scheduleId", scheduleId.toString(),
                "scheduleQty", scheduleQty.toString(),
                "goodsItems", goodsItemsJson,
                "successAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(MIXED_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("🔗✅ [STORE→ORDER] 복합형 예약 성공 이벤트 발행 완료 - orderId: {}, eventId: {}", orderId, eventId);

        } catch (Exception e) {
            log.error("🔗❌ [STORE→ORDER] 복합형 예약 성공 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 실패 이벤트 발행 (스케줄 + 굿즈)
     */
    public void publishMixedReservationFailedEvent(java.util.UUID orderId, java.util.UUID popupId, String reason) {
        try {
            log.info("🔗❌ [STORE→ORDER] 복합형 예약 실패 이벤트 발행 시작 - orderId: {}, reason: {}", orderId, reason);

            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.MIXED_RESERVATION_FAILED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "popupId", popupId != null ? popupId.toString() : "",
                "reason", reason,
                "failedAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            // Map<String, Object>를 Map<String, String>으로 변환
            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(MIXED_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("🔗❌ [STORE→ORDER] 복합형 예약 실패 이벤트 발행 완료 - orderId: {}, eventId: {}", orderId, eventId);

        } catch (Exception e) {
            log.error("🔗🚨 [STORE→ORDER] 복합형 예약 실패 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 예약 성공 이벤트 발행
     */
    public void publishScheduleReservationSuccessEvent(
            java.util.UUID orderId, String orderNo, java.util.UUID popupId,
            String reservedSessionsJson, String reservationToken,
            LocalDateTime reservedAt, LocalDateTime expiresAt) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.SCHEDULE_RESERVATION_SUCCEEDED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "orderNo", orderNo,
                "popupId", popupId != null ? popupId.toString() : "",
                "reservedSessions", reservedSessionsJson,
                "reservationToken", reservationToken != null ? reservationToken : "",
                "reservedAt", reservedAt.toString(),
                "expiresAt", expiresAt.toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(SCHEDULE_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("📅✅ [STORE→ORDER] 스케줄 예약 성공 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);
        } catch (Exception e) {
            log.error("📅❌ [STORE→ORDER] 스케줄 예약 성공 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 예약 실패 이벤트 발행
     */
    public void publishScheduleReservationFailedEvent(
            java.util.UUID orderId, String orderNo, java.util.UUID popupId,
            String failedSessionsJson, String failureReason) {
        try {
            String eventId = java.util.UUID.randomUUID().toString();
            Map<String, Object> eventData = Map.of(
                "eventType", EventConstants.EventTypes.SCHEDULE_RESERVATION_FAILED,
                "eventId", eventId,
                "orderId", orderId.toString(),
                "orderNo", orderNo,
                "popupId", popupId != null ? popupId.toString() : "",
                "failedSessions", failedSessionsJson,
                "failureReason", failureReason != null ? failureReason : "",
                "failedAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            Map<String, String> stringEventData = eventData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));

            StringRecord record = StreamRecords.string(stringEventData).withStreamKey(SCHEDULE_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("📅❌ [STORE→ORDER] 스케줄 예약 실패 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);
        } catch (Exception e) {
            log.error("📅❌ [STORE→ORDER] 스케줄 예약 실패 이벤트 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
        }
    }

    // DTO classes for Redis Stream serialization (only for complex responses)

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PriceLookupResponseEventDto {
        private String eventId;
        private String correlationId;
        private String requestType;
        private java.util.UUID sessionId;
        private java.util.UUID goodsId;
        private Integer price;
        private Integer stockQuantity;
        private boolean success;
        private String message;
        private java.time.LocalDateTime respondedAt;
        private java.time.LocalDateTime eventTime;
    }
}
