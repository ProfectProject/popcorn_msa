package com.example.orderquery.domain.itemView.entity;

import java.time.LocalDateTime;
import java.util.UUID;


import com.popcorn.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "popup_order_items_view",
        indexes = {
                @Index(name = "idx_popup_items_popup_time", columnList = "popup_id, ordered_at DESC"),
                @Index(name = "idx_popup_items_status_time", columnList = "popup_id, order_status, ordered_at DESC"),
                @Index(name = "idx_popup_items_checkin", columnList = "popup_id, checked_in, ordered_at DESC"),
                @Index(name = "idx_popup_items_type_time", columnList = "popup_id, item_type, ordered_at DESC")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemView extends BaseEntity {

    @EmbeddedId
    private OrderItemViewId id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 30)
    private OrderStatus orderStatus;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    // schedule fields
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "schedule_start_at")
    private LocalDateTime scheduleStartAt;

    @Column(name = "schedule_end_at")
    private LocalDateTime scheduleEndAt;

    // goods fields
    @Column(name = "goods_variant_id")
    private UUID goodsId;

    @Column(name = "goods_name", length = 100)
    private String goodsName;

    @Column(name = "stock_unit", length = 64)
    private String stockUnit;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(name = "line_price", nullable = false)
    private int linePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_approved_at")
    private LocalDateTime paymentApprovedAt;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn;

    @Column(name = "checkin_at")
    private LocalDateTime checkinAt;

}
