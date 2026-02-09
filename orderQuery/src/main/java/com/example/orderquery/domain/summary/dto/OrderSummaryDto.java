package com.example.orderquery.domain.summary.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class OrderSummaryDto {

    private final UUID popupId;
    private final UUID storeId;

    @JsonIgnore
    private final Long ownerId;

    private final String popupTitle;
    private final String popupStatus;

    private final String addressRoad;
    private final String addressDetail;
    private final LocalDateTime reservationOpenAt;

    // 예약/굿즈를 분리해서 보여줌
    private final KpiCountDto reservation;
    private final KpiCountDto goods;

    // 체크인은 예약만 의미 (그대로)
    private final int checkedInOrders;

    private final LocalDateTime updatedAt;
}
