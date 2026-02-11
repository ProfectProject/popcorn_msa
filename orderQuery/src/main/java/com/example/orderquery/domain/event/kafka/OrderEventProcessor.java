package com.example.orderquery.domain.event.kafka;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.itemView.entity.ItemType;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;
import com.example.orderquery.domain.itemView.service.OrderItemViewUpsertService;
import com.example.orderquery.domain.itemView.service.OrderItemViewUpdateService;
import com.example.orderquery.domain.summary.entity.EventType;
import com.example.orderquery.domain.summary.service.OrderSummaryDeltaService;
import com.example.orderquery.domain.summary.service.PopupSummaryUpsertService;
import com.example.orderquery.domain.itemView.service.CheckInItemUpsertService;
import com.example.orderquery.domain.itemView.service.OrderItemViewUpsertService.OrderItemLine;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderEventProcessor {

    private final OrderItemViewUpsertService orderItemViewUpsertService;
    private final OrderItemViewUpdateService orderItemViewUpdateService;
    private final OrderSummaryDeltaService orderSummaryDeltaService;
    private final PopupSummaryUpsertService popupSummaryUpsertService;
    private final CheckInItemUpsertService checkInItemUpsertService;
    private final ObjectMapper objectMapper;

    public void processEvent(String topic, Map<String, Object> envelope) {
        if (envelope == null) {
            log.warn("Ignored null event envelope for topic {}", topic);
            return;
        }

        String rawEventType = asString(envelope.get("eventType"));
        String normalizedEventType = normalizeEventType(rawEventType);
        UUID eventId = parseUUID(envelope.get("eventId"));
        LocalDateTime eventTime = parseEventTime(envelope);
        log.debug("📨 [ORDERQUERY] Received event {} (normalized={}) from {}", rawEventType, normalizedEventType, topic);

        Map<String, Object> payload = flattenPayload(envelope);

        switch (normalizedEventType) {
            case "ORDER_CREATED" -> handleOrderCreated(eventId, payload, eventTime);
            case "ORDER_PAID" -> handleOrderPaid(eventId, payload, eventTime);
            case "ORDER_STATUS_UPDATED" -> handleOrderStatusUpdated(payload);
            case "ORDER_CANCELLED" -> handleOrderCancelled(eventId, payload, eventTime);
            case "PAYMENT_APPROVED" -> handlePaymentStatus(payload, PaymentStatus.PAID, parseLocalDateTime(payload, "approvedAt", "paidAt"));
            case "PAYMENT_FAILED" -> handlePaymentStatus(payload, PaymentStatus.FAILED, null);
            case "PAYMENT_USER_CANCELLED" -> handlePaymentStatus(payload, PaymentStatus.CANCELLED, null);
            case "CHECKIN_CREATED" -> handleCheckInCreated(eventId, payload, eventTime);
            case "POPUP_CREATED" -> handlePopupLifecycle(eventId, payload, EventType.POPUP_CREATED, rawEventType, normalizedEventType);
            case "POPUP_STATUS_UPDATED" -> handlePopupLifecycle(eventId, payload, EventType.POPUP_STATUS_UPDATED, rawEventType, normalizedEventType);
            case "POPUP_INFO_UPDATED" -> handlePopupLifecycle(eventId, payload, EventType.POPUP_INFO_UPDATED, rawEventType, normalizedEventType);
            case "POPUP_UPDATED" -> handlePopupLifecycle(eventId, payload, EventType.POPUP_INFO_UPDATED, rawEventType, normalizedEventType);
            default -> log.debug("Unsupported event type received: {}", rawEventType);
        }
    }

    private void handleOrderCreated(UUID eventId, Map<String, Object> payload, LocalDateTime eventTime) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        if (popupId == null || orderId == null) {
            log.warn("ORDER_CREATED ignored: missing identifiers (popupId={}, orderId={})", popupId, orderId);
            return;
        }

        UUID storeId = parseUUID(payload, "storeId", "store_id");
        Long userId = parseLong(payload, "userId", "user_id");
        String orderNo = getString(payload, "orderNo", "order_no");
        LocalDateTime orderedAt = parseLocalDateTime(payload, "orderedAt", "createdAt", "timestamp", "occurredAt");
        OrderStatus status = parseOrderStatus(payload, "orderStatus", "status");
        List<OrderLine> lines = parseLines(payload);
        boolean hasReservation = detectReservation(payload, lines, getString(payload, "orderType", "order_type"));
        boolean hasGoods = detectGoods(payload, lines, getString(payload, "orderType", "order_type"));

        boolean applied = orderSummaryDeltaService.applyOrderCreated(eventId, orderId, popupId, hasReservation, hasGoods, eventTime);
        if (!applied) {
            log.debug("ORDER_CREATED skipped: event already applied or summary missing (orderId={}, popupId={})", orderId, popupId);
            return;
        }

        orderItemViewUpsertService.createFromOrder(storeId, popupId, orderId, userId, orderNo, orderedAt, status,
                mapToItemLines(lines));
    }

    private void handleOrderPaid(UUID eventId, Map<String, Object> payload, LocalDateTime eventTime) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        if (popupId == null || orderId == null) {
            log.warn("ORDER_PAID ignored: missing identifiers (popupId={}, orderId={})", popupId, orderId);
            return;
        }

        List<OrderLine> lines = parseLines(payload);
        boolean hasReservation = detectReservation(payload, lines, getString(payload, "orderType", "order_type"));
        boolean hasGoods = detectGoods(payload, lines, getString(payload, "orderType", "order_type"));

        orderSummaryDeltaService.applyOrderPaid(eventId, orderId, popupId, hasReservation, hasGoods, eventTime);
    }

    private void handleOrderStatusUpdated(Map<String, Object> payload) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        OrderStatus status = parseOrderStatus(payload, "toStatus", "status", "orderStatus");

        if (popupId == null || orderId == null || status == null) {
            log.debug("ORDER_STATUS_UPDATED skipped: missing identifiers or status");
            return;
        }

        orderItemViewUpdateService.updateOrderStatus(popupId, orderId, status);
    }

    private void handleOrderCancelled(UUID eventId, Map<String, Object> payload, LocalDateTime eventTime) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        if (popupId == null || orderId == null) {
            log.warn("ORDER_CANCELLED ignored: missing identifiers (popupId={}, orderId={})", popupId, orderId);
            return;
        }

        List<OrderLine> lines = parseLines(payload);
        boolean hasReservation = detectReservation(payload, lines, getString(payload, "orderType", "order_type"));
        boolean hasGoods = detectGoods(payload, lines, getString(payload, "orderType", "order_type"));

        orderSummaryDeltaService.applyOrderCancelled(eventId, orderId, popupId, hasReservation, hasGoods, eventTime);
        orderItemViewUpdateService.updateOrderStatus(popupId, orderId, OrderStatus.CANCELLED);
    }

    private void handlePaymentStatus(Map<String, Object> payload, PaymentStatus paymentStatus, LocalDateTime approvedAt) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        if (popupId == null || orderId == null) {
            log.debug("{} ignored: missing identifiers", paymentStatus);
            return;
        }

        orderItemViewUpdateService.updatePaymentStatus(popupId, orderId, paymentStatus, approvedAt);
    }

    private void handleCheckInCreated(UUID eventId, Map<String, Object> payload, LocalDateTime eventTime) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID orderId = parseUUID(payload, "orderId", "order_id");
        UUID orderGoodsId = parseUUID(payload, "orderGoodsId", "order_goods_id");
        UUID storeId = parseUUID(payload, "storeId", "store_id");
        LocalDateTime checkinAt = parseLocalDateTime(payload, "checkinAt", "checkin_at", "occurredAt");

        if (popupId == null || orderId == null || orderGoodsId == null || storeId == null) {
            log.warn("CHECKIN_CREATED ignored: missing identifiers");
            return;
        }

        boolean applied = orderSummaryDeltaService.applyCheckInCreated(eventId, orderId, popupId, eventTime);
        if (!applied) {
            log.debug("CHECKIN_CREATED skipped: already applied or missing summary (orderId={}, popupId={})",
                    orderId, popupId);
            return;
        }

        checkInItemUpsertService.updateFromCheckIn(storeId, popupId, orderGoodsId, checkinAt);
    }

    private void handlePopupLifecycle(UUID eventId, Map<String, Object> payload, EventType eventType,
                                     String rawEventType, String normalizedEventType) {
        UUID popupId = parseUUID(payload, "popupId", "popup_id");
        UUID storeId = parseUUID(payload, "storeId", "store_id");
        String title = getString(payload, "title");
        String status = getString(payload, "status");
        String addressRoad = getString(payload, "addressRoad", "address_road");
        String addressDetail = getString(payload, "addressDetail", "address_detail");
        LocalDateTime reservationOpenAt = parseLocalDateTime(payload, "reservationOpenAt", "reservation_open_at");

        Long ownerId = parseLong(payload, "ownerId", "owner_id");

        if (popupId == null || storeId == null) {
            log.warn("Popup event ignored: missing identifiers (popupId={}, storeId={})", popupId, storeId);
            return;
        }

        if (eventType == EventType.POPUP_CREATED) {
            popupSummaryUpsertService.createFromPopup(
                    eventId, eventType, popupId, storeId, ownerId, title, status, addressRoad, addressDetail, reservationOpenAt);
            return;
        }

        boolean isStatusEvent = "PopupStatusUpdatedEvent".equals(rawEventType) || "POPUP_STATUS_UPDATED".equals(normalizedEventType);
        boolean isInfoEvent = Set.of("PopupUpdatedEvent", "PopupInfoUpdatedEvent").contains(rawEventType)
                || "POPUP_INFO_UPDATED".equals(normalizedEventType)
                || "POPUP_UPDATED".equals(normalizedEventType);

        if (isStatusEvent) {
            popupSummaryUpsertService.updateStatusFromPopup(
                    eventId, EventType.POPUP_STATUS_UPDATED, popupId, storeId, ownerId, status);
        } else if (isInfoEvent) {
            popupSummaryUpsertService.updateInfoFromPopup(
                    eventId, EventType.POPUP_INFO_UPDATED, popupId, storeId, ownerId, title, status, addressRoad,
                    addressDetail, reservationOpenAt);
        } else {
            log.debug("Unsupported popup lifecycle event type: {} / {}", rawEventType, normalizedEventType);
        }
    }

    // --- helpers ---

    private Map<String, Object> flattenPayload(Map<String, Object> envelope) {
        if (envelope == null) {
            return Map.of();
        }

        Map<String, Object> merged = new HashMap<>(envelope);
        Object inner = envelope.get("payload");
        if (inner instanceof Map<?, ?> payload) {
            payload.forEach((k, v) -> merged.putIfAbsent(k.toString(), v));
        }
        return merged;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private String normalizeEventType(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace("-", "_")
                .replace(".", "_")
                .replace(" ", "_")
                .toUpperCase();
    }

    private UUID parseUUID(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            log.debug("UUID parsing failed for value: {}", value);
            return null;
        }
    }

    private UUID parseUUID(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            UUID uuid = parseUUID(source.get(key));
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private Long parseLong(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ex) {
                log.debug("Long parsing failed for key {} value {}", key, value);
            }
        }
        return null;
    }

    private String getString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String result = value.toString().trim();
            if (!result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    private LocalDateTime parseLocalDateTime(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            LocalDateTime parsed = toLocalDateTime(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDateTime parseEventTime(Map<String, Object> envelope) {
        LocalDateTime firstCandidate = parseLocalDateTime(envelope, "timestamp", "occurredAt", "eventTime", "createdAt");
        return firstCandidate != null ? firstCandidate : LocalDateTime.now();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof String text) {
            String cleaned = text.trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(cleaned);
            } catch (DateTimeParseException ignored) {
            }
            try {
                Instant instant = Instant.parse(cleaned);
                return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private List<OrderLine> parseLines(Map<String, Object> payload) {
        List<OrderLine> lines = new ArrayList<>();
        if (payload == null) {
            return lines;
        }

        List<?> raw = extractList(payload, "lines", "items", "orderItems");
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> lineMap)) {
                continue;
            }
            Map<String, Object> normalized = normalizeLineMap(lineMap);
            OrderLine line = buildOrderLine(normalized);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<OrderItemLine> mapToItemLines(List<OrderLine> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(this::toOrderItemLine)
                .filter(line -> line != null && line.getOrderGoodsId() != null)
                .toList();
    }

    private OrderItemLine toOrderItemLine(OrderLine source) {
        if (source == null) {
            return null;
        }
        return OrderItemLine.builder()
                .orderGoodsId(source.getOrderGoodsId())
                .itemType(source.getItemType())
                .goodsId(source.getGoodsId())
                .scheduleId(source.getScheduleId())
                .goodsName(source.getGoodsName())
                .stockUnit(source.getStockUnit())
                .qty(source.getQty())
                .unitPrice(source.getUnitPrice())
                .linePrice(source.getLinePrice())
                .scheduleStartAt(source.getScheduleStartAt())
                .scheduleEndAt(source.getScheduleEndAt())
                .build();
    }

    private List<?> extractList(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                return list;
            }
            if (value instanceof String text && text.startsWith("[")) {
                try {
                    return objectMapper.readValue(text, List.class);
                } catch (Exception ignore) {
                }
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeLineMap(Map<?, ?> raw) {
        Map<String, Object> normalized = new HashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key.toString(), value);
            }
        });
        return normalized;
    }

    private OrderLine buildOrderLine(Map<String, Object> data) {
        UUID lineId = parseUUID(data, "orderGoodsId", "itemId", "id", "order_goods_id");
        if (lineId == null) {
            return null;
        }

        ItemType itemType = resolveItemType(getString(data, "itemType", "item_type", "orderItemType"));
        UUID goodsId = parseUUID(data, "goodsId", "goods_id", "goodsVariantId", "goods_variant_id");
        UUID scheduleId = parseUUID(data, "scheduleId", "schedule_id", "sessionId");

        int qty = parseInteger(data, "qty", "quantity", "count");
        int unitPrice = parseInteger(data, "unitPrice", "unit_price", "price");
        int linePrice = parseInteger(data, "lineAmount", "line_amount", "total", "linePrice");

        LocalDateTime startAt = parseLocalDateTime(data, "scheduleStartAt", "schedule_start_at");
        LocalDateTime endAt = parseLocalDateTime(data, "scheduleEndAt", "schedule_end_at");

        return OrderLine.builder()
                .orderGoodsId(lineId)
                .itemType(itemType)
                .goodsId(goodsId)
                .scheduleId(scheduleId)
                .goodsName(getString(data, "goodsName", "name"))
                .stockUnit(getString(data, "stockUnit", "unit"))
                .qty(qty)
                .unitPrice(unitPrice)
                .linePrice(linePrice > 0 ? linePrice : qty * unitPrice)
                .scheduleStartAt(startAt)
                .scheduleEndAt(endAt)
                .build();
    }

    private int parseInteger(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private ItemType resolveItemType(String raw) {
        if (raw == null) {
            return ItemType.GOODS;
        }
        String normalized = raw.toUpperCase().replace("-", "_");
        if (normalized.contains("SCHEDULE") || normalized.contains("RESERV")) {
            return ItemType.RESERVATION;
        }
        if (normalized.contains("GOODS") || normalized.contains("PRODUCT")) {
            return ItemType.GOODS;
        }
        return ItemType.GOODS;
    }

    private boolean detectReservation(Map<String, Object> payload, List<OrderLine> lines, String orderType) {
        Boolean explicit = parseBoolean(payload, "hasReservation", "has_reservation");
        if (explicit != null) {
            return explicit;
        }
        if (orderType != null && orderType.toUpperCase().contains("RESERV")) {
            return true;
        }
        return lines.stream().anyMatch(line -> ItemType.RESERVATION.equals(line.getItemType()));
    }

    private boolean detectGoods(Map<String, Object> payload, List<OrderLine> lines, String orderType) {
        Boolean explicit = parseBoolean(payload, "hasGoods", "has_goods");
        if (explicit != null) {
            return explicit;
        }
        if (orderType != null) {
            String normalized = orderType.toUpperCase();
            if (normalized.contains("GOOD")) {
                return true;
            }
        }
        return lines.stream().anyMatch(line -> ItemType.GOODS.equals(line.getItemType()));
    }

    private Boolean parseBoolean(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = value.toString().trim();
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return null;
    }

    private OrderStatus parseOrderStatus(Map<String, Object> source, String... keys) {
        String value = getString(source, keys);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown order status value: {}", value);
            return null;
        }
    }

    @Getter
    @Builder
    private static class OrderLine {
        private final UUID orderGoodsId;
        private final ItemType itemType;
        private final UUID goodsId;
        private final UUID scheduleId;
        private final String goodsName;
        private final String stockUnit;
        private final int qty;
        private final int unitPrice;
        private final int linePrice;
        private final LocalDateTime scheduleStartAt;
        private final LocalDateTime scheduleEndAt;
    }
}
