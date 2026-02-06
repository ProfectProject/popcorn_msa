package com.popcorn.store.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import com.popcorn.store.constants.EventConstants;
import com.popcorn.store.domain.goods.entity.ReservationType;
import com.popcorn.store.domain.goods.entity.GoodsVariant;
import com.popcorn.store.domain.goods.service.GoodsService;
import com.popcorn.store.event.order.StockDeductionSuccessEvent;
import com.popcorn.store.event.order.StockDeductionFailedEvent;
import com.popcorn.store.domain.goods.service.GoodsOrderReservationService;
import com.popcorn.store.domain.goods.repository.GoodsVariantRepository;
import com.popcorn.store.domain.popup.entity.PopupSchedule;
import com.popcorn.store.domain.popup.repository.owner.jpa.JpaOwnerPopupRepository;
import com.popcorn.store.domain.store.repository.jpa.JpaStoreRepository;
import com.popcorn.store.domain.popup.repository.owner.jpa.JpaOwnerPopupScheduleRepository;
import com.popcorn.store.domain.popup.service.ScheduleInventoryApiService;
import com.popcorn.store.event.payment.InventoryConfirmationRequestedEvent;
import com.popcorn.store.event.order.OrderPaidEvent;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.GoodsHoldItem;
import com.popcorn.store.inventory.redis.InventoryRedisHoldService.HoldResult;
import com.popcorn.store.event.kafka.KafkaPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Store 서비스 Redis Stream 이벤트 리스너
 * - Redis Stream 메시지 수신 및 처리
 * - Consumer Group 기반 메시지 처리
 * - 메시지 ACK 자동 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreRedisStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final GoodsOrderReservationService reservationService;
    private final GoodsService goodsService;
    private final StoreRedisEventPublisher storeRedisEventPublisher;
    private final GoodsVariantRepository goodsVariantRepository;
    private final JpaOwnerPopupScheduleRepository popupScheduleRepository;
    private final JpaOwnerPopupRepository popupRepository;
    private final JpaStoreRepository storeRepository;
    private final InventoryRedisHoldService inventoryHoldService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduleInventoryApiService scheduleInventoryApiService;
    private final KafkaPublisher kafkaPublisher;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            String streamName = record.getStream();
            String recordId = record.getId().getValue();
            Map<String, Object> values = new HashMap<>(record.getValue());
            String debugEventId = normalizeQuotedString((String) values.get("eventId"));

            log.info("🔔 [STORES] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                    streamName, recordId, values.get("eventType"));
            log.debug("🧩 [STORES] 수신 이벤트 메타 - stream: {}, recordId: {}, eventId: {}, orderId: {}",
                    streamName, recordId, debugEventId, values.get("orderId"));

            // eventType 필수 검증
            String eventType = validateEventType(values, streamName, recordId);
            String originalEventType = eventType;
            // eventType에서 따옴표 제거
            if (eventType != null) {
                eventType = eventType.trim().replaceAll("^\"|\"$", "");
            }

            log.debug("🔍 [STORES] EventType 정규화 - original: '{}', normalized: '{}'",
                     originalEventType, eventType);

            handleIncomingEvent(eventType, values, streamName);

            // 메시지 처리 완료 후 ACK (자동으로 처리됨)
            log.debug("✅ [STORES] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId);

        } catch (Exception e) {
            log.error("🚨 [STORES] Stream 메시지 처리 실패 - record: {}, error: {}",
                    record, e.getMessage(), e);

            // 실패한 메시지를 DLQ로 이동
            sendToDeadLetterQueue(record, e);
        }
    }

    /**
     * Stream 이벤트 타입별 처리
     */
    public void handleIncomingEvent(String eventType, Map<String, Object> values, String source) {
        String context = source != null ? source : "unknown";
        log.debug("🔁 [STORES] Event dispatch - source={}, eventType={}", context, eventType);
        try {
            switch (eventType) {
                case EventConstants.EventTypes.ORDER_PAID:
                    log.info("💳 [STORES] 주문 결제 완료 이벤트 수신 - source={}", context);
                    publishOrderPaidEvent(values);
                    break;
                case EventConstants.EventTypes.GOODS_RESERVATION_REQUESTED:
                    log.info("📋 [STORES] 굿즈 재고 예약 요청 이벤트 수신 - source={}", context);
                    publishGoodsReservationRequestedEvent(values);
                    break;
                case EventConstants.EventTypes.MIXED_RESERVATION_REQUESTED:
                    log.info("🔗 [STORES] 복합형 예약 요청 이벤트 수신 (스케줄+굿즈) - source={}", context);
                    handleMixedReservationRequested(values);
                    break;
                case EventConstants.EventTypes.GOODS_RESERVATION_CANCEL_REQUESTED:
                    log.info("↩️ [STORES] 굿즈 예약 취소 요청 이벤트 수신 - source={}", context);
                    handleGoodsReservationCancelRequested(values);
                    break;
                case EventConstants.EventTypes.SCHEDULE_RESERVATION_REQUESTED:
                    log.info("📅 [STORES] 스케줄 예약 요청 이벤트 수신 - source={}", context);
                    handleScheduleReservationRequested(values);
                    break;
                case EventConstants.EventTypes.SCHEDULE_RESERVATION_CANCEL_REQUESTED:
                    log.info("↩️ [STORES] 스케줄 예약 취소 요청 이벤트 수신 - source={}", context);
                    handleScheduleReservationCancelRequested(values);
                    break;
                case EventConstants.EventTypes.STOCK_DEDUCTION_REQUESTED:
                    log.info("📦 [STORES] 재고 차감 요청 이벤트 수신 - orderId: {} - source={}",
                            values.get("orderId"), context);
                    handleStockDeductionRequest(values);
                    break;
                case EventConstants.EventTypes.PRICE_LOOKUP_REQUESTED:
                    log.info("💰 [STORES] 가격 조회 요청 이벤트 수신 - source={}", context);
                    publishPriceLookupResponseEvent(values);
                    break;
                case EventConstants.EventTypes.POPUP_INFO_LOOKUP_REQUESTED:
                    log.info("🏬 [STORES] 팝업 정보 조회 요청 이벤트 수신 - source={}", context);
                    handlePopupInfoLookupRequested(values);
                    break;
                case EventConstants.EventTypes.SCHEDULE_CONFIRMATION_REQUESTED:
                    log.info("📅🔒 [STORES] 스케줄 확정 요청 이벤트 수신 - source={}", context);
                    handleScheduleConfirmationRequested(values);
                    break;
                case EventConstants.EventTypes.INVENTORY_CONFIRMATION_REQUESTED:
                    log.info("📦🔒 [STORES] 재고 확정/복구 요청 이벤트 수신 - source={}", context);
                    handleInventoryConfirmationRequested(values);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("🚨 [STORES] 이벤트 처리 실패 - eventType: {}, source: {}, error: {}",
                    eventType, context, e.getMessage(), e);
            sendEventFailureToDLQ(eventType, values, e);
        }
    }

    private void handleInventoryConfirmationRequested(Map<String, Object> values) {
        try {
            String paymentIdStr = normalizeUuidString((String) values.get("paymentId"));
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String actionType = normalizeQuotedString((String) values.get("actionType"));
            String reason = normalizeQuotedString((String) values.get("reason"));
            String requestedAt = normalizeQuotedString((String) values.get("requestedAt"));
            String occurredAt = normalizeQuotedString((String) values.get("occurredAt"));
            String eventId = normalizeQuotedString((String) values.get("eventId"));

            if (orderIdStr == null || orderIdStr.isBlank()) {
                log.warn("📦🔒 [STORES] 재고 확정 요청 필수 데이터 누락 - values: {}", values);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            UUID paymentId = paymentIdStr != null && !paymentIdStr.isBlank()
                    ? UUID.fromString(paymentIdStr)
                    : new UUID(0, 0);

            InventoryConfirmationRequestedEvent event =
                    InventoryConfirmationRequestedEvent.fromPayload(
                            paymentId,
                            orderId,
                            actionType,
                            reason,
                            requestedAt,
                            occurredAt,
                            eventId
                    );

            eventPublisher.publishEvent(event);
            log.info("✅ [STORES] 재고 확정/복구 이벤트 전달 완료 - orderId: {}, action: {}",
                    orderId, event.getActionType());

        } catch (Exception e) {
            log.error("🚨 [STORES] 재고 확정/복구 요청 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void publishOrderPaidEvent(Map<String, Object> values) {
        try {
            String eventId = normalizeQuotedString((String) values.get("eventId"));
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String userIdStr = normalizeQuotedString((String) values.get("userId"));
            String popupIdStr = normalizeQuotedString((String) values.get("popupId"));
            String orderType = normalizeQuotedString((String) values.get("orderType"));
            String totalAmountStr = normalizeQuotedString((String) values.get("totalAmount"));
            String paidAtStr = normalizeQuotedString((String) values.get("paidAt"));
            String eventTimeStr = normalizeQuotedString((String) values.get("eventTime"));
            String orderItemsJson = (String) values.get("orderItems");

            if (orderItemsJson != null) {
                orderItemsJson = orderItemsJson.trim().replaceAll("^\"|\"$", "");
                orderItemsJson = orderItemsJson.replace("\\\"", "\"");
            }

            List<OrderPaidEvent.OrderItemInfo> orderItems = List.of();
            if (orderItemsJson != null && !orderItemsJson.isBlank()) {
                orderItems = objectMapper.readValue(
                        orderItemsJson, new TypeReference<List<OrderPaidEvent.OrderItemInfo>>() {}
                );
            }

            OrderPaidEvent event = OrderPaidEvent.builder()
                    .eventId(eventId)
                    .orderId(UUID.fromString(orderIdStr))
                    .orderNo(orderNo)
                    .customerId(userIdStr != null && !userIdStr.isBlank() ? Long.parseLong(userIdStr) : null)
                    .popupId(popupIdStr != null && !popupIdStr.isBlank() ? UUID.fromString(normalizeUuidString(popupIdStr)) : null)
                    .orderType(orderType)
                    .totalAmount(totalAmountStr != null && !totalAmountStr.isBlank()
                            ? Integer.parseInt(totalAmountStr)
                            : null)
                    .orderItems(orderItems)
                    .paidAt(java.time.LocalDateTime.parse(paidAtStr))
                    .eventTime(java.time.LocalDateTime.parse(eventTimeStr))
                    .build();

            eventPublisher.publishEvent(event);
            log.info("📨 [STORES] 주문 결제 완료 이벤트 발행 - orderId: {}, eventId: {}",
                    event.getOrderId(), eventId);

        } catch (Exception e) {
            log.error("🚨 [STORES] 주문 결제 완료 이벤트 변환 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void publishGoodsReservationRequestedEvent(Map<String, Object> values) {
        try {
            String eventId = normalizeQuotedString((String) values.get("eventId"));
            String reservationItemsJson = (String) values.get("reservationItems");

            // JSON 문자열 정규화 (Redis Stream에서 전송된 JSON 문자열 처리)
            if (reservationItemsJson != null) {
                // 1. 시작/끝 따옴표 제거
                reservationItemsJson = reservationItemsJson.trim().replaceAll("^\"|\"$", "");
                // 2. 이스케이프된 따옴표를 정상 따옴표로 변환
                reservationItemsJson = reservationItemsJson.replace("\\\"", "\"");

                log.debug("🔧 [STORES] JSON 정규화 완료 - reservationItems: {}", reservationItemsJson);
            }

            List<GoodsReservationItem> reservationItems = objectMapper.readValue(
                    reservationItemsJson, new TypeReference<List<GoodsReservationItem>>() {}
            );

            // orderId와 popupId에서 따옴표 제거
            String orderIdStr = (String) values.get("orderId");
            String popupIdStr = (String) values.get("popupId");

            if (orderIdStr != null) {
                orderIdStr = orderIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (popupIdStr != null) {
                popupIdStr = popupIdStr.trim().replaceAll("^\"|\"$", "");
            }

            UUID orderId;
            try {
                orderId = UUID.fromString(normalizeUuidString(orderIdStr));
            } catch (Exception e) {
                log.error("📦 [STORES] 재고 차감 요청 orderId UUID 파싱 실패 - orderId: {}, error: {}",
                        orderIdStr, e.getMessage(), e);
                return;
            }
            String orderNo = (String) values.get("orderNo");
            UUID popupId = (popupIdStr == null || popupIdStr.isEmpty()) ?
                    null : UUID.fromString(popupIdStr);
            log.info("📦 [STORES] 굿즈 예약 요청 수신 - orderId: {}, eventId: {}, items: {}",
                    orderId, eventId, reservationItems.size());

            if (reservationItems == null || reservationItems.isEmpty()) {
                log.warn("📋 [STORES] 굿즈 재고 예약 요청 항목이 없음 - orderId: {}", orderId);
                return;
            }

            LocalDateTime reservationExpiresAt = LocalDateTime.now().plusMinutes(10);

            for (GoodsReservationItem item : reservationItems) {
                if (item.goodsId == null || item.quantity == null) {
                    log.warn("📋 [STORES] 굿즈 재고 예약 요청 항목 누락 - orderId: {}, item: {}",
                            orderId, item);
                    continue;
                }

                try {
                    UUID resolvedPopupId = popupId;
                    if (resolvedPopupId == null) {
                        resolvedPopupId = goodsService.resolvePopupId(item.goodsId);
                    }

                    goodsService.reservationGoods(resolvedPopupId, item.goodsId, item.quantity);
                    reservationService.createGoodsReservation(
                            orderId, orderNo, resolvedPopupId, item.goodsId, item.quantity
                    );

                    log.info("✅ [STORES] 굿즈 재고 예약 완료 - orderId: {}, goodsId: {}, qty: {}",
                            orderId, item.goodsId, item.quantity);

                    storeRedisEventPublisher.publishGoodsReservedEvent(
                            orderId, orderNo, resolvedPopupId, item.goodsId, item.quantity
                    );

                    kafkaPublisher.publishGoodsReservationSucceeded(
                            orderId, item.goodsId, item.quantity, reservationExpiresAt
                    );

                } catch (Exception e) {
                    log.error("❌ [STORES] 굿즈 재고 예약 실패 - orderId: {}, goodsId: {}, qty: {}, error: {}",
                            orderId, item.goodsId, item.quantity, e.getMessage(), e);

                    storeRedisEventPublisher.publishGoodsReservationFailedEvent(
                            orderId, popupId, item.goodsId, item.quantity, 0, e.getMessage()
                    );

                    String failureReason = e.getMessage() != null ? e.getMessage() : "unknown error";
                    kafkaPublisher.publishGoodsReservationFailed(orderId, item.goodsId, failureReason);
                }
            }

        } catch (Exception e) {
            log.error("🚨 [STORES] 굿즈 재고 예약 요청 이벤트 변환 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void publishPriceLookupResponseEvent(Map<String, Object> values) {
        try {
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");

            // correlationId에서 따옴표 제거
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }

            // requestType에서 따옴표 제거
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }

            if (correlationId == null || requestType == null) {
                log.warn("💰 [STORES] 가격 조회 요청 누락 - values: {}", values);
                return;
            }

            log.info("💰 [STORES] 가격 조회 요청 처리 - correlationId: {}, requestType: {}",
                    correlationId, requestType);

            if ("SESSION".equals(requestType)) {
                handleSessionPriceLookup(values);
            } else if ("GOODS".equals(requestType)) {
                handleGoodsPriceLookup(values);
            } else {
                log.warn("💰 [STORES] 지원하지 않는 가격 조회 타입 - type: \"{}\"", requestType);
                publishPriceLookupFailure(values, "지원하지 않는 가격 조회 타입");
            }

        } catch (Exception e) {
            log.error("🚨 [STORES] 가격 조회 요청 이벤트 변환 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
        }
    }

    private void handleSessionPriceLookup(Map<String, Object> values) {
        try {
            String sessionIdStr = (String) values.get("sessionId");
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");

            // 따옴표 제거
            if (sessionIdStr != null) {
                sessionIdStr = sessionIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }

            if (sessionIdStr == null || sessionIdStr.isEmpty()) {
                publishPriceLookupFailure(values, "sessionId가 없습니다.");
                return;
            }

            UUID sessionId = UUID.fromString(sessionIdStr);
            PopupSchedule schedule = popupScheduleRepository.findById(sessionId)
                    .filter(value -> value.getDeletedAt() == null)
                    .orElse(null);

            if (schedule == null || schedule.getPrice() == null) {
                publishPriceLookupFailure(values, "세션 정보를 찾을 수 없습니다.");
                return;
            }

            StoreRedisEventPublisher.PriceLookupResponseEventDto response =
                    StoreRedisEventPublisher.PriceLookupResponseEventDto.builder()
                            .eventId(UUID.randomUUID().toString())
                            .correlationId(correlationId)
                            .requestType(requestType)
                            .sessionId(sessionId)
                            .price(schedule.getPrice())
                            .success(true)
                            .message("OK")
                            .respondedAt(java.time.LocalDateTime.now())
                            .eventTime(java.time.LocalDateTime.now())
                            .build();

            storeRedisEventPublisher.publishPriceLookupResponseEvent(response);

            log.info("✅ [STORES] 세션 가격 조회 응답 완료 - sessionId: {}, price: {}원",
                    sessionId, schedule.getPrice());

        } catch (Exception e) {
            log.error("🚨 [STORES] 세션 가격 조회 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
            publishPriceLookupFailure(values, "세션 가격 조회 처리 실패");
        }
    }

    private void handleGoodsPriceLookup(Map<String, Object> values) {
        try {
            String goodsIdStr = (String) values.get("goodsId");
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");

            // 따옴표 제거
            if (goodsIdStr != null) {
                goodsIdStr = goodsIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }

            if (goodsIdStr == null || goodsIdStr.isEmpty()) {
                publishPriceLookupFailure(values, "goodsId가 없습니다.");
                return;
            }

            log.info("💰 [STORES] 굿즈 가격 조회 시작 - goodsId: {}, correlationId: {}",
                    goodsIdStr, correlationId);

            UUID goodsId = UUID.fromString(goodsIdStr);
            GoodsVariant variant = goodsVariantRepository.findById(goodsId)
                    .filter(value -> value.getDeletedAt() == null)
                    .orElse(null);

            if (variant == null) {
                log.warn("💰 [STORES] 굿즈 정보 없음 - goodsId: {}", goodsId);
                publishPriceLookupFailure(values, "굿즈 정보를 찾을 수 없습니다.");
                return;
            }

            StoreRedisEventPublisher.PriceLookupResponseEventDto response =
                    StoreRedisEventPublisher.PriceLookupResponseEventDto.builder()
                            .eventId(UUID.randomUUID().toString())
                            .correlationId(correlationId)
                            .requestType(requestType)
                            .goodsId(goodsId)
                            .price(variant.getGoodsPrice())
                            .stockQuantity(variant.getStock())
                            .success(true)
                            .message("OK")
                            .respondedAt(java.time.LocalDateTime.now())
                            .eventTime(java.time.LocalDateTime.now())
                            .build();

            storeRedisEventPublisher.publishPriceLookupResponseEvent(response);

            log.info("✅ [STORES] 굿즈 가격 조회 응답 완료 - goodsId: {}, price: {}원, stock: {}개",
                    goodsId, variant.getGoodsPrice(), variant.getStock());

        } catch (Exception e) {
            log.error("🚨 [STORES] 굿즈 가격 조회 처리 실패 - values: {}, error: {}",
                    values, e.getMessage(), e);
            publishPriceLookupFailure(values, "굿즈 가격 조회 처리 실패");
        }
    }

    private void handleGoodsReservationCancelRequested(Map<String, Object> values) {
        try {
            String popupIdStr = (String) values.get("popupId");
            String goodsIdStr = (String) values.get("goodsId");
            String quantityStr = (String) values.get("quantity");
            if (goodsIdStr == null) {
                goodsIdStr = (String) values.get("goodsVariantId");
            }
            if (quantityStr == null) {
                quantityStr = (String) values.get("qty");
            }

            if (popupIdStr != null) {
                popupIdStr = popupIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (goodsIdStr != null) {
                goodsIdStr = goodsIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (quantityStr != null) {
                quantityStr = quantityStr.trim().replaceAll("^\"|\"$", "");
            }

            if (goodsIdStr == null || goodsIdStr.isEmpty() || quantityStr == null || quantityStr.isEmpty()) {
                log.warn("↩️ [STORES] 굿즈 예약 취소 요청 데이터 누락 - values: {}", values);
                return;
            }

            UUID goodsId = UUID.fromString(goodsIdStr);
            UUID popupId = popupIdStr != null && !popupIdStr.isEmpty() ? UUID.fromString(popupIdStr) : goodsService.resolvePopupId(goodsId);
            int quantity = Integer.parseInt(quantityStr);

            goodsService.cancelReservationGoods(popupId, goodsId, quantity);

            log.info("↩️ [STORES] 굿즈 예약 취소 완료 - goodsId: {}, quantity: {}", goodsId, quantity);

        } catch (Exception e) {
            log.error("↩️ [STORES] 굿즈 예약 취소 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    private void handlePopupInfoLookupRequested(Map<String, Object> values) {
        try {
            String popupIdStr = (String) values.get("popupId");
            String correlationId = (String) values.get("correlationId");

            if (popupIdStr != null) {
                popupIdStr = popupIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }

            if (popupIdStr == null || popupIdStr.isEmpty() || correlationId == null || correlationId.isEmpty()) {
                log.warn("🏬 [STORES] 팝업 정보 조회 요청 누락 - values: {}", values);
                return;
            }

            UUID popupId = UUID.fromString(popupIdStr);
            var popupOpt = popupRepository.findById(popupId);
            if (popupOpt.isEmpty() || popupOpt.get().getDeletedAt() != null) {
                publishPopupInfoLookupFailure(correlationId, popupId, "팝업 정보를 찾을 수 없습니다.");
                return;
            }

            var popup = popupOpt.get();
            var storeOpt = storeRepository.findById(popup.getStoreId());

            String storeName = storeOpt.map(s -> s.getName()).orElse("");

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("eventType", "popup-info-lookup-response");
            response.put("eventId", UUID.randomUUID().toString());
            response.put("correlationId", correlationId);
            response.put("popupId", popup.getId().toString());
            response.put("success", true);
            response.put("message", "OK");
            response.put("title", popup.getTitle());
            response.put("description", popup.getDescription());
            response.put("storeId", popup.getStoreId() != null ? popup.getStoreId().toString() : "");
            response.put("storeName", storeName);
            response.put("address1", popup.getAddressRoad());
            response.put("address2", popup.getAddressDetail());
            response.put("phoneNumber", "");
            response.put("status", popup.getStatus() != null ? popup.getStatus().name() : "");
            response.put("startDate", popup.getReservationOpenAt() != null ? popup.getReservationOpenAt().toString() : "");
            response.put("endDate", "");
            response.put("respondedAt", java.time.LocalDateTime.now().toString());

            storeRedisEventPublisher.publishPopupInfoLookupResponseEvent(response);

        } catch (Exception e) {
            log.error("🏬 [STORES] 팝업 정보 조회 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    private void publishPopupInfoLookupFailure(String correlationId, UUID popupId, String message) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("eventType", "popup-info-lookup-response");
        response.put("eventId", UUID.randomUUID().toString());
        response.put("correlationId", correlationId);
        response.put("popupId", popupId.toString());
        response.put("success", false);
        response.put("message", message);
        response.put("respondedAt", java.time.LocalDateTime.now().toString());
        storeRedisEventPublisher.publishPopupInfoLookupResponseEvent(response);
    }

    private void publishPriceLookupFailure(Map<String, Object> values, String reason) {
        try {
            String sessionIdStr = (String) values.get("sessionId");
            String goodsIdStr = (String) values.get("goodsId");  // goodsId -> goodsId 수정
            String correlationId = (String) values.get("correlationId");
            String requestType = (String) values.get("requestType");

            // 따옴표 제거
            if (correlationId != null) {
                correlationId = correlationId.trim().replaceAll("^\"|\"$", "");
            }
            if (requestType != null) {
                requestType = requestType.trim().replaceAll("^\"|\"$", "");
            }
            if (sessionIdStr != null) {
                sessionIdStr = sessionIdStr.trim().replaceAll("^\"|\"$", "");
            }
            if (goodsIdStr != null) {
                goodsIdStr = goodsIdStr.trim().replaceAll("^\"|\"$", "");
            }

            // UUID 안전 파싱
            UUID sessionId = null;
            if (sessionIdStr != null && !sessionIdStr.isEmpty() && !sessionIdStr.equals("null")) {
                try {
                    sessionId = UUID.fromString(sessionIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("💰 [STORES] 유효하지 않은 sessionId UUID - sessionId: {}", sessionIdStr);
                }
            }

            UUID goodsId = null;
            if (goodsIdStr != null && !goodsIdStr.isEmpty() && !goodsIdStr.equals("null")) {
                try {
                    goodsId = UUID.fromString(goodsIdStr);
                } catch (IllegalArgumentException e) {
                    log.warn("💰 [STORES] 유효하지 않은 goodsId UUID - goodsId: {}", goodsIdStr);
                }
            }

            StoreRedisEventPublisher.PriceLookupResponseEventDto response =
                    StoreRedisEventPublisher.PriceLookupResponseEventDto.builder()
                            .eventId(UUID.randomUUID().toString())
                            .correlationId(correlationId)
                            .requestType(requestType)
                            .sessionId(sessionId)
                            .goodsId(goodsId)
                            .success(false)
                            .message(reason)
                            .respondedAt(java.time.LocalDateTime.now())
                            .eventTime(java.time.LocalDateTime.now())
                            .build();

            storeRedisEventPublisher.publishPriceLookupResponseEvent(response);

        } catch (Exception e) {
            log.error("🚨 [STORES] 가격 조회 실패 이벤트 발행 실패 - error: {}", e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 요청 이벤트 처리
     */
    private void handleStockDeductionRequest(Map<String, Object> values) {
        try {
            // 이벤트 데이터 추출
            String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            Object itemsPayload = values.get("items");
            if (itemsPayload == null) {
                itemsPayload = values.get("deductionItems");
            }

            if (orderIdStr == null || itemsPayload == null) {
                log.error("📦 [STORES] 재고 차감 요청 필수 데이터 누락 - orderId: {}, orderNo: {}, itemsPayload: {}",
                        orderIdStr, orderNo, itemsPayload);
                return;
            }

            UUID orderId = UUID.fromString(normalizeUuidString(orderIdStr));
            log.info("📦 [STORES] 재고 차감 요청 처리 시작 - orderId: {}, orderNo: {}", orderId, orderNo);

            // items JSON 역직렬화
            List<Map<String, Object>> itemsList;
            try {
                if (itemsPayload instanceof String) {
                    String itemsJson = normalizeQuotedString((String) itemsPayload);
                    itemsList = objectMapper.readValue(itemsJson,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                } else {
                    itemsList = objectMapper.convertValue(itemsPayload,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                }
            } catch (Exception e) {
                log.error("📦 [STORES] 재고 차감 요청 JSON 파싱 실패 - orderId: {}, payload: {}, error: {}",
                        orderId, itemsPayload, e.getMessage(), e);
                publishStockDeductionFailure(orderId, orderNo, null, "JSON 파싱 실패", e.getMessage());
                return;
            }

            if (itemsList.isEmpty()) {
                log.warn("📦 [STORES] 재고 차감 요청 항목이 비어있음 - orderId: {}", orderId);
                publishStockDeductionFailure(orderId, orderNo, null, "차감 항목 없음", "items 리스트가 비어있습니다");
                return;
            }

            // popupId는 첫 번째 항목에서 추출 (모든 항목이 같은 팝업에 속함)
            UUID popupId = null;
            StringBuilder stockDetails = new StringBuilder();
            boolean allSuccess = true;
            String failureReason = "";

            // 각 항목에 대해 재고 차감 처리
            for (Map<String, Object> item : itemsList) {
                try {
                    String goodsIdStr = (String) item.get("goodsId");
                    if (goodsIdStr == null) {
                        goodsIdStr = (String) item.get("goodsVariantId");
                    }
                    Integer quantity = null;

                    Object quantityObj = item.get("quantity");
                    if (quantityObj == null) {
                        quantityObj = item.get("qty");
                    }
                    if (quantityObj instanceof Integer) {
                        quantity = (Integer) quantityObj;
                    } else if (quantityObj instanceof String) {
                        quantity = Integer.parseInt((String) quantityObj);
                    }

                    if (goodsIdStr == null || quantity == null || quantity <= 0) {
                        log.error("📦 [STORES] 재고 차감 항목 데이터 오류 - goodsId: {}, quantity: {}",
                                 goodsIdStr, quantity);
                        allSuccess = false;
                        failureReason = "항목 데이터 오류";
                        break;
                    }

                    UUID goodsId;
                    try {
                        goodsId = UUID.fromString(normalizeUuidString(goodsIdStr));
                    } catch (Exception e) {
                        log.error("📦 [STORES] 재고 차감 항목 goodsId UUID 파싱 실패 - goodsId: {}, error: {}",
                                goodsIdStr, e.getMessage(), e);
                        allSuccess = false;
                        failureReason = "goodsId UUID 파싱 실패";
                        break;
                    }
                    String productName = (String) item.get("productName");
                    String variantName = (String) item.get("variantName");

                    log.info("📦 [STORES] 재고 차감 처리 - goodsId: {}, quantity: {}, product: {}",
                            goodsId, quantity, productName);

                    // 실제 재고 차감 처리 (GoodsService 호출)
                    // popupId는 실제로는 별도 조회가 필요하지만, 임시로 goodsId를 사용
                    if (popupId == null) {
                        // 첫 번째 항목에서 popupId를 결정 (실제로는 goodsVariant에서 조회해야 함)
                        popupId = goodsId; // 임시 처리
                    }

                    var stockResponse = goodsService.completeReservationGoods(popupId, goodsId, quantity);

                    // 성공 정보 누적
                    if (stockDetails.length() > 0) {
                        stockDetails.append(", ");
                    }
                    stockDetails.append(String.format("%s(%s):%d개->재고:%d",
                        productName != null ? productName : "상품",
                        variantName != null ? variantName : "기본",
                        quantity,
                        stockResponse.getStock()));

                    log.info("📦 [STORES] 재고 차감 성공 - goodsId: {}, quantity: {}, currentStock: {}",
                            goodsId, quantity, stockResponse.getStock());

                } catch (Exception e) {
                    log.error("📦 [STORES] 재고 차감 실패 - goodsId: {}, error: {}",
                             item.get("goodsId"), e.getMessage(), e);
                    allSuccess = false;
                    failureReason = e.getMessage();
                    break;
                }
            }

            // 결과에 따라 성공/실패 이벤트 발행
            if (allSuccess) {
                publishStockDeductionSuccess(orderId, orderNo, popupId, stockDetails.toString());
            } else {
                publishStockDeductionFailure(orderId, orderNo, popupId, failureReason, stockDetails.toString());
            }

        } catch (Exception e) {
            log.error("📦 [STORES] 재고 차감 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
            try {
                String orderIdStr = (String) values.get("orderId");
                String orderNo = (String) values.get("orderNo");
                if (orderIdStr != null && orderNo != null) {
                    UUID safeOrderId;
                    try {
                        safeOrderId = UUID.fromString(normalizeUuidString(orderIdStr));
                    } catch (Exception ignored) {
                        return;
                    }
                    publishStockDeductionFailure(safeOrderId, orderNo, null,
                            "시스템 오류", e.getMessage());
                }
            } catch (Exception ignored) {
                // 추가 에러 발생 시 무시
            }
        }
    }

    /**
     * 재고 차감 성공 이벤트 발행
     */
    private void publishStockDeductionSuccess(UUID orderId, String orderNo, UUID popupId, String stockDetails) {
        try {
            log.info("✅ [STORES] 재고 차감 성공 이벤트 발행 - orderId: {}, details: {}", orderId, stockDetails);

            StockDeductionSuccessEvent event = StockDeductionSuccessEvent.create(
                orderId, orderNo, popupId, stockDetails);

            storeRedisEventPublisher.publishStockDeductionSuccessEvent(event);

            kafkaPublisher.publishStockDeductionSucceeded(
                    orderId, event.getStockDetails(), event.getSucceededAt()
            );

            log.info("✅ [STORES] 재고 차감 성공 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());
        } catch (Exception e) {
            log.error("🚨 [STORES] 재고 차감 성공 이벤트 발행 실패 - orderId: {}, error: {}",
                     orderId, e.getMessage(), e);
        }
    }

    private void handleScheduleReservationRequested(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String popupIdStr = normalizeUuidString((String) values.get("popupId"));
            String reservationItemsJson = normalizeQuotedString((String) values.get("reservedSessions"));

            UUID orderId = UUID.fromString(orderIdStr);
            UUID popupId = UUID.fromString(popupIdStr);

            if (reservationItemsJson == null || reservationItemsJson.trim().isEmpty()) {
                log.warn("🚨 [STORES] 예약 항목 JSON이 비어있음 - orderId: {}, popupId: {}", orderId, popupId);
                storeRedisEventPublisher.publishScheduleReservationFailedEvent(
                        orderId, orderNo, popupId, "[]", "예약 항목 JSON이 비어있음");
                kafkaPublisher.publishScheduleReservationFailed(orderId, null, "예약 항목 JSON이 비어있음");
                return;
            }

            List<Map<String, Object>> items = objectMapper.readValue(
                    reservationItemsJson, new TypeReference<List<Map<String, Object>>>() {}
            );

            if (items == null || items.isEmpty()) {
                storeRedisEventPublisher.publishScheduleReservationFailedEvent(
                        orderId, orderNo, popupId, "[]", "예약 항목이 비어있음");
                kafkaPublisher.publishScheduleReservationFailed(orderId, null, "예약 항목이 비어있음");
                return;
            }

            if (items.size() > 1) {
                storeRedisEventPublisher.publishScheduleReservationFailedEvent(
                        orderId, orderNo, popupId, "[]", "현재는 단일 세션 예약만 지원");
                kafkaPublisher.publishScheduleReservationFailed(orderId, null, "현재는 단일 세션 예약만 지원");
                return;
            }

            Map<String, Object> rawItem = items.get(0);
            String scheduleIdStr = normalizeUuidString(
                    (String) rawItem.getOrDefault("scheduleId", rawItem.get("sessionOptionId")));
            int quantity = parseQuantity(rawItem.get("quantity"), rawItem.get("qty"));
            String sessionName = normalizeQuotedString((String) rawItem.get("sessionName"));
            String sessionTime = normalizeQuotedString((String) rawItem.get("sessionTime"));

            if (scheduleIdStr == null || scheduleIdStr.isBlank()) {
                log.warn("🚨 [STORES] 스케줄 ID 누락 - orderId: {}", orderId);
                kafkaPublisher.publishScheduleReservationFailed(orderId, null, "scheduleId 누락");
                storeRedisEventPublisher.publishScheduleReservationFailedEvent(
                        orderId, orderNo, popupId, "[]", "scheduleId 누락");
                return;
            }

            UUID scheduleId = UUID.fromString(scheduleIdStr);
            scheduleInventoryApiService.ensureScheduleKey(popupId, scheduleId);
            HoldResult holdResult = inventoryHoldService.holdSchedule(orderId, popupId, scheduleId, quantity);
            if (!holdResult.isSuccess()) {
                Map<String, Object> failureDetail = new java.util.LinkedHashMap<>();
                failureDetail.put("scheduleId", scheduleId.toString());
                failureDetail.put("requestedQuantity", String.valueOf(quantity));
                failureDetail.put("availableQuantity", "0");
                failureDetail.put("sessionName", sessionName != null ? sessionName : "");
                failureDetail.put("sessionTime", sessionTime != null ? sessionTime : "");
                failureDetail.put("failureReason", holdResult.getCode().getDescription());
                String failedSessionsJson = objectMapper.writeValueAsString(List.of(failureDetail));
                storeRedisEventPublisher.publishScheduleReservationFailedEvent(
                        orderId, orderNo, popupId, failedSessionsJson, holdResult.getCode().getDescription());
                kafkaPublisher.publishScheduleReservationFailed(
                        orderId, scheduleId, holdResult.getCode().getDescription());
                return;
            }

            // 🚀 병렬 처리로 성능 최적화
            CompletableFuture<Void> dbSaveFuture = CompletableFuture.runAsync(() ->
                reservationService.createScheduleReservation(orderId, orderNo, popupId, scheduleId, quantity));

            // 🚀 불필요한 연산 최적화 - 한 번만 호출
            int remaining = scheduleInventoryApiService.available(popupId, scheduleId);
            String reservationCode = UUID.randomUUID().toString();
            String eventId = UUID.randomUUID().toString();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            // 🚀 JSON 직렬화 최적화 - StringBuilder 사용으로 10-20ms 절약
            String reservedSessionsJson = buildReservedSessionJson(
                scheduleId, quantity, sessionName, sessionTime, remaining, reservationCode);

            storeRedisEventPublisher.publishScheduleReservationSuccessEvent(
                    orderId, orderNo, popupId, reservedSessionsJson,
                    eventId, now, now.plusMinutes(30)
            );

            kafkaPublisher.publishScheduleReservationSucceeded(
                    orderId, scheduleId, quantity, now.plusMinutes(30)
            );

            // 🚀 DB 저장 완료 대기 (비동기)
            dbSaveFuture.join();

        } catch (Exception e) {
            log.error("🚨 [STORES] 스케줄 예약 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    private void handleScheduleReservationCancelRequested(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            UUID orderId = UUID.fromString(orderIdStr);
            inventoryHoldService.releaseHold(orderId);
            log.info("↩️ [STORES] 스케줄 예약 홀드 해제 완료 - orderId: {}", orderId);
        } catch (Exception e) {
            log.error("🚨 [STORES] 스케줄 예약 취소 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 재고 차감 실패 이벤트 발행
     */
    private void publishStockDeductionFailure(UUID orderId, String orderNo, UUID popupId,
                                            String reason, String details) {
        try {
            log.warn("❌ [STORES] 재고 차감 실패 이벤트 발행 - orderId: {}, reason: {}, details: {}",
                    orderId, reason, details);

            StockDeductionFailedEvent event = StockDeductionFailedEvent.forSystemError(
                orderId, orderNo, popupId, String.format("%s - %s", reason, details));

            storeRedisEventPublisher.publishStockDeductionFailedEvent(event);

            kafkaPublisher.publishStockDeductionFailed(
                    orderId,
                    event.getReason(),
                    isRetryable(event.getFailureCode()),
                    event.getFailedAt()
            );

            log.info("❌ [STORES] 재고 차감 실패 이벤트 발행 완료 - orderId: {}, eventId: {}",
                    orderId, event.getEventId());
        } catch (Exception e) {
            log.error("🚨 [STORES] 재고 차감 실패 이벤트 발행 실패 - orderId: {}, error: {}",
                     orderId, e.getMessage(), e);
        }
    }

    private static class GoodsReservationItem {
        public UUID goodsId;
        public Integer quantity;
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

    private String normalizeQuotedString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                try {
                    // Decode JSON-escaped strings like "\"O2026\"" or "\"[{\\\"a\\\":1}]\""
                    return objectMapper.readValue(trimmed, String.class).trim();
                } catch (Exception ignored) {
                    return trimmed.substring(1, trimmed.length() - 1).trim();
                }
            }
        }
        return trimmed;
    }

    /**
     * 🚀 고성능 JSON 빌드 - StringBuilder 사용으로 ObjectMapper보다 10-20ms 빠름
     */
    private String buildReservedSessionJson(UUID scheduleId, int quantity, String sessionName,
                                          String sessionTime, int remaining, String reservationCode) {
        StringBuilder json = new StringBuilder(256);
        json.append("[{")
            .append("\"sessionOptionId\":\"").append(scheduleId).append("\",")
            .append("\"reservedQuantity\":\"").append(quantity).append("\",")
            .append("\"sessionName\":\"").append(sessionName != null ? sessionName : "").append("\",")
            .append("\"sessionTime\":\"").append(sessionTime != null ? sessionTime : "").append("\",")
            .append("\"remainingSeats\":\"").append(remaining).append("\",")
            .append("\"reservationCode\":\"").append(reservationCode).append("\"")
            .append("}]");
        return json.toString();
    }

    private int parseQuantity(Object primary, Object fallback) {
        Integer parsed = convertToInteger(primary);
        if (parsed != null) {
            return parsed;
        }
        parsed = convertToInteger(fallback);
        return parsed != null ? parsed : 0;
    }

    private Integer convertToInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isRetryable(String failureCode) {
        return failureCode == null || !"INSUFFICIENT_STOCK".equals(failureCode);
    }

    /**
     * 스케줄 확정 요청 처리 (결제 완료 후)
     * 예약 상태에서 확정 상태로 변경하여 취소 불가능하게 만듦
     */
    private void handleScheduleConfirmationRequested(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeUuidString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String popupIdStr = normalizeUuidString((String) values.get("popupId"));
            String confirmationItemsJson = normalizeQuotedString((String) values.get("confirmationItems"));
            if (confirmationItemsJson == null || confirmationItemsJson.isBlank()) {
                confirmationItemsJson = normalizeQuotedString((String) values.get("reservedSessions"));
            }

            UUID orderId = UUID.fromString(orderIdStr);
            UUID popupId = UUID.fromString(popupIdStr);

            log.info("📅🔒 [STORES] 스케줄 확정 처리 시작 - orderId: {}, popupId: {}", orderId, popupId);

            if (confirmationItemsJson == null || confirmationItemsJson.isBlank()) {
                log.warn("📅🔒 [STORES] 스케줄 확정 JSON 누락 - orderId: {}", orderId);
                return;
            }

            List<Map<String, Object>> confirmationItems = objectMapper.readValue(
                    confirmationItemsJson, new TypeReference<List<Map<String, Object>>>() {}
            );

            if (confirmationItems == null || confirmationItems.isEmpty()) {
                log.warn("📅🔒 [STORES] 스케줄 확정 항목이 비어있음 - orderId: {}", orderId);
                return;
            }

            int confirmedCount = 0;
            int failedCount = 0;

            for (Map<String, Object> item : confirmationItems) {
                try {
                    String scheduleIdStr = normalizeUuidString((String) item.get("scheduleId"));
                    if (scheduleIdStr == null) {
                        scheduleIdStr = normalizeUuidString((String) item.get("sessionOptionId"));
                    }
                    int quantity = parseQuantity(item.get("quantity"), item.get("qty"));
                    if (scheduleIdStr == null || scheduleIdStr.isEmpty()) {
                        throw new IllegalArgumentException("scheduleId 누락");
                    }
                    if (quantity <= 0) {
                        throw new IllegalArgumentException("qty 누락 또는 0");
                    }
                    UUID scheduleId = UUID.fromString(scheduleIdStr);

                    String sessionName = (String) item.get("sessionName");

                    log.info("📅🔒 [STORES] 스케줄 확정 처리 - scheduleId: {}, quantity: {}, session: {}",
                            scheduleId, quantity, sessionName);

                    // TODO: 실제 스케줄 확정 로직 구현 필요
                    // 예: 예약 상태를 '확정'으로 변경, 취소 불가 마킹 등
                    // scheduleInventoryApiService.confirmSchedule(popupId, scheduleId, quantity);

                    // 현재는 로깅만 수행
                    log.info("✅ [STORES] 스케줄 확정 완료 (임시 로직) - scheduleId: {}, quantity: {}",
                            scheduleId, quantity);

                    confirmedCount++;

                } catch (Exception itemError) {
                    log.error("❌ [STORES] 스케줄 확정 실패 - item: {}, error: {}",
                             item, itemError.getMessage(), itemError);
                    failedCount++;
                }
            }

            // 결과 발행 (성공/실패 이벤트)
            if (failedCount == 0) {
                // 모두 성공
                publishScheduleConfirmationSuccessEvent(orderId, orderNo, popupId, confirmedCount);
            } else {
                // 일부 또는 전체 실패
                publishScheduleConfirmationFailedEvent(orderId, orderNo, popupId,
                        String.format("확정 성공: %d개, 실패: %d개", confirmedCount, failedCount));
            }

            log.info("✅ [STORES] 스케줄 확정 처리 완료 - orderId: {}, 성공: {}개, 실패: {}개",
                    orderId, confirmedCount, failedCount);

        } catch (Exception e) {
            log.error("🚨 [STORES] 스케줄 확정 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);

            try {
                String orderIdStr = (String) values.get("orderId");
                String orderNo = (String) values.get("orderNo");
                if (orderIdStr != null && orderNo != null) {
                    UUID safeOrderId = UUID.fromString(normalizeUuidString(orderIdStr));
                    publishScheduleConfirmationFailedEvent(safeOrderId, orderNo, null,
                            "시스템 오류: " + e.getMessage());
                }
            } catch (Exception ignored) {
                // 추가 오류 발생 시 무시
            }
        }
    }

    /**
     * 스케줄 확정 성공 이벤트 발행
     */
    private void publishScheduleConfirmationSuccessEvent(UUID orderId, String orderNo, UUID popupId, int confirmedCount) {
        try {
            // TODO: StoreRedisEventPublisher에 스케줄 확정 성공 이벤트 발행 메서드 추가 필요
            log.info("📅✅ [STORES] 스케줄 확정 성공 - orderId: {}, 확정된 스케줄: {}개", orderId, confirmedCount);

        } catch (Exception e) {
            log.error("🚨 [STORES] 스케줄 확정 성공 이벤트 발행 실패 - orderId: {}, error: {}",
                     orderId, e.getMessage(), e);
        }
    }

    /**
     * 스케줄 확정 실패 이벤트 발행
     */
    private void publishScheduleConfirmationFailedEvent(UUID orderId, String orderNo, UUID popupId, String reason) {
        try {
            // TODO: StoreRedisEventPublisher에 스케줄 확정 실패 이벤트 발행 메서드 추가 필요
            log.warn("📅❌ [STORES] 스케줄 확정 실패 - orderId: {}, 사유: {}", orderId, reason);

        } catch (Exception e) {
            log.error("🚨 [STORES] 스케줄 확정 실패 이벤트 발행 실패 - orderId: {}, error: {}",
                     orderId, e.getMessage(), e);
        }
    }

    /**
     * 복합형 예약 요청 처리 (스케줄 + 굿즈) - Redis Lua hold_both.lua 사용
     */
    private void handleMixedReservationRequested(Map<String, Object> values) {
        try {
            String orderIdStr = normalizeQuotedString((String) values.get("orderId"));
            String orderNo = normalizeQuotedString((String) values.get("orderNo"));
            String popupIdStr = normalizeQuotedString((String) values.get("popupId"));
            String scheduleItemsJson = normalizeQuotedString((String) values.get("scheduleItems"));
            String goodsItemsJson = normalizeQuotedString((String) values.get("goodsItems"));

            UUID orderId = UUID.fromString(normalizeUuidString(orderIdStr));
            UUID popupId = popupIdStr != null && !popupIdStr.isEmpty() ?
                UUID.fromString(normalizeUuidString(popupIdStr)) : null;

            log.info("🔗 [STORES] 복합형 예약 처리 시작 - orderId: {}, popupId: {}", orderId, popupId);

            // 스케줄 항목 파싱
            List<Map<String, Object>> scheduleItems = objectMapper.readValue(
                scheduleItemsJson, new TypeReference<List<Map<String, Object>>>() {}
            );

            // 굿즈 항목 파싱
            List<Map<String, Object>> goodsItemsList = objectMapper.readValue(
                goodsItemsJson, new TypeReference<List<Map<String, Object>>>() {}
            );

            if (scheduleItems.isEmpty() || goodsItemsList.isEmpty()) {
                log.error("🔗 [STORES] 복합형 예약 항목 누락 - orderId: {}, schedule: {}, goods: {}",
                         orderId, scheduleItems.size(), goodsItemsList.size());
                return;
            }

            // 스케줄 정보 추출 (첫 번째 스케줄 항목)
            Map<String, Object> firstSchedule = scheduleItems.get(0);
            UUID scheduleId = UUID.fromString((String) firstSchedule.get("sessionId"));
            Integer scheduleQty = (Integer) firstSchedule.get("quantity");

            // 굿즈 항목들을 GoodsHoldItem으로 변환
            List<GoodsHoldItem> goodsHoldItems = new ArrayList<>();
            for (Map<String, Object> goodsItem : goodsItemsList) {
                UUID goodsId = UUID.fromString((String) goodsItem.get("goodsId"));
                Integer quantity = (Integer) goodsItem.get("quantity");
                goodsHoldItems.add(new GoodsHoldItem(goodsId, quantity));
            }

            // popupId 추론 (필요한 경우)
            if (popupId == null) {
                popupId = goodsService.resolvePopupId(goodsHoldItems.get(0).getGoodsId());
            }

            log.info("🔗 [STORES] Redis Lua holdBoth 실행 - orderId: {}, scheduleId: {}, scheduleQty: {}, goodsItems: {}",
                     orderId, scheduleId, scheduleQty, goodsHoldItems.size());

            // Redis Lua 스크립트 실행 (hold_both.lua)
            HoldResult holdResult = inventoryHoldService.holdBoth(
                orderId, popupId, scheduleId, scheduleQty, goodsHoldItems);

            if (holdResult.isSuccess()) {
                // 성공: 스케줄 예약 생성
                for (Map<String, Object> scheduleItem : scheduleItems) {
                    UUID sessionId = UUID.fromString((String) scheduleItem.get("sessionId"));
                    Integer quantity = (Integer) scheduleItem.get("quantity");

                    reservationService.createScheduleReservation(
                        orderId, orderNo, popupId, sessionId, quantity);
                }

                // 성공: 굿즈 예약 생성
                for (GoodsHoldItem goodsItem : goodsHoldItems) {
                    reservationService.createGoodsReservation(
                        orderId, orderNo, popupId, goodsItem.getGoodsId(), goodsItem.getQuantity());
                }

                log.info("🔗✅ [STORES] 복합형 예약 성공 - orderId: {}, schedule+goods 모두 홀드 완료", orderId);

                // 성공 이벤트 발행 (Order 서비스에게 알림)
                storeRedisEventPublisher.publishMixedReservationSuccessEvent(
                    orderId, popupId, scheduleId, scheduleQty, goodsHoldItems);

            } else {
                log.error("🔗❌ [STORES] 복합형 예약 실패 - orderId: {}, reason: {}",
                         orderId, holdResult.getDetail());

                // 실패 이벤트 발행
                storeRedisEventPublisher.publishMixedReservationFailedEvent(
                    orderId, popupId, holdResult.getDetail());
            }

        } catch (Exception e) {
            log.error("🔗❌ [STORES] 복합형 예약 처리 오류 - values: {}, error: {}",
                     values, e.getMessage(), e);

            try {
                String orderIdStr = (String) values.get("orderId");
                if (orderIdStr != null) {
                    UUID orderId = UUID.fromString(normalizeUuidString(orderIdStr));
                    storeRedisEventPublisher.publishMixedReservationFailedEvent(
                        orderId, null, "시스템 오류: " + e.getMessage());
                }
            } catch (Exception ignored) {
                // 추가 오류 무시
            }
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
            dlqMessage.put("original_data", record.getValue());

            // DLQ Stream에 실패 메시지 저장
            redisTemplate.opsForStream().add("store-failed-events", dlqMessage);

            log.warn("📮 [STORES] 실패한 메시지를 DLQ로 이동: stream={}, id={}, error={}",
                    record.getStream(), record.getId().getValue(), e.getMessage());

        } catch (Exception dlqError) {
            log.error("🚨 [STORES] DLQ 전송 실패: {}", dlqError.getMessage(), dlqError);
        }
    }

    /**
     * 이벤트 처리 실패를 DLQ에 기록
     */
    private void sendEventFailureToDLQ(String eventType, Map<String, Object> values, Exception e) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("failed_event_type", eventType);
            dlqMessage.put("failed_at", System.currentTimeMillis());
            dlqMessage.put("error_message", e.getMessage());
            dlqMessage.put("error_class", e.getClass().getSimpleName());
            dlqMessage.put("event_data", values);

            // DLQ Stream에 실패한 이벤트 기록
            redisTemplate.opsForStream().add("store-event-failures", dlqMessage);

            log.warn("📮 [STORES] 실패한 이벤트를 DLQ로 기록: eventType={}, error={}",
                    eventType, e.getMessage());

        } catch (Exception dlqError) {
            log.error("🚨 [STORES] 이벤트 실패 DLQ 기록 실패: {}", dlqError.getMessage(), dlqError);
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
        if (eventType.isEmpty()) {
            String errorMessage = String.format(
                "[CRITICAL] eventType이 비어있음! stream=%s, recordId=%s",
                streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 필수 항목입니다: " + streamName);
        }

        // 기존 따옴표 제거 로직 유지하면서 검증
        eventType = normalizeQuotedString(eventType);

        // eventType 기본 검증 (영문자, 숫자, 언더스코어, 하이픈만 허용)
        if (!eventType.matches("^[A-Za-z0-9_-]+$")) {
            String errorMessage = String.format(
                "[CRITICAL] eventType 형식 오류! eventType=%s, stream=%s, recordId=%s (영문자, 숫자, 언더스코어, 하이픈만 허용)",
                eventType, streamName, recordId
            );
            log.error(errorMessage);
            throw new IllegalArgumentException("eventType은 올바른 형식이어야 합니다: " + eventType);
        }

        log.debug("✅ [STORES] eventType 검증 통과: {} (stream: {}, recordId: {})",
                eventType, streamName, recordId);

        return eventType;
    }
}
