package com.popcorn.store.event.standard;

/**
 * 표준 이벤트 타입 정의
 * 모든 마이크로서비스에서 사용할 표준화된 이벤트 타입
 */
public enum StandardEventType {

    // 📋 Order Events
    ORDER_CREATED("ORDER_CREATED"),
    ORDER_STATUS_UPDATED("ORDER_STATUS_UPDATED"),
    ORDER_DELETED("ORDER_DELETED"),

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
    POPUP_INFO_UPDATED("POPUP_INFO_UPDATED"),
    POPUP_UPDATED("POPUP_UPDATED"),
    POPUP_DELETED("POPUP_DELETED");

    private final String value;

    StandardEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
