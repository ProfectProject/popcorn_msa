package com.popcorn.order.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상품 타입 Enum
 *
 * 주문 시스템에서 다루는 상품의 종류를 정의
 */
@Getter
@RequiredArgsConstructor
public enum ProductType {

    /** 굿즈 상품 (구매형) */
    GOODS("굿즈", "GOODS"),

    /** 세션 상품 (예약형) */
    SESSION("세션", "SESSION"),

    /** 팝업 아이템 */
    POPUP_ITEM("팝업 아이템", "POPUP_ITEM"),

    /** 일반 상품 */
    GENERAL("일반 상품", "GENERAL");

    /** 화면에 표시될 이름 */
    private final String displayName;

    /** 시스템에서 사용하는 코드 */
    private final String code;

    /**
     * ItemType으로부터 ProductType 추론
     *
     * @param itemType OrderItem의 타입
     * @return 해당하는 ProductType
     */
    public static ProductType fromItemType(com.popcorn.order.entity.ItemType itemType) {
        if (itemType == null) {
            return GENERAL;
        }

        switch (itemType) {
            case GOODS:
                return GOODS;
            case RESERVATION:
                return SESSION;
            default:
                return GENERAL;
        }
    }

    /**
     * 문자열로부터 ProductType 추론
     *
     * @param itemTypeStr 아이템 타입 문자열
     * @return 해당하는 ProductType
     */
    public static ProductType fromString(String itemTypeStr) {
        if (itemTypeStr == null) {
            return GENERAL;
        }

        switch (itemTypeStr.toUpperCase()) {
            case "GOODS":
                return GOODS;
            case "RESERVATION":
            case "SESSION":
                return SESSION;
            case "POPUP_ITEM":
                return POPUP_ITEM;
            default:
                return GENERAL;
        }
    }

    /**
     * 굿즈 타입인지 확인
     */
    public boolean isGoods() {
        return this == GOODS;
    }

    /**
     * 세션/예약 타입인지 확인
     */
    public boolean isSession() {
        return this == SESSION;
    }
}