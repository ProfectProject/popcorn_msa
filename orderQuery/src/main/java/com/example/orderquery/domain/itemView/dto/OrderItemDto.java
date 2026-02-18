package com.example.orderquery.domain.itemView.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


import com.example.orderquery.domain.itemView.entity.ItemType;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class OrderItemDto {

    private final UUID popupId;
    private final UUID orderGoodsId;
    private final UUID orderId;
    private final UUID storeId;
    private final Long userId;

    // 필드명 호환성을 위한 추가 필드들
    private final String orderNo;
    private final String orderNumber;  // orderNo의 별칭
    private final String orderStatus;
    private final String status;  // orderStatus의 별칭
    private final LocalDateTime orderedAt;
    private final LocalDateTime createdAt;  // orderedAt의 별칭
    private final LocalDateTime updatedAt;

    // 총 금액 관련
    private final int linePrice;
    private final BigDecimal totalAmount;  // linePrice의 BigDecimal 버전

    private final ItemType itemType;

    // schedule
    private final UUID scheduleId;
    private final LocalDateTime scheduleStartAt;
    private final LocalDateTime scheduleEndAt;

    // goods
    private final UUID goodsId;
    private final String goodsName;
    private final String stockUnit;

    private final int qty;
    private final int unitPrice;

    private final String paymentStatus;
    private final LocalDateTime paymentApprovedAt;

    private final boolean checkedIn;
    private final LocalDateTime checkinAt;

    // 추가 정보
    private final String popupName;  // 팝업명 (현재는 null이거나 기본값)
}