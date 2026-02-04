package com.popcorn.payment.event.common

import com.popcorn.payment.event.domain.payment.PaymentSuccessEvent
import com.popcorn.payment.event.integration.PaymentSuccessIntegrationEvent

/**
 * Payment 도메인 이벤트 -> 외부 통합 이벤트 매퍼 모음
 */

fun PaymentSuccessEvent.toIntegrationEvent(): PaymentSuccessIntegrationEvent =
    PaymentSuccessIntegrationEvent(
        this.paymentId,
        this.orderId,
        this.orderNo,
        this.orderType,
        this.totalAmount,
        this.userId,
        this.orderItems?.map {
            PaymentSuccessIntegrationEvent.OrderItemInfo(
                it.id,
                it.productId,
                it.productName,
                it.quantity,
                it.price,
                it.itemType
            )
        },
        this.paidAt,
        this.paymentMethod,
        this.paymentKey
    )
