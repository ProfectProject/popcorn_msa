package com.popcorn.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.dto.user.UserAddressResponse;
import com.popcorn.order.entity.ItemType;
import com.popcorn.order.entity.OrderStatus;
import com.popcorn.order.event.PopupInfoLookupResponseEvent;
import com.popcorn.order.repository.OrderRepository;
import com.popcorn.order.service.OrderCommandService;
import com.popcorn.order.service.OrderInfoResponseService;
import com.popcorn.order.service.PaymentCacheService;
import com.popcorn.order.service.OrderCacheService;
import com.popcorn.order.service.OrderPopupLookupService;
import com.popcorn.order.service.OrderPriceLookupService;
import com.popcorn.order.service.OrderUserLookupService;
import com.popcorn.order.service.OrderUserAddressCacheService;
import com.popcorn.order.service.OrderReservationAwaiter;
import com.popcorn.order.service.OrderIdempotencyService;
import com.popcorn.order.dto.payment.PaymentUrlResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Order 서비스 Redis Stream 이벤트 리스너
 *
 * 다른 마이크로서비스로부터 오는 응답 이벤트를 수신하고 처리
 */
@Component
@RequiredArgsConstructor
public class OrderRedisStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(OrderRedisStreamListener.class);

    private final OrderPriceLookupService orderPriceLookupService;
    private final OrderUserLookupService orderUserLookupService;
    private final OrderUserAddressCacheService orderUserAddressCacheService;
    private final OrderCommandService orderCommandService;
    private final OrderRepository orderRepository;
    private final OrderPopupLookupService orderPopupLookupService;
    private final OrderReservationAwaiter orderReservationAwaiter;
    private final OrderInfoResponseService orderInfoResponseService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCacheService paymentCacheService;
    private final OrderCacheService orderCacheService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "order-redis-retry");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            String streamName = record.getStream();
            String recordId = record.getId().getValue();

            Map<String, Object> values = new HashMap<>(record.getValue());

            // eventType 필수 검증
            String eventType = validateEventType(values, streamName, recordId);

            log.info("🔔 [ORDER] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                    streamName, recordId, eventType);

            if ("order:query:stream".equals(streamName)) {
                log.info("🔍 [ORDER] 주문 조회 요청 이벤트 수신");
                handleOrderQueryRequest(values);
                log.debug("✅ [ORDER] 주문 조회 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            if ("order:update:stream".equals(streamName)) {
                log.info("🔄 [ORDER] 주문 상태 업데이트 요청 이벤트 수신");
                handleOrderStatusUpdateRequest(values);
                log.debug("✅ [ORDER] 주문 상태 업데이트 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            if ("order-info-requests".equals(streamName)) {
                log.info("📨 [ORDER] Order 정보 요청 이벤트 수신");
                orderInfoResponseService.handleOrderInfoRequest(values);
                log.debug("✅ [ORDER] Order 정보 요청 처리 완료 - recordId: {}", recordId);
                return;
            }

            handleStreamEvent(eventType, values);

            log.debug("✅ [ORDER] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId);

        } catch (Exception e) {
            log.error("🚨 [ORDER] Stream 메시지 처리 실패 - record: {}, error: {}",
                    record, e.getMessage(), e);

            // 실패한 메시지를 DLQ로 이동
            sendToDeadLetterQueue(record, e);
        }
    }

    private void handleOrderQueryRequest(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String correlationId = (String) values.get("correlationId");

            if (orderIdStr == null || orderIdStr.isBlank()) {
                log.warn("🔍 [ORDER] 주문 조회 요청 필수 데이터 누락 - values: {}", values);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            orderRepository.findById(orderId).ifPresentOrElse(order -> {
                try {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", order.getId());
                    response.put("orderNo", order.getOrderNo());
                    response.put("customerId", order.getCustomerId());
                    response.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount() : 0);
                    response.put("status", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
                    response.put("orderType", order.getOrderType() != null ? order.getOrderType().name() : "UNKNOWN");
                    response.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

                    String cacheKey = "order:cache:" + orderId;
                    String json = objectMapper.writeValueAsString(response);
                    redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(30));

                    log.info("✅ [ORDER] 주문 조회 응답 캐시 저장 - orderId: {}, correlationId: {}",
                            orderId, correlationId);
                } catch (Exception e) {
                    log.error("🚨 [ORDER] 주문 조회 응답 캐시 저장 실패 - orderId: {}, error: {}",
                            orderId, e.getMessage(), e);
                }
            }, () -> log.warn("🔍 [ORDER] 주문 조회 요청 대상 없음 - orderId: {}", orderIdStr));
        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 조회 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    private String normalizeUuidString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private void handleOrderStatusUpdateRequest(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String newStatus = (String) values.get("newStatus");
            String reason = (String) values.get("reason");
            String correlationId = (String) values.get("correlationId");

            if (orderIdStr == null || orderIdStr.isBlank() || newStatus == null) {
                log.warn("🔄 [ORDER] 주문 상태 업데이트 요청 필수 데이터 누락 - values: {}", values);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            orderCommandService.updateOrderStatus(orderId, newStatus, reason != null ? reason : "");

            String responseKey = "order:update:response:" + correlationId;
            redisTemplate.opsForValue().set(responseKey, "OK", Duration.ofSeconds(30));

            log.info("✅ [ORDER] 주문 상태 업데이트 응답 저장 - orderId: {}, correlationId: {}",
                    orderId, correlationId);
        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 상태 업데이트 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * Stream 이벤트 타입별 처리
     */
    private void handleStreamEvent(String eventType, Map<String, Object> values) {
        try {
            switch (eventType) {
                case "price-lookup-response":
                    log.info("💰 [ORDER] 가격 조회 응답 이벤트 수신");
                    handlePriceLookupResponse(values);
                    break;
                case "user-address-lookup-response":
                    log.info("🏠 [ORDER] 사용자 주소 조회 응답 이벤트 수신");
                    handleUserAddressLookupResponse(values);
                    break;
                case "popup-info-lookup-response":
                    log.info("🏬 [ORDER] 팝업 정보 조회 응답 이벤트 수신");
                    handlePopupInfoLookupResponse(values);
                    break;
                case "payment-approved":
                    log.info("💳 [ORDER] 결제 승인 이벤트 수신");
                    handlePaymentApproved(values);
                    break;
                case "payment-completed":
                    log.info("🔕 [ORDER] 결제 완료 이벤트 무시 (payment-approved에서 이미 처리됨)");
                    // handlePaymentCompleted(values); // 중복 처리 방지를 위해 비활성화
                    break;
                case "payment-failed":
                    log.info("❌ [ORDER] 결제 실패 이벤트 수신");
                    handlePaymentFailed(values);
                    break;
                case "payment-cancelled":
                    log.info("↩️ [ORDER] 결제 취소 이벤트 수신");
                    handlePaymentCancelled(values);
                    break;
                case "order-paid":
                    log.info("💳✅ [ORDER] 주문 결제 완료 이벤트 수신 - 재고 차감 시작");
                    handleOrderPaid(values);
                    break;
                case "schedule-reservation-requested":
                    // Order가 발행한 요청 이벤트이므로 무시
                    log.debug("🔕 [ORDER] 스케줄 예약 요청 이벤트 무시 - eventId: {}",
                            values.get("eventId"));
                    break;
                case "schedule-reservation-success":
                    log.info("📅✅ [ORDER] 스케줄 예약 성공 이벤트 수신");
                    handleScheduleReservationSuccess(values);
                    break;
                case "schedule-reservation-failed":
                    log.info("📅❌ [ORDER] 스케줄 예약 실패 이벤트 수신");
                    handleScheduleReservationFailed(values);
                    break;
                case "stock-deduction-success":
                    log.info("📦✅ [ORDER] 재고 차감 성공 이벤트 수신");
                    handleStockDeductionSuccess(values);
                    break;
                case "stock-deduction-failed":
                    log.info("📦❌ [ORDER] 재고 차감 실패 이벤트 수신");
                    handleStockDeductionFailed(values);
                    break;
                case "stock-deduction-requested":
                    // Order가 발행한 이벤트이므로 수신 시 무시
                    log.debug("🔕 [ORDER] 재고 차감 요청 이벤트 무시 - eventId: {}",
                            values.get("eventId"));
                    break;
                case "goods-reserved":
                    log.info("📦✅ [ORDER] 굿즈 예약 성공 이벤트 수신");
                    handleGoodsReserved(values);
                    break;
                case "goods-reservation-failed":
                    log.info("📦❌ [ORDER] 굿즈 예약 실패 이벤트 수신");
                    handleGoodsReservationFailed(values);
                    break;
                case "goods-reservation-cancel-requested":
                    // Order가 발행한 이벤트이므로 수신 시 무시
                    log.debug("🔕 [ORDER] 굿즈 예약 취소 요청 이벤트 무시 - eventId: {}",
                            values.get("eventId"));
                    break;
                case "payment-create-requested":
                    // Order가 발행한 이벤트이므로 수신 시 무시
                    log.debug("🔕 [ORDER] 결제 생성 요청 이벤트 무시 - eventId: {}",
                            values.get("eventId"));
                    break;
                case "goods-reservation-requested":
                    // Order가 발행한 이벤트이므로 수신 시 무시
                    log.debug("🔕 [ORDER] 굿즈 재고 예약 요청 이벤트 무시 - eventId: {}",
                            values.get("eventId"));
                    break;
                case "mixed-reservation-success":
                    log.info("🔗✅ [ORDER] 복합형 예약 성공 이벤트 수신");
                    handleMixedReservationSuccess(values);
                    break;
                case "mixed-reservation-failed":
                    log.info("🔗❌ [ORDER] 복합형 예약 실패 이벤트 수신");
                    handleMixedReservationFailed(values);
                    break;
                default:
                    log.debug("🔔 [ORDER] 알 수 없는 이벤트 타입 - type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("🚨 [ORDER] 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.getMessage(), e);
        }
    }

    private void handleScheduleReservationSuccess(Map<String, Object> values) {
        try {
            String eventId = normalizeQuotedString((String) values.get("eventId"));
            String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String popupIdStr = normalizeQuotedString((String) values.get("popupId"));
            String reservedSessionsJson = normalizeJsonString((String) values.get("reservedSessions"));
            String reservationToken = normalizeQuotedString((String) values.get("reservationToken"));
            String reservedAtStr = normalizeQuotedString((String) values.get("reservedAt"));
            String expiresAtStr = normalizeQuotedString((String) values.get("expiresAt"));

            List<Map<String, Object>> reservedSessionsRaw = objectMapper.readValue(
                    reservedSessionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
            );

            List<ScheduleReservationSuccessEvent.ReservedSession> sessions = new ArrayList<>();
            for (Map<String, Object> raw : reservedSessionsRaw) {
                String sessionIdStr = String.valueOf(raw.get("sessionOptionId"));
                String reservedQtyStr = String.valueOf(raw.get("reservedQuantity"));
                String sessionName = raw.get("sessionName") != null ? String.valueOf(raw.get("sessionName")) : null;
                String sessionTimeStr = raw.get("sessionTime") != null ? String.valueOf(raw.get("sessionTime")) : null;
                String remainingStr = raw.get("remainingSeats") != null ? String.valueOf(raw.get("remainingSeats")) : null;
                String reservationCode = raw.get("reservationCode") != null ? String.valueOf(raw.get("reservationCode")) : null;

                sessions.add(ScheduleReservationSuccessEvent.ReservedSession.create(
                        UUID.fromString(sessionIdStr),
                        parseInt(reservedQtyStr),
                        sessionName,
                        sessionTimeStr != null && !sessionTimeStr.isBlank()
                                ? java.time.LocalDateTime.parse(sessionTimeStr)
                                : null,
                        parseInt(remainingStr),
                        reservationCode
                ));
            }

            ScheduleReservationSuccessEvent event = ScheduleReservationSuccessEvent.builder()
                    .eventId(eventId)
                    .orderId(UUID.fromString(orderIdStr))
                    .orderNo(orderNo)
                    .popupId(popupIdStr != null && !popupIdStr.isBlank() ? UUID.fromString(popupIdStr) : null)
                    .reservedSessions(sessions)
                    .reservationToken(reservationToken)
                    .reservedAt(reservedAtStr != null && !reservedAtStr.isBlank()
                            ? java.time.LocalDateTime.parse(reservedAtStr) : java.time.LocalDateTime.now())
                    .expiresAt(expiresAtStr != null && !expiresAtStr.isBlank()
                            ? java.time.LocalDateTime.parse(expiresAtStr) : java.time.LocalDateTime.now().plusMinutes(30))
                    .build();

            eventPublisher.publishEvent(event);

            // ✅ 스케줄 예약 완료 신호를 OrderReservationAwaiter에 전송
            UUID orderId = UUID.fromString(orderIdStr);
            orderRepository.findById(orderId).ifPresent(order -> {
                PaymentUrlResponse paymentUrl = orderCommandService.generatePaymentUrlAfterReservation(order);
                orderReservationAwaiter.completeSuccess(orderId, paymentUrl);
                log.info("📅✅ [ORDER] 스케줄 예약 완료 신호 전송 - orderId: {}, orderNo: {}", orderId, orderNo);
            });
        } catch (Exception e) {
            log.error("🚨 [ORDER] 스케줄 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);

            // ✅ 처리 실패 시에도 실패 신호를 전송하여 타임아웃 방지
            try {
                String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
                if (orderIdStr != null && !orderIdStr.isEmpty()) {
                    UUID orderId = UUID.fromString(orderIdStr);
                    orderReservationAwaiter.completeFailure(orderId, "스케줄 예약 성공 이벤트 처리 실패: " + e.getMessage());
                    log.info("📅❌ [ORDER] 스케줄 예약 처리 실패로 인한 실패 신호 전송 - orderId: {}", orderId);
                }
            } catch (Exception failureEx) {
                log.error("스케줄 예약 실패 신호 전송 중 오류 발생: {}", failureEx.getMessage(), failureEx);
            }
        }
    }

    private void handleScheduleReservationFailed(Map<String, Object> values) {
        try {
            String eventId = normalizeQuotedString((String) values.get("eventId"));
            String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String popupIdStr = normalizeQuotedString((String) values.get("popupId"));
            String failedSessionsJson = normalizeJsonString((String) values.get("failedSessions"));
            String failureReason = normalizeQuotedString((String) values.get("failureReason"));
            String failedAtStr = normalizeQuotedString((String) values.get("failedAt"));

            List<Map<String, Object>> failedSessionsRaw = objectMapper.readValue(
                    failedSessionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
            );

            List<ScheduleReservationFailedEvent.FailedSession> sessions = new ArrayList<>();
            for (Map<String, Object> raw : failedSessionsRaw) {
                String sessionIdStr = String.valueOf(raw.get("sessionOptionId"));
                String requestedQtyStr = String.valueOf(raw.get("requestedQuantity"));
                String availableQtyStr = String.valueOf(raw.get("availableQuantity"));
                String sessionName = raw.get("sessionName") != null ? String.valueOf(raw.get("sessionName")) : null;
                String sessionTimeStr = raw.get("sessionTime") != null ? String.valueOf(raw.get("sessionTime")) : null;
                String itemReason = raw.get("failureReason") != null ? String.valueOf(raw.get("failureReason")) : null;

                sessions.add(ScheduleReservationFailedEvent.FailedSession.create(
                        UUID.fromString(sessionIdStr),
                        parseInt(requestedQtyStr),
                        parseInt(availableQtyStr),
                        sessionName,
                        sessionTimeStr != null && !sessionTimeStr.isBlank()
                                ? java.time.LocalDateTime.parse(sessionTimeStr)
                                : null,
                        itemReason
                ));
            }

            ScheduleReservationFailedEvent event = ScheduleReservationFailedEvent.builder()
                    .eventId(eventId)
                    .orderId(UUID.fromString(orderIdStr))
                    .orderNo(orderNo)
                    .popupId(popupIdStr != null && !popupIdStr.isBlank() ? UUID.fromString(popupIdStr) : null)
                    .failedSessions(sessions)
                    .failureReason(failureReason)
                    .failedAt(failedAtStr != null && !failedAtStr.isBlank()
                            ? java.time.LocalDateTime.parse(failedAtStr) : java.time.LocalDateTime.now())
                    .build();

            eventPublisher.publishEvent(event);

            // ✅ 스케줄 예약 실패 신호를 OrderReservationAwaiter에 전송
            UUID orderId = UUID.fromString(orderIdStr);
            orderReservationAwaiter.completeFailure(orderId, failureReason != null ? failureReason : "스케줄 예약 실패");
            log.info("📅❌ [ORDER] 스케줄 예약 실패 신호 전송 - orderId: {}, orderNo: {}, reason: {}", orderId, orderNo, failureReason);
        } catch (Exception e) {
            log.error("🚨 [ORDER] 스케줄 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);

            // ✅ 실패 이벤트 처리 실패 시에도 실패 신호를 전송하여 타임아웃 방지
            try {
                String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
                if (orderIdStr != null && !orderIdStr.isEmpty()) {
                    UUID orderId = UUID.fromString(orderIdStr);
                    orderReservationAwaiter.completeFailure(orderId, "스케줄 예약 실패 이벤트 처리 실패: " + e.getMessage());
                    log.info("📅❌ [ORDER] 스케줄 예약 실패 이벤트 처리 실패로 인한 실패 신호 전송 - orderId: {}", orderId);
                }
            } catch (Exception failureEx) {
                log.error("스케줄 예약 실패 신호 전송 중 오류 발생: {}", failureEx.getMessage(), failureEx);
            }
        }
    }

    private String normalizeQuotedString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private String normalizeJsonString(String value) {
        String trimmed = normalizeQuotedString(value);
        if (trimmed == null) {
            return null;
        }
        // Handle escaped JSON payloads like "\"[{\\\"a\\\":1}]\""
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || trimmed.contains("\\\"")) {
            try {
                return objectMapper.readValue(trimmed, String.class).trim();
            } catch (Exception ignored) {
                return trimmed.replace("\\\"", "\"");
            }
        }
        return trimmed;
    }

    private int parseInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replaceAll("^\"|\"$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 가격 조회 응답 처리
     */
    private void handlePriceLookupResponse(Map<String, Object> values) {
        try {
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");
            String successStr = (String) values.get("success");
            String message = (String) values.get("message");

            // 따옴표 제거
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }
            if (message != null) {
                message = message.trim().replaceAll("^\"|\"$", "");
            }
            if (successStr != null) {
                successStr = successStr.trim().replaceAll("^\"|\"$", "");
            }

            boolean success = Boolean.parseBoolean(successStr);

            if (correlationId == null || requestType == null) {
                log.warn("💰 [ORDER] 가격 조회 응답 필수 데이터 누락 - correlationId: {}, requestType: {}",
                        correlationId, requestType);
                return;
            }

            log.info("💰 [ORDER] 가격 조회 응답 처리 - correlationId: {}, type: {}, success: {}",
                    correlationId, requestType, success);

            Integer price = null;
            Integer stockQuantity = null;
            UUID sessionId = null;
            UUID goodsId = null;

            if (success) {
                try {
                    String priceStr = (String) values.get("price");
                    if (priceStr != null && !priceStr.isEmpty()) {
                        priceStr = priceStr.trim().replaceAll("^\"|\"$", "");
                        if (!priceStr.isEmpty() && !priceStr.equals("null")) {
                            price = Integer.parseInt(priceStr);
                        }
                    }

                    String stockStr = (String) values.get("stockQuantity");
                    if (stockStr != null && !stockStr.isEmpty()) {
                        stockStr = stockStr.trim().replaceAll("^\"|\"$", "");
                        if (!stockStr.isEmpty() && !stockStr.equals("null")) {
                            stockQuantity = Integer.parseInt(stockStr);
                        }
                    }

                    String sessionIdStr = (String) values.get("sessionId");
                    if (sessionIdStr != null && !sessionIdStr.isEmpty()) {
                        sessionIdStr = sessionIdStr.trim().replaceAll("^\"|\"$", "");
                        if (!sessionIdStr.isEmpty() && !sessionIdStr.equals("null")) {
                            sessionId = UUID.fromString(sessionIdStr);
                        }
                    }

                    String goodsIdStr = (String) values.get("goodsId");
                    if (goodsIdStr != null && !goodsIdStr.isEmpty()) {
                        goodsIdStr = goodsIdStr.trim().replaceAll("^\"|\"$", "");
                        if (!goodsIdStr.isEmpty() && !goodsIdStr.equals("null")) {
                            goodsId = UUID.fromString(goodsIdStr);
                        }
                    }

                } catch (Exception e) {
                    log.error("💰 [ORDER] 가격 조회 응답 데이터 파싱 실패 - correlationId: {}, error: {}",
                            correlationId, e.getMessage());
                    success = false;
                    message = "데이터 파싱 실패";
                }
            }

            // PriceLookupResponseEvent 생성 및 처리
            PriceLookupResponseEvent response = PriceLookupResponseEvent.builder()
                    .eventId((String) values.get("eventId"))
                    .correlationId(correlationId)
                    .requestType(requestType)
                    .sessionId(sessionId)
                    .goodsId(goodsId)
                    .price(price)
                    .stockQuantity(stockQuantity)
                    .success(success)
                    .message(message)
                    .respondedAt(java.time.LocalDateTime.now())
                    .build();

            // OrderPriceLookupService에 응답 전달
            orderPriceLookupService.handlePriceLookupResponse(response);

            log.info("✅ [ORDER] 가격 조회 응답 처리 완료 - correlationId: {}, price: {}원",
                    correlationId, price);

        } catch (Exception e) {
            log.error("🚨 [ORDER] 가격 조회 응답 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 사용자 주소 조회 응답 처리
     */
    private void handleUserAddressLookupResponse(Map<String, Object> values) {
        try {
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");
            String successStr = (String) values.get("success");
            String message = (String) values.get("message");

            // 따옴표 제거
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }
            if (message != null) {
                message = message.trim().replaceAll("^\"|\"$", "");
            }
            if (successStr != null) {
                successStr = successStr.trim().replaceAll("^\"|\"$", "");
            }

            boolean success = Boolean.parseBoolean(successStr);

            if (correlationId == null || requestType == null) {
                log.warn("🏠 [ORDER] 사용자 주소 조회 응답 필수 데이터 누락 - correlationId: {}, requestType: {}",
                        correlationId, requestType);
                return;
            }

            log.info("🏠 [ORDER] 사용자 주소 조회 응답 처리 - correlationId: {}, type: {}, success: {}",
                    correlationId, requestType, success);

            Long userId = null;
            List<UserAddressResponse> addresses = null;
            try {
                String userIdStr = (String) values.get("userId");
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    // userId에서도 따옴표 제거
                    userIdStr = userIdStr.trim().replaceAll("^\"|\"$", "");
                    if (!userIdStr.isEmpty() && !userIdStr.equals("null")) {
                        userId = Long.parseLong(userIdStr);
                    }
                }

                // 개별 주소 필드 파싱
                if (success) {
                    String addressId = (String) values.get("addressId");
                    String addrName = (String) values.get("addrName");
                    String address1 = (String) values.get("address1");
                    String address2 = (String) values.get("address2");
                    String postalCode = (String) values.get("postalCode");
                    String isDefaultStr = (String) values.get("isDefault");

                    if (addressId != null && !addressId.trim().isEmpty()) {
                        // 따옴표 제거
                        addressId = addressId.trim().replaceAll("^\"|\"$", "");
                        if (addrName != null) addrName = addrName.trim().replaceAll("^\"|\"$", "");
                        if (address1 != null) address1 = address1.trim().replaceAll("^\"|\"$", "");
                        if (address2 != null) address2 = address2.trim().replaceAll("^\"|\"$", "");
                        if (postalCode != null) postalCode = postalCode.trim().replaceAll("^\"|\"$", "");
                        if (isDefaultStr != null) isDefaultStr = isDefaultStr.trim().replaceAll("^\"|\"$", "");

                        boolean isDefault = "true".equals(isDefaultStr);

                        if (!addressId.isEmpty() && !addressId.equals("null")) {
                            UserAddressResponse address = UserAddressResponse.builder()
                                    .addrId(UUID.fromString(addressId))
                                    .userId(userId)
                                    .addrName(addrName)
                                    .address1(address1)
                                    .address2(address2)
                                    .postalCode(postalCode)
                                    .isDefault(isDefault)
                                    .build();

                            addresses = List.of(address);
                            log.info("🏠 [ORDER] 주소 데이터 파싱 성공 - addrId: {}, addrName: {}",
                                    address.getAddrId(), address.getAddrName());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("🏠 [ORDER] 사용자 주소 조회 응답 데이터 파싱 실패 - correlationId: {}, error: {}",
                        correlationId, e.getMessage());
                success = false;
                message = "데이터 파싱 실패";
                addresses = null;
            }

            // UserAddressLookupResponseEvent 생성 및 처리
            UserAddressLookupResponseEvent response = UserAddressLookupResponseEvent.builder()
                    .eventId((String) values.get("eventId"))
                    .correlationId(correlationId)
                    .requestType(requestType)
                    .userId(userId)
                    .addresses(addresses)
                    .success(success)
                    .message(message)
                    .respondedAt(java.time.LocalDateTime.now())
                    .build();

            if (success && userId != null && addresses != null && !addresses.isEmpty()) {
                UserAddressResponse address = addresses.get(0);
                orderUserAddressCacheService.cacheDefaultAddress(userId, address);
            }

            // OrderUserLookupService에 응답 전달
            orderUserLookupService.handleUserAddressLookupResponse(response);

            log.info("✅ [ORDER] 사용자 주소 조회 응답 처리 완료 - correlationId: {}, userId: {}",
                    correlationId, userId);

        } catch (Exception e) {
            log.error("🚨 [ORDER] 사용자 주소 조회 응답 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void handlePopupInfoLookupResponse(Map<String, Object> values) {
        try {
            String correlationId = (String) values.get("correlationId");
            String successStr = (String) values.get("success");
            String popupIdStr = (String) values.get("popupId");

            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (successStr != null) {
                successStr = successStr.trim().replaceAll("^\"|\"$", "");
            }
            if (popupIdStr != null) {
                popupIdStr = popupIdStr.trim().replaceAll("^\"|\"$", "");
            }

            boolean success = Boolean.parseBoolean(successStr);

            java.util.UUID popupId = null;
            if (popupIdStr != null && !popupIdStr.isEmpty()) {
                popupId = java.util.UUID.fromString(popupIdStr);
            }

            PopupInfoLookupResponseEvent response = PopupInfoLookupResponseEvent.builder()
                    .eventId((String) values.get("eventId"))
                    .correlationId(correlationId)
                    .popupId(popupId)
                    .success(success)
                    .message((String) values.get("message"))
                    .title((String) values.get("title"))
                    .description((String) values.get("description"))
                    .storeId(parseUuidValue(values.get("storeId")))
                    .storeName((String) values.get("storeName"))
                    .address1((String) values.get("address1"))
                    .address2((String) values.get("address2"))
                    .phoneNumber((String) values.get("phoneNumber"))
                    .status((String) values.get("status"))
                    .startDate(parseDateValue(values.get("startDate")))
                    .endDate(parseDateValue(values.get("endDate")))
                    .respondedAt(parseDateValue(values.get("respondedAt")))
                    .eventTime(parseDateValue(values.get("eventTime")))
                    .build();

            orderPopupLookupService.handlePopupInfoLookupResponse(response);

        } catch (Exception e) {
            log.error("🚨 [ORDER] 팝업 정보 조회 응답 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private java.util.UUID parseUuidValue(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim().replaceAll("^\"|\"$", "");
        if (raw.isEmpty()) {
            return null;
        }
        return java.util.UUID.fromString(raw);
    }

    private java.time.LocalDateTime parseDateValue(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim().replaceAll("^\"|\"$", "");
        if (raw.isEmpty()) {
            return null;
        }
        return java.time.LocalDateTime.parse(raw);
    }

    /**
     * 결제 승인 이벤트 처리
     */
    private void handlePaymentApproved(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");
            String amount = (String) values.get("amount");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (amount != null) amount = amount.trim().replaceAll("^\"|\"$", "");

            log.info("💳 [ORDER] 결제 승인 처리 - orderId: {}, paymentId: {}, amount: {}",
                    orderId, paymentId, amount);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 주문 상태를 PAID로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.PAID.name(),
                    "결제 승인 완료 - 결제ID: " + paymentId);

                // 1-1. 결제 관련 캐시 무효화 (결제 URL, 토큰 등)
                try {
                    paymentCacheService.evictPaymentCache(orderUuid);
                    orderCacheService.evictPaymentUrlCache(orderUuid);
                    log.info("🗑️ [ORDER] 결제 캐시 무효화 완료 - orderId: {} (PaymentCache + OrderCache)", orderId);
                } catch (Exception cacheEx) {
                    log.warn("⚠️ [ORDER] 결제 캐시 무효화 실패 - orderId: {}, error: {}", orderId, cacheEx.getMessage());
                }

                // 1-2. 멱등성 키 무효화 (주문 생성 중복 방지 키 해제)
                try {
                    var order = orderRepository.findById(orderUuid).orElse(null);
                    if (order != null && order.getCustomerId() != null && order.getPopupId() != null) {
                        String idempotencyKey = "order:create:" + order.getCustomerId() + ":" + order.getPopupId();
                        orderIdempotencyService.invalidateKey(idempotencyKey, "결제 승인 완료");
                        log.info("🔑 [ORDER] 멱등성 키 무효화 완료 - orderId: {}, key: {}", orderId, idempotencyKey);
                    }
                } catch (Exception idempEx) {
                    log.warn("⚠️ [ORDER] 멱등성 키 무효화 실패 - orderId: {}, error: {}", orderId, idempEx.getMessage());
                }

                // 2. 내부 PaymentCompletedEvent 발행하여 재고 차감 프로세스 시작
                try {
                    PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.builder()
                            .eventId(java.util.UUID.randomUUID().toString())
                            .orderId(orderUuid)
                            .paymentKey(paymentId) // 결제키로 사용
                            .amount(amount != null ? Integer.valueOf(amount) : null)
                            .paymentMethod("TOSS_PAYMENT")
                            .completedAt(java.time.LocalDateTime.now())
                            .eventTime(java.time.LocalDateTime.now())
                            .build();

                    eventPublisher.publishEvent(paymentEvent);
                    log.info("✅ [ORDER] PaymentCompletedEvent 발행 완료 - 재고 차감 프로세스 시작 - orderId: {}", orderId);
                } catch (Exception eventEx) {
                    log.error("🚨 [ORDER] PaymentCompletedEvent 발행 실패 - orderId: {}", orderId, eventEx);
                }

                log.info("✅ [ORDER] 결제 승인 처리 완료 - orderId: {}, status: PAID", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 승인 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 완료 이벤트 처리 (비활성화됨 - handlePaymentApproved에서 처리)
     *
     * 중복 결제 방지를 위해 사용 중단.
     * 모든 결제 처리는 handlePaymentApproved에서 통합 처리됨.
     */
    @SuppressWarnings("unused")
    private void handlePaymentCompleted_DEPRECATED(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");

            log.info("✅ [ORDER] 결제 완료 처리 - orderId: {}, paymentId: {}",
                    orderId, paymentId);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 재고 차감 요청 (Store 서비스에 재고 차감 요청 전송)
                orderCommandService.requestStockDeduction(orderUuid);

                // 2. 주문 상태를 COMPLETED로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.COMPLETED.name(),
                    "결제 완료 - 결제ID: " + paymentId);

                // 3. 주문 완료 이벤트 발행 (알림 등 후속 처리용)
                orderCommandService.publishOrderCompletedEvent(orderUuid);

                log.info("✅ [ORDER] 주문 완료 처리 완료 - orderId: {}, status: COMPLETED", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 완료 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 주문 결제 완료 이벤트 처리 (Stores 서비스에서 발행)
     * - 재고 차감 요청 발행
     * - 스케줄 확정 처리
     */
    private void handleOrderPaid(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String paymentId = (String) values.get("paymentId");
            String totalAmount = (String) values.get("totalAmount");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (totalAmount != null) totalAmount = totalAmount.trim().replaceAll("^\"|\"$", "");

            log.info("💳✅ [ORDER] 주문 결제 완료 처리 시작 - orderId: {}, orderNo: {}, amount: {}",
                    orderId, orderNo, totalAmount);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 주문 상태를 PAID로 업데이트
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.PAID.name(),
                    "결제 완료 - 재고 차감 및 스케줄 확정 진행");

                // 2. 재고 차감 요청 (Store 서비스에 재고 차감 요청 전송)
                log.info("📦 [ORDER] 재고 차감 요청 발행 - orderId: {}", orderId);
                orderCommandService.requestStockDeduction(orderUuid);

                // 3. 스케줄 확정 처리 (예약에서 확정으로 변경)
                log.info("📅 [ORDER] 스케줄 확정 요청 발행 - orderId: {}", orderId);
                orderCommandService.requestScheduleConfirmation(orderUuid);

                // 4. 내부 결제 완료 이벤트 발행 (다른 서비스 알림용)
                try {
                    PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.builder()
                            .eventId(java.util.UUID.randomUUID().toString())
                            .orderId(orderUuid)
                            .paymentKey(paymentId)
                            .amount(totalAmount != null ? Integer.valueOf(totalAmount) : null)
                            .paymentMethod("TOSS_PAYMENT")
                            .completedAt(java.time.LocalDateTime.now())
                            .eventTime(java.time.LocalDateTime.now())
                            .build();

                    eventPublisher.publishEvent(paymentEvent);
                    log.info("📨 [ORDER] 내부 결제 완료 이벤트 발행 완료 - orderId: {}", orderId);

                } catch (Exception eventError) {
                    log.warn("⚠️ [ORDER] 내부 결제 완료 이벤트 발행 실패 (재고/스케줄 처리는 계속) - error: {}",
                            eventError.getMessage());
                }

                log.info("✅ [ORDER] 주문 결제 완료 처리 완료 - orderId: {}, 재고차감+스케줄확정 요청 발행됨", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 주문 결제 완료 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 실패 이벤트 처리
     */
    private void handlePaymentFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");
            String reason = (String) values.get("reason");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (paymentId != null) paymentId = paymentId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.info("❌ [ORDER] 결제 실패 처리 - orderId: {}, paymentId: {}, reason: {}",
                    orderId, paymentId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 결제 취소 처리 (결제 실패 시)
                if (paymentId != null && !paymentId.isEmpty()) {
                    orderCommandService.cancelPaymentForOrder(orderUuid, paymentId, reason);
                }

                // 2. 주문 상태를 CANCELLED로 업데이트 (결제 실패로 인한 취소)
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                    "결제 실패 - 결제ID: " + paymentId + ", 사유: " + reason);

                // 3. 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                log.info("✅ [ORDER] 결제 실패 처리 완료 - orderId: {}, 결제취소: {}, status: CANCELLED, 재고 예약 해제됨",
                        orderId, paymentId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 성공 이벤트 처리
     */
    private void handleStockDeductionSuccess(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String stockDetails = (String) values.get("stockDetails");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (stockDetails != null) stockDetails = stockDetails.trim().replaceAll("^\"|\"$", "");

            log.info("📦✅ [ORDER] 재고 차감 성공 처리 - orderId: {}, orderNo: {}, stockDetails: {}",
                    orderId, orderNo, stockDetails);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 주문 상태를 COMPLETED로 업데이트 (재고 차감 성공 = 주문 완료)
                try {
                    orderCommandService.updateOrderStatus(orderUuid, OrderStatus.COMPLETED.name(),
                        "재고 차감 완료 - 주문 완료: " + stockDetails);
                } catch (Exception e) {
                    // 멱등성 처리나 이미 완료된 상태일 경우 로깅만 하고 넘어감
                    if (e.getMessage() != null && e.getMessage().contains("IdempotencyException")) {
                        log.warn("📦✅ [ORDER] 재고 차감 성공 멱등 처리 중복 - orderId: {}, reason: {}",
                                orderId, e.getMessage());
                        return;
                    }
                    // 다른 예외는 다시 던짐
                    throw e;
                }

                log.info("📦✅ [ORDER] 재고 차감 성공으로 주문 완료 상태 업데이트 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 재고 차감 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 실패 이벤트 처리
     */
    private void handleStockDeductionFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");
            String reason = (String) values.get("reason");

            // 따옴표 제거
            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.error("📦❌ [ORDER] 재고 차감 실패 처리 - orderId: {}, orderNo: {}, reason: {}",
                    orderId, orderNo, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 1. 결제 취소 요청 (보상 트랜잭션)
                orderCommandService.cancelPaymentForOrder(orderUuid, null,
                    "재고 차감 실패로 인한 자동 환불: " + reason);

                // 2. 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                // 3. 주문 상태를 CANCELLED로 업데이트 (재고 차감 실패)
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                    "재고 차감 실패로 인한 주문 취소 - " + reason);

                log.info("📦❌ [ORDER] 재고 차감 실패로 주문 취소 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 재고 차감 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 결제 취소 이벤트 처리
     */
    private void handlePaymentCancelled(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String reason = (String) values.get("cancelReason");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.info("↩️ [ORDER] 결제 취소 처리 - orderId: {}, reason: {}", orderId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);

                // 결제 취소 시 캐시 무효화
                try {
                    paymentCacheService.evictPaymentCache(orderUuid);
                    orderCacheService.evictPaymentUrlCache(orderUuid);
                    log.info("🗑️ [ORDER] 결제 취소로 인한 캐시 무효화 완료 - orderId: {} (PaymentCache + OrderCache)", orderId);
                } catch (Exception cacheEx) {
                    log.warn("⚠️ [ORDER] 결제 취소 캐시 무효화 실패 - orderId: {}, error: {}", orderId, cacheEx.getMessage());
                }

                // 멱등성 키 무효화 (주문 생성 중복 방지 키 해제)
                try {
                    var order = orderRepository.findById(orderUuid).orElse(null);
                    if (order != null && order.getCustomerId() != null && order.getPopupId() != null) {
                        String idempotencyKey = "order:create:" + order.getCustomerId() + ":" + order.getPopupId();
                        orderIdempotencyService.invalidateKey(idempotencyKey, "결제 취소 - " + (reason != null ? reason : "사용자 요청"));
                        log.info("🔑 [ORDER] 멱등성 키 무효화 완료 - orderId: {}, key: {}, reason: {}", orderId, idempotencyKey, reason);
                    }
                } catch (Exception idempEx) {
                    log.warn("⚠️ [ORDER] 멱등성 키 무효화 실패 - orderId: {}, error: {}", orderId, idempEx.getMessage());
                }

                // 재고 예약 해제
                orderCommandService.cancelStockReservationsForOrder(orderUuid);

                // 주문 취소
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                        "결제 취소 - " + (reason != null ? reason : ""));
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 결제 취소 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 굿즈 예약 성공 이벤트 처리
     */
    private void handleGoodsReserved(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String orderNo = (String) values.get("orderNo");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (orderNo != null) orderNo = orderNo.trim().replaceAll("^\"|\"$", "");

            if ((orderNo == null || orderNo.isBlank()) && orderId != null && !orderId.isBlank()) {
                try {
                    UUID orderUuid = UUID.fromString(orderId);
                    orderNo = orderRepository.findById(orderUuid)
                        .map(o -> o.getOrderNo())
                        .orElse(null);
                } catch (Exception ignored) {
                    // best-effort only
                }
            }

            log.info("📦✅ [ORDER] 굿즈 예약 성공 처리 - orderId: {}, orderNo: {}", orderId, orderNo);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);
                scheduleOrderStatusUpdate(orderUuid, 1);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 굿즈 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void scheduleOrderStatusUpdate(UUID orderId, int attempt) {
        int maxAttempts = 10;
        long delayMillis = 200L;

        retryExecutor.schedule(() -> {
            try {
                var orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    if (attempt < maxAttempts) {
                        scheduleOrderStatusUpdate(orderId, attempt + 1);
                    } else {
                        log.warn("📦✅ [ORDER] 굿즈 예약 성공 처리 재시도 실패 - orderId: {}", orderId);
                    }
                    return;
                }

                OrderStatus currentStatus = orderOpt.get().getStatus();
                if (currentStatus == OrderStatus.PAYMENT_PENDING) {
                    log.info("📦✅ [ORDER] 이미 PAYMENT_PENDING 상태 - orderId: {}", orderId);
                    return;
                }
                if (currentStatus == OrderStatus.PAID
                        || currentStatus == OrderStatus.COMPLETED
                        || currentStatus == OrderStatus.CANCELLED
                        || currentStatus == OrderStatus.REJECTED) {
                    log.warn("📦✅ [ORDER] 예약 성공 이벤트 무시 - 현재 상태: {}, orderId: {}",
                            currentStatus, orderId);
                    return;
                }

                orderCommandService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING.name(),
                        "재고 예약 완료 - 결제 진행");

                // 결제 생성 요청 이벤트 발행
                orderCommandService.publishPaymentCreateRequestedEvent(orderId);

                orderRepository.findById(orderId).ifPresent(order -> {
                    PaymentUrlResponse paymentUrl = orderCommandService.generatePaymentUrlAfterReservation(order);
                    orderReservationAwaiter.completeSuccess(orderId, paymentUrl);
                });
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "";
                if (message.contains("주문을 찾을 수 없어요") && attempt < maxAttempts) {
                    scheduleOrderStatusUpdate(orderId, attempt + 1);
                    return;
                }
                log.error("🚨 [ORDER] 굿즈 예약 성공 처리 실패 - orderId: {}, error: {}", orderId, e.getMessage(), e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 굿즈 예약 실패 이벤트 처리
     */
    private void handleGoodsReservationFailed(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String reason = (String) values.get("reason");

            if (orderId != null) orderId = orderId.trim().replaceAll("^\"|\"$", "");
            if (reason != null) reason = reason.trim().replaceAll("^\"|\"$", "");

            log.warn("📦❌ [ORDER] 굿즈 예약 실패 처리 - orderId: {}, reason: {}", orderId, reason);

            if (orderId != null && !orderId.isEmpty()) {
                UUID orderUuid = UUID.fromString(orderId);
                orderCommandService.updateOrderStatus(orderUuid, OrderStatus.CANCELLED.name(),
                        "재고 부족 - 주문 실패: " + (reason != null ? reason : "재고 부족"));
                orderReservationAwaiter.completeFailure(orderUuid,
                        reason != null ? reason : "재고 부족");
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 굿즈 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 성공 이벤트 처리
     */
    private void handleMixedReservationSuccess(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = (String) values.get("orderNo");
            String reservationToken = (String) values.get("reservationToken");

            log.info("🔗✅ [ORDER] 복합형 예약 성공 처리 - orderId: {}, orderNo: {}, token: {}",
                    orderIdStr, orderNo, reservationToken);

            if (orderIdStr != null && !orderIdStr.isEmpty()) {
                UUID orderId = UUID.fromString(orderIdStr);

                // 주문 상태를 RESERVED로 업데이트
                orderCommandService.updateOrderStatus(orderId, OrderStatus.RESERVED.name(),
                        "복합형 예약 성공 (스케줄+상품)");

                // 예약 대기자에게 성공 알림
                orderRepository.findById(orderId).ifPresent(order -> {
                    PaymentUrlResponse paymentUrlResponse = orderCommandService
                            .generatePaymentUrlAfterReservation(order);
                    orderReservationAwaiter.completeSuccess(orderId, paymentUrlResponse);
                });

                log.info("✅ [ORDER] 복합형 예약 성공 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 복합형 예약 성공 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 실패 이벤트 처리
     */
    private void handleMixedReservationFailed(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = (String) values.get("orderNo");
            String failureReason = (String) values.get("failureReason");

            log.warn("🔗❌ [ORDER] 복합형 예약 실패 처리 - orderId: {}, orderNo: {}, reason: {}",
                    orderIdStr, orderNo, failureReason);

            if (orderIdStr != null && !orderIdStr.isEmpty()) {
                UUID orderId = UUID.fromString(orderIdStr);

                // 주문 상태를 CANCELLED로 업데이트
                orderCommandService.updateOrderStatus(orderId, OrderStatus.CANCELLED.name(),
                        "복합형 예약 실패: " + (failureReason != null ? failureReason : "알 수 없는 이유"));

                // 예약 대기자에게 실패 알림
                orderReservationAwaiter.completeFailure(orderId,
                        failureReason != null ? failureReason : "복합형 예약 실패");

                log.warn("❌ [ORDER] 복합형 예약 실패 처리 완료 - orderId: {}", orderId);
            }

        } catch (Exception e) {
            log.error("🚨 [ORDER] 복합형 예약 실패 이벤트 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    /**
     * 실패한 메시지를 Dead Letter Queue로 전송
     */
    private void sendToDeadLetterQueue(MapRecord<String, String, String> record, Exception e) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("original_stream", record.getStream());
            dlqMessage.put("original_id", record.getId().getValue());
            dlqMessage.put("failed_at", System.currentTimeMillis());
            dlqMessage.put("error_message", e.getMessage());
            dlqMessage.put("error_class", e.getClass().getSimpleName());
            dlqMessage.put("original_data", new HashMap<>(record.getValue()));

            // DLQ Stream에 실패 메시지 저장
            redisTemplate.opsForStream().add("order-failed-events", dlqMessage);

            log.warn("📮 [ORDER] 실패한 메시지를 DLQ로 이동: stream={}, id={}, error={}",
                    record.getStream(), record.getId().getValue(), e.getMessage());

        } catch (Exception dlqError) {
            log.error("🚨 [ORDER] DLQ 전송 실패: {}", dlqError.getMessage(), dlqError);
        }
    }

    /**
     * 수신한 이벤트의 eventType 필수 검증
     */
    private String validateEventType(Map<String, Object> values, String streamName, String recordId) {
        Object eventTypeObj = values.get("eventType");

        if (eventTypeObj == null) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 누락된 이벤트 수신! stream=%s, recordId=%s, values=%s",
                streamName, recordId, values
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + streamName);
        }

        String eventType = eventTypeObj.toString().trim();
        // 따옴표 제거
        eventType = eventType.replaceAll("^\"|\"$", "").trim();
        if (eventType.isEmpty()) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 비어있음! stream=%s, recordId=%s",
                streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + streamName);
        }

        // eventType 형식 검증 (kebab-case)
        if (!eventType.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            String errorMessage = String.format(
                "[CRITICAL] eventType 형식 오류! eventType=%s, stream=%s, recordId=%s (kebab-case 형식 필요)",
                eventType, streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 kebab-case 형식이어야 합니다: " + eventType);
        }

        log.debug("✅ [ORDER] eventType 검증 통과: {} (stream: {}, recordId: {})",
                eventType, streamName, recordId);

        return eventType;
    }
}
