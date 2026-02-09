package com.example.orderquery.domain.summary.mapper;

import com.example.orderquery.domain.summary.dto.KpiCountDto;
import com.example.orderquery.domain.summary.dto.OrderSummaryDto;
import com.example.orderquery.domain.summary.entity.OrderSummary;

public final class OrderSummaryMapper {

    private OrderSummaryMapper() {
    }

    public static OrderSummaryDto toDto(OrderSummary summary) {
        return OrderSummaryDto.builder()
                .popupId(summary.getPopupId())
                .storeId(summary.getStoreId())
                .ownerId(summary.getOwnerId())
                .popupTitle(summary.getPopupTitle())
                .popupStatus(summary.getPopupStatus())
                .addressRoad(summary.getAddressRoad())
                .addressDetail(summary.getAddressDetail())
                .reservationOpenAt(summary.getReservationOpenAt())
                .reservation(KpiCountDto.builder()
                        .total(summary.getReservationTotalOrders())
                        .paid(summary.getReservationPaidOrders())
                        .cancelled(summary.getReservationCancelledOrders())
                        .build())
                .goods(KpiCountDto.builder()
                        .total(summary.getGoodsTotalOrders())
                        .paid(summary.getGoodsPaidOrders())
                        .cancelled(summary.getGoodsCancelledOrders())
                        .build())
                .checkedInOrders(summary.getCheckedInOrders())
                .updatedAt(summary.getUpdatedAt())
                .build();
    }
}
