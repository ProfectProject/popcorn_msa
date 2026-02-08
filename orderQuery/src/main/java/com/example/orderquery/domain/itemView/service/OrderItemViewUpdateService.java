package com.example.orderquery.domain.itemView.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderquery.domain.itemView.entity.OrderStatus;
import com.example.orderquery.domain.itemView.entity.PaymentStatus;
import com.example.orderquery.domain.itemView.repository.OrderItemViewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderItemViewUpdateService {

    private final OrderItemViewRepository orderItemViewRepository;

    public void updateOrderStatus(UUID popupId, UUID orderId, OrderStatus orderStatus) {
        if (popupId == null || orderId == null || orderStatus == null) {
            log.debug("Order status update ignored: missing identifiers or status (orderId={}, popupId={}, status={})",
                    orderId, popupId, orderStatus);
            return;
        }

        int updated = orderItemViewRepository.updateOrderStatus(popupId, orderId, orderStatus);
        if (updated == 0) {
            log.debug("Order status update did not touch any rows (orderId={}, popupId={})", orderId, popupId);
        }
    }

    public void updatePaymentStatus(UUID popupId,
                                    UUID orderId,
                                    PaymentStatus paymentStatus,
                                    LocalDateTime approvedAt) {
        if (popupId == null || orderId == null || paymentStatus == null) {
            log.debug("Payment status update ignored: missing identifiers or status (orderId={}, popupId={}, status={})",
                    orderId, popupId, paymentStatus);
            return;
        }

        int updated = orderItemViewRepository.updatePaymentStatus(popupId, orderId, paymentStatus, approvedAt);
        if (updated == 0) {
            log.debug("Payment status update did not touch any rows (orderId={}, popupId={})", orderId, popupId);
        }
    }
}
