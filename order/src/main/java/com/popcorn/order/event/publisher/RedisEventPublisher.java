package com.popcorn.order.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.event.order.OrderPaidEvent;
import com.popcorn.order.event.store.StoreRequestEvent;
import com.popcorn.order.event.stock.StockDeductionRequestedEvent;
import com.popcorn.order.event.schedule.ScheduleReservationRequestedEvent;
import com.popcorn.order.event.schedule.ScheduleReservationCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Redis Stream을 사용한 이벤트 퍼블리셔
 *
 * Redis Stream 방식으로 마이크로서비스 간 이벤트 통신 처리
 * - 메시지 지속성 보장 (Pub/Sub은 휘발성)
 * - Consumer Group을 통한 부하 분산
 * - 메시지 ACK 및 재처리 지원
 * - eventType 필수 검증 지원
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis Stream 이름 상수 - 동기 통신으로 변경된 기능들의 스트림 제거
    private static final String ORDER_EVENTS_STREAM = "order-events";    // 주문 생성, 결제 완료 등
    private static final String SCHEDULE_EVENTS_STREAM = "schedule-events";  // 스케줄 예약
    private static final String GOODS_EVENTS_STREAM = "goods-events";        // 굿즈 예약
    private static final String MIXED_EVENTS_STREAM = "mixed-events";        // 복합형 (스케줄+굿즈)
    private static final String STOCK_EVENTS_STREAM = "stock-events";        // 재고 차감
    // 가격 조회는 HTTP 동기 방식으로 변경 - PRICE_EVENTS_STREAM 제거
    private static final String PAYMENT_EVENTS_STREAM = "payment-events";    // 결제
    private static final String STORE_LOOKUP_STREAM = "store-lookup-events"; // 팝업 정보 조회 (필요시만 사용)

    /**
     * 주문 결제 완료 이벤트 발행 (Store 서비스에서 수신)
     */
    public void publishOrderPaidEvent(OrderPaidEvent event) {
        try {
            log.info("주문 결제 완료 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            java.util.Map<String, String> eventData = new java.util.HashMap<>();
            eventData.put("eventType", "order-paid");
            eventData.put("orderId", event.getOrderId().toString());
            eventData.put("eventId", event.getEventId().toString());
            eventData.put("orderNo", event.getOrderNo());
            eventData.put("userId", event.getUserId() != null ? event.getUserId().toString() : "");
            eventData.put("popupId", event.getPopupId() != null ? event.getPopupId().toString() : "");
            eventData.put("orderType", event.getOrderType() != null ? event.getOrderType() : "");
            eventData.put("totalAmount", event.getTotalAmount() != null ? event.getTotalAmount().toString() : "");
            eventData.put("orderItems", objectMapper.writeValueAsString(
                    event.getOrderItems() != null ? event.getOrderItems() : java.util.List.of()
            ));
            eventData.put("paidAt", event.getPaidAt().toString());
            eventData.put("eventTime", LocalDateTime.now().toString());

            // eventType 검증 및 통합 발행
            validateAndPublish(eventData, ORDER_EVENTS_STREAM, "publishOrderPaidEvent");

        } catch (Exception e) {
            log.error("주문 결제 완료 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("주문 결제 완료 이벤트 Stream 발행 실패", e);
        }
    }


    /**
     * 재고 차감 요청 이벤트 발행 (Store 서비스에서 수신)
     */
    public void publishStockDeductionRequestedEvent(StockDeductionRequestedEvent event) {
        try {
            log.info("재고 차감 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            Map<String, String> eventData = Map.of(
                "eventType", "stock-deduction-requested",
                "eventId", event.getEventId().toString(),
                "orderId", event.getOrderId().toString(),
                "orderNo", event.getOrderNo() != null ? event.getOrderNo() : "",
                "items", objectMapper.writeValueAsString(event.getDeductionItems() != null ? event.getDeductionItems() : "[]"),
                "requestedAt", event.getRequestedAt().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(STOCK_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("재고 차감 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("재고 차감 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("재고 차감 요청 이벤트 Stream 발행 실패", e);
        }
    }

    /**
     * 굿즈 예약 취소 요청 이벤트 발행 (Store 서비스에서 수신)
     */
    public void publishGoodsReservationCancelRequestedEvent(String eventId,
                                                            java.util.UUID orderId,
                                                            java.util.UUID popupId,
                                                            java.util.UUID goodsId,
                                                            Integer quantity) {
        try {
            log.info("굿즈 예약 취소 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    orderId, eventId);

            Map<String, String> eventData = Map.of(
                "eventType", "goods-reservation-cancel-requested",
                "eventId", eventId,
                "orderId", orderId.toString(),
                "popupId", popupId != null ? popupId.toString() : "",
                "goodsId", goodsId != null ? goodsId.toString() : "",
                "quantity", quantity != null ? quantity.toString() : "",
                "requestedAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(GOODS_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("굿즈 예약 취소 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);

        } catch (Exception e) {
            log.error("굿즈 예약 취소 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    orderId, eventId, e.getMessage(), e);
            throw new RuntimeException("굿즈 예약 취소 요청 이벤트 Stream 발행 실패", e);
        }
    }

    /**
     * 결제 생성 요청 이벤트 발행 (Payment 서비스에서 수신)
     */
    public void publishPaymentCreateRequestedEvent(String eventId,
                                                   java.util.UUID orderId,
                                                   String orderNo,
                                                   Integer amount,
                                                   String paymentMethod,
                                                   Long customerId,
                                                   String paymentKey) {
        try {
            log.info("결제 생성 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    orderId, eventId);

            Map<String, String> eventData = Map.of(
                "eventType", "payment-create-requested",
                "eventId", eventId,
                "orderId", orderId.toString(),
                "orderNo", orderNo != null ? orderNo : "",
                "amount", amount != null ? amount.toString() : "",
                "paymentMethod", paymentMethod != null ? paymentMethod : "",
                "customerId", customerId != null ? customerId.toString() : "",
                "paymentKey", paymentKey != null ? paymentKey : "",
                "requestedAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(PAYMENT_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("결제 생성 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);

        } catch (Exception e) {
            log.error("결제 생성 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    orderId, eventId, e.getMessage(), e);
            throw new RuntimeException("결제 생성 요청 이벤트 Stream 발행 실패", e);
        }
    }

    /**
     * 결제 취소 요청 이벤트 발행 (Payment 서비스에서 수신)
     */
    public void publishPaymentCancelRequestedEvent(String eventId,
                                                   java.util.UUID orderId,
                                                   String orderNo,
                                                   String paymentId,
                                                   String reason,
                                                   Long customerId) {
        try {
            log.info("결제 취소 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    orderId, eventId);

            Map<String, String> eventData = Map.of(
                "eventType", "payment-cancel-requested",
                "eventId", eventId,
                "orderId", orderId.toString(),
                "orderNo", orderNo != null ? orderNo : "",
                "paymentId", paymentId != null ? paymentId : "",
                "reason", reason != null ? reason : "",
                "customerId", customerId != null ? customerId.toString() : "",
                "requestedAt", LocalDateTime.now().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(PAYMENT_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("결제 취소 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    orderId, eventId);

        } catch (Exception e) {
            log.error("결제 취소 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    orderId, eventId, e.getMessage(), e);
            throw new RuntimeException("결제 취소 요청 이벤트 Stream 발행 실패", e);
        }
    }

    // 팝업 정보 조회 요청 이벤트 발행 메서드 제거됨 (HTTP 동기 방식으로 변경)

    // 가격 조회 요청 이벤트 발행 메서드 제거됨 (HTTP 동기 방식으로 변경)

    // 사용자 주소 조회 요청 이벤트 발행 메서드 제거됨 (HTTP 동기 방식으로 변경)

    // ================ 📅 스케줄 예약 관련 이벤트 발행 메소드들 ================

    /**
     * 스케줄 예약 요청 이벤트 발행 (Store 서비스에서 수신)
     */
    public void publishScheduleReservationRequestedEvent(ScheduleReservationRequestedEvent event) {
        try {
            log.info("📅 [ORDER→STORE] 스케줄 예약 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            // 예약 항목들을 JSON으로 직렬화
            String reservationItemsJson = objectMapper.writeValueAsString(event.getReservationItems());

            Map<String, String> eventData = Map.of(
                "eventType", "schedule-reservation-requested",
                "eventId", event.getEventId().toString(),
                "orderId", event.getOrderId().toString(),
                "orderNo", event.getOrderNo(),
                "popupId", event.getPopupId().toString(),
                "reservationItems", reservationItemsJson,
                "requestedAt", event.getRequestedAt().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(SCHEDULE_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("📅✅ [ORDER→STORE] 스케줄 예약 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("📅❌ [ORDER→STORE] 스케줄 예약 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("스케줄 예약 요청 이벤트 Stream 발행 실패", e);
        }
    }

    /**
     * 스케줄 예약 취소 요청 이벤트 발행 (Store 서비스에서 수신)
     */
    public void publishScheduleReservationCancelRequestedEvent(ScheduleReservationCancelRequestedEvent event) {
        try {
            log.info("📅 [ORDER→STORE] 스케줄 예약 취소 요청 이벤트 Stream 발행 시작 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

            // 취소 항목들을 JSON으로 직렬화
            String cancelItemsJson = objectMapper.writeValueAsString(event.getCancelItems());

            Map<String, String> eventData = Map.of(
                "eventType", "schedule-reservation-cancel-requested",
                "eventId", event.getEventId().toString(),
                "orderId", event.getOrderId().toString(),
                "orderNo", event.getOrderNo(),
                "popupId", event.getPopupId().toString(),
                "reservationToken", event.getReservationToken() != null ? event.getReservationToken() : "",
                "cancelItems", cancelItemsJson,
                "cancelReason", event.getCancelReason(),
                "cancelRequestedAt", event.getCancelRequestedAt().toString(),
                "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey(SCHEDULE_EVENTS_STREAM);
            redisTemplate.opsForStream().add(record);

            log.info("📅✅ [ORDER→STORE] 스케줄 예약 취소 요청 이벤트 Stream 발행 완료 - orderId: {}, eventId: {}",
                    event.getOrderId(), event.getEventId());

        } catch (Exception e) {
            log.error("📅❌ [ORDER→STORE] 스케줄 예약 취소 요청 이벤트 Stream 발행 실패 - orderId: {}, eventId: {}, error: {}",
                    event.getOrderId(), event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("스케줄 예약 취소 요청 이벤트 Stream 발행 실패", e);
        }
    }


    /**
     * 스케줄 확정 요청 이벤트 Stream 발행 (결제 완료 후)
     * Store 서비스로 스케줄 예약 → 확정 변경 요청 전송
     */
    public void publishScheduleConfirmationRequestedEvent(
            String eventId,
            java.util.UUID orderId,
            String orderNo,
            java.util.UUID popupId,
            java.util.List<com.popcorn.order.service.core.OrderCommandService.ScheduleConfirmationItem> confirmationItems) {

        try {
            log.warn("🔥 [DEBUG] 스케줄 확정 요청 이벤트 Stream 발행 시작 - eventId: {}, orderId: {}",
                    eventId, orderId);

            // 확정 항목들을 JSON 형태로 직렬화
            String confirmationItemsJson = objectMapper.writeValueAsString(
                confirmationItems.stream()
                    .map(item -> java.util.Map.of(
                        "scheduleId", item.getScheduleId().toString(),
                        "quantity", item.getQuantity().toString(),
                        "sessionName", item.getSessionName() != null ? item.getSessionName() : "",
                        "sessionTime", item.getSessionTime() != null ? item.getSessionTime() : ""
                    ))
                    .toList()
            );

            // Stream 이벤트 데이터 구성
            java.util.Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("eventType", "schedule-confirmation-requested");
            eventData.put("eventId", eventId);
            eventData.put("orderId", orderId.toString());
            eventData.put("orderNo", orderNo);
            eventData.put("popupId", popupId != null ? popupId.toString() : "");
            eventData.put("confirmationItems", confirmationItemsJson);
            eventData.put("requestedAt", java.time.LocalDateTime.now().toString());
            eventData.put("eventTime", java.time.LocalDateTime.now().toString());

            log.warn("🔥 [DEBUG] 스케줄 확정 이벤트 데이터: {}", eventData);

            // String 값으로 변환
            java.util.Map<String, String> stringEventData = eventData.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                    ));

            // Redis Stream Record 생성 및 발행
            StringRecord record = StreamRecords.string(stringEventData)
                    .withStreamKey("schedule-events");

            log.warn("🔥 [DEBUG] StringRecord 생성 완료");

            String recordId = redisTemplate.opsForStream().add(record).getValue();

            log.warn("🔥 [DEBUG] Redis Stream 발행 완료 - recordId: {}", recordId);
            log.info("✅ 스케줄 확정 요청 이벤트 Stream 발행 완료 - eventId: {}, orderId: {}, recordId: {}",
                    eventId, orderId, recordId);

        } catch (Exception e) {
            log.error("❌ [ORDER→STORE] 스케줄 확정 요청 이벤트 Stream 발행 실패 - orderId: {}, error: {}",
                    orderId, e.getMessage(), e);
            throw new RuntimeException("스케줄 확정 요청 이벤트 Stream 발행 실패", e);
        }
    }

    /**
     * 이벤트 데이터에서 eventType 필수 검증
     * 모든 Redis Stream 이벤트는 반드시 eventType을 포함해야 함
     */
    private void validateEventType(Map<String, String> eventData, String methodName) {
        String eventType = eventData.get("eventType");

        if (eventType == null || eventType.trim().isEmpty()) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 누락되었습니다! method=%s, eventData=%s",
                methodName, eventData
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + methodName);
        }

        // eventType 형식 검증 (kebab-case)
        if (!eventType.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            String errorMessage = String.format(
                "[CRITICAL] eventType 형식이 잘못되었습니다! eventType=%s, method=%s (kebab-case 형식 필요)",
                eventType, methodName
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 kebab-case 형식이어야 합니다: " + eventType);
        }

        log.debug("✅ eventType 검증 통과: {} (method: {})", eventType, methodName);
    }

    /**
     * Redis Stream 발행 전 통합 검증
     */
    private void validateAndPublish(Map<String, String> eventData, String streamName, String methodName) {
        // 1. eventType 필수 검증
        validateEventType(eventData, methodName);

        // 2. 기본 필수 필드 검증
        if (!eventData.containsKey("eventId") || eventData.get("eventId").trim().isEmpty()) {
            throw new IllegalArgumentException("eventId는 필수 항목입니다: " + methodName);
        }

        if (!eventData.containsKey("eventTime") || eventData.get("eventTime").trim().isEmpty()) {
            throw new IllegalArgumentException("eventTime은 필수 항목입니다: " + methodName);
        }

        // 3. Redis Stream 발행
        try {
            StringRecord record = StreamRecords.string(eventData).withStreamKey(streamName);
            String recordId = redisTemplate.opsForStream().add(record).getValue();

            log.info("✅ [{}] 이벤트 Stream 발행 성공: eventType={}, recordId={}, stream={}",
                    methodName, eventData.get("eventType"), recordId, streamName);

        } catch (Exception e) {
            log.error("❌ [{}] 이벤트 Stream 발행 실패: eventType={}, stream={}, error={}",
                    methodName, eventData.get("eventType"), streamName, e.getMessage());
            throw new RuntimeException("Redis Stream 발행 실패: " + methodName, e);
        }
    }

    /**
     * 🏪 통합 Store 요청 이벤트 Redis Stream 발행
     *
     * @param event Store 요청 이벤트
     */
    public void publishStoreRequestEvent(StoreRequestEvent event) {
        try {
            log.info("🏪 [UNIFIED-STORE] Store 요청 이벤트 Stream 발행 시작 - orderId: {}, requestType: {}",
                    event.getOrderId(), event.getRequestType());

            Map<String, String> eventData = Map.of(
                    "eventType", event.getEventType(),
                    "eventId", event.getEventId().toString(),
                    "orderId", event.getOrderId().toString(),
                    "orderNo", event.getOrderNo() != null ? event.getOrderNo() : "",
                    "popupId", event.getPopupId() != null ? event.getPopupId().toString() : "",
                    "requestType", event.getRequestType().name(),
                    "itemCount", String.valueOf(event.getRequestItems().size()),
                    "requestedAt", event.getRequestedAt().toString(),
                    "eventTime", LocalDateTime.now().toString()
            );

            StringRecord record = StreamRecords.string(eventData).withStreamKey("order-store-requests");
            redisTemplate.opsForStream().add(record);

            log.info("✅ [UNIFIED-STORE] Store 요청 이벤트 Stream 발행 완료 - orderId: {}, requestType: {}",
                    event.getOrderId(), event.getRequestType());

        } catch (Exception e) {
            log.error("❌ [UNIFIED-STORE] Store 요청 이벤트 Stream 발행 실패 - orderId: {}, requestType: {}, error: {}",
                    event.getOrderId(), event.getRequestType(), e.getMessage(), e);
            throw new RuntimeException("통합 Store 요청 이벤트 Stream 발행 실패", e);
        }
    }
}
