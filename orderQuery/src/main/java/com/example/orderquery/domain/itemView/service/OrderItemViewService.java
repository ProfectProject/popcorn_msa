package com.example.orderquery.domain.itemView.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.itemView.dto.OrderItemDto;
import com.example.orderquery.domain.itemView.dto.OrderItemPageDto;
import com.example.orderquery.domain.itemView.dto.OrderItemQuery;
import com.example.orderquery.domain.itemView.dto.PageInfoDto;
import com.example.orderquery.domain.itemView.entity.OrderItemView;
import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;
import com.example.orderquery.domain.itemView.exception.ItemViewException;
import com.example.orderquery.domain.itemView.mapper.OrderItemMapper;
import com.example.orderquery.domain.itemView.repository.OrderItemViewRepository;
import com.example.orderquery.domain.itemView.repository.OrderItemViewSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderItemViewService {

    private final OrderItemViewRepository orderItemViewRepository;

    public OrderItemPageDto getItems(UUID storeId, UUID popupId, OrderItemQuery query) {
        validateDateRange(query);
        OrderStatus orderStatus = parseOrderStatus(query.getOrderStatus());
        PaymentStatus paymentStatus = parsePaymentStatus(query.getPaymentStatus());

        int size = Math.min(query.getSize(), 200);
        PageRequest pageRequest = PageRequest.of(query.getPage(), size, Sort.by(Sort.Direction.DESC, "orderedAt"));

        Page<OrderItemView> page = orderItemViewRepository.findAll(
                OrderItemViewSpecifications.byStorePopupAndFilters(storeId, popupId, query, orderStatus, paymentStatus),
                pageRequest);

        List<OrderItemDto> items = page.getContent()
                .stream()
                .map(OrderItemMapper::toDto)
                .toList();

        // 🔧 데이터가 없어도 빈 결과 반환 (예외 던지지 않음)
        // 프론트엔드에서 스토어별 데이터 표시를 위해 500 에러 대신 빈 목록 반환

        PageInfoDto pageInfo = PageInfoDto.builder()
                .page(page.getNumber())
                .currentPage(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();

        return OrderItemPageDto.builder()
                .orders(items)
                .pageInfo(pageInfo)
                .build();
    }

    private OrderStatus parseOrderStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ItemViewException.invalidOrderStatus(raw);
        }
    }

    private PaymentStatus parsePaymentStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ItemViewException.invalidPaymentStatus(raw);
        }
    }

    private void validateDateRange(OrderItemQuery query) {
        if (query.getFrom() != null && query.getTo() != null
                && !query.getFrom().isBefore(query.getTo())) {
            throw ItemViewException.invalidDateRange(
                    String.valueOf(query.getFrom()),
                    String.valueOf(query.getTo()));
        }
    }
}
