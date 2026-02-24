package com.popcorn.store.event.standard;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 표준 이벤트 라인 아이템 구조
 * 주문 내 개별 항목 정보 (예약/굿즈)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLineItem {

    /**
     * 아이템 타입: SCHEDULE(예약) 또는 GOODS(굿즈)
     */
    private String itemType;

    /**
     * 주문 내 개별 라인 ID
     */
    private UUID itemId;

    // === 예약 관련 필드 (itemType = "SCHEDULE") ===
    /**
     * 스케줄 ID (예약용)
     */
    private UUID scheduleId;

    /**
     * 스케줄 시작 시간
     */
    private String scheduleStartAt;

    /**
     * 스케줄 종료 시간
     */
    private String scheduleEndAt;

    // === 굿즈 관련 필드 (itemType = "GOODS") ===
    /**
     * 굿즈 변형 ID (굿즈용)
     */
    private UUID goodsId;

    /**
     * 굿즈 이름
     */
    private String goodsName;

    /**
     * 재고 단위 (예: "BLACK-M")
     */
    private String stockUnit;

    // === 공통 필드 ===
    /**
     * 주문 굿즈 ID
     */
    private UUID orderGoodsId;

    /**
     * 수량
     */
    @JsonAlias("quantity")
    private Integer qty;

    /**
     * 단가
     */
    private Integer unitPrice;

    /**
     * 라인 총 금액 (unitPrice * qty)
     */
    @JsonAlias("lineAmount")
    private Integer linePrice;
}
