package com.example.orderquery.domain.summary.entity;

public enum EventType {

    // 주문
    ORDER_CREATED,              // total_orders +1

    // 결제
    PAYMENT_BECAME_PAID,        // paid_orders +1
    PAYMENT_BECAME_UNPAID,      // paid_orders -1 (환불/취소)

    // 주문 취소
    ORDER_BECAME_CANCELLED,     // cancelled_orders +1
    ORDER_CANCELLED_REVERTED,   // cancelled_orders -1 (복구가 있다면)

    // 체크인
    CHECKIN_CREATED,            // checked_in_orders +1
    CHECKIN_REVERTED,           // checked_in_orders -1 (있다면)

    // 팝업
    POPUP_CREATED,
    POPUP_INFO_UPDATED,
    POPUP_STATUS_UPDATED

}
