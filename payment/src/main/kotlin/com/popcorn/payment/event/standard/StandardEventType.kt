package com.popcorn.payment.event.standard

/**
 * 표준 이벤트 타입 정의 (Kotlin 버전)
 * 모든 마이크로서비스에서 사용할 표준화된 이벤트 타입
 */
enum class StandardEventType(val value: String) {

    // 📋 Order Events
    ORDER_CREATED("ORDER_CREATED"),
    ORDER_STATUS_UPDATED("ORDER_STATUS_UPDATED"),
    ORDER_DELETED("ORDER_DELETED"),
    ORDER_INFO_REQUEST("order-info-request"), // kebab-case 형식

    // 💳 Payment Events
    PAYMENT_CREATED("PAYMENT_CREATED"),
    PAYMENT_APPROVED("PAYMENT_APPROVED"),
    PAYMENT_FAILED("PAYMENT_FAILED"),
    PAYMENT_CANCELLED("PAYMENT_CANCELLED"),

    // ✅ CheckIn Events
    CHECKIN_CREATED("CHECKIN_CREATED"),

    // 🏪 Popup Events
    POPUP_CREATED("POPUP_CREATED"),
    POPUP_STATUS_UPDATED("POPUP_STATUS_UPDATED"),
    POPUP_UPDATED("POPUP_UPDATED"),
    POPUP_DELETED("POPUP_DELETED");

    override fun toString(): String = value
}