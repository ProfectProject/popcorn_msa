package com.example.orderquery.domain.itemView.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.itemView.entity.ItemType;
import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderItemViewId;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;
import com.example.orderquery.domain.itemView.repository.OrderItemViewRepository;
import com.example.orderquery.domain.summary.entity.OrderSummary;
import com.example.orderquery.domain.summary.repository.OrderSummaryRepository;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderItemViewUpsertService {

    private final OrderItemViewRepository orderItemViewRepository;
    private final OrderSummaryRepository orderSummaryRepository;

    public void createFromOrder(UUID storeId,
                                UUID popupId,
                                UUID orderId,
                                Long userId,
                                String orderNo,
                                LocalDateTime orderedAt,
                                OrderStatus orderStatus,
                                List<OrderItemLine> lines) {
        if (popupId == null || orderId == null) {
            log.warn("Order item view creation skipped: missing order or popup id (orderId={}, popupId={})",
                    orderId, popupId);
            return;
        }
        Long resolvedUserId = userId != null ? userId : 0L;
        if (userId == null) {
            log.warn("Order item view userId missing - fallback to 0 (orderId={}, popupId={})", orderId, popupId);
        }


        UUID resolvedStoreId = resolveStoreId(storeId, popupId);
        if (resolvedStoreId == null) {
            log.warn("Order item view creation skipped: storeId not available (popupId={})", popupId);
            return;
        }

        if (lines == null || lines.isEmpty()) {
            log.warn("Order item view creation skipped: no line items (orderId={}, popupId={})", orderId, popupId);
            return;
        }

        LocalDateTime effectiveOrderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        OrderStatus statusToUse = orderStatus != null ? orderStatus : OrderStatus.REQUESTED;

        for (OrderItemLine line : lines) {
            if (line == null) {
                continue;
            }

            if (line.getOrderGoodsId() == null) {
                log.warn("Order item view creation skipped: line without id (orderId={}, popupId={})",
                        orderId, popupId);
                continue;
            }

            OrderItemViewId id = new OrderItemViewId(popupId, line.getOrderGoodsId());
            if (orderItemViewRepository.existsById(id)) {
                log.debug("Order item view already exists: orderGoodsId={} (orderId={}, popupId={})",
                        line.getOrderGoodsId(), orderId, popupId);
                continue;
            }

            OrderItemView itemView = OrderItemView.builder()
                    .id(id)
                    .orderId(orderId)
                    .storeId(resolvedStoreId)
                    .userId(resolvedUserId)
                    .orderNo(orderNo)
                    .orderStatus(statusToUse)
                    .orderedAt(effectiveOrderedAt)
                    .itemType(line.getItemType() != null ? line.getItemType() : ItemType.GOODS)
                    .scheduleId(line.getScheduleId())
                    .scheduleStartAt(line.getScheduleStartAt())
                    .scheduleEndAt(line.getScheduleEndAt())
                    .goodsId(line.getGoodsId())
                    .goodsName(line.getGoodsName())
                    .stockUnit(line.getStockUnit())
                    .qty(line.getQty())
                    .unitPrice(line.getUnitPrice())
                    .linePrice(line.getLinePrice())
                    .paymentStatus(PaymentStatus.READY)
                    .checkedIn(false)
                    .build();

            orderItemViewRepository.save(itemView);
        }
    }

    private UUID resolveStoreId(UUID storeId, UUID popupId) {
        if (storeId != null) {
            return storeId;
        }
        OrderSummary summary = orderSummaryRepository.findById(popupId).orElse(null);
        if (summary != null) {
            return summary.getStoreId();
        }
        return null;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class OrderItemLine {
        private UUID orderGoodsId;
        private ItemType itemType;
        private UUID goodsId;
        private UUID scheduleId;
        private String goodsName;
        private String stockUnit;
        private int qty;
        private int unitPrice;
        private int linePrice;
        private LocalDateTime scheduleStartAt;
        private LocalDateTime scheduleEndAt;
    }
}
