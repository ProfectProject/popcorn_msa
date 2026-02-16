package com.example.orderquery.domain.summary.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.summary.dto.KpiCountDto;
import com.example.orderquery.domain.summary.dto.OrderSummaryDto;
import com.example.orderquery.domain.summary.exception.SummaryException;
import com.example.orderquery.domain.summary.mapper.OrderSummaryMapper;
import com.example.orderquery.domain.summary.repository.OrderSummaryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSummaryService {

    private final OrderSummaryRepository orderSummaryRepository;

    public OrderSummaryDto getSummary(UUID storeId, UUID popupId) {
        return orderSummaryRepository.findByStoreIdAndPopupId(storeId, popupId)
                .map(OrderSummaryMapper::toDto)
                .orElseGet(() -> createDefaultSummary(storeId, popupId));
    }

    private OrderSummaryDto createDefaultSummary(UUID storeId, UUID popupId) {
        // 기본 요약 정보 생성 (모든 값이 0인 기본 상태)
        return OrderSummaryDto.builder()
                .popupId(popupId)
                .storeId(storeId)
                .popupTitle("팝업 정보 없음")
                .popupStatus("UNKNOWN")
                .addressRoad("")
                .addressDetail("")
                .reservationOpenAt(null)
                .ownerId(null)
                .reservation(KpiCountDto.builder()
                        .total(0)
                        .paid(0)
                        .cancelled(0)
                        .build())
                .goods(KpiCountDto.builder()
                        .total(0)
                        .paid(0)
                        .cancelled(0)
                        .build())
                .checkedInOrders(0)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
