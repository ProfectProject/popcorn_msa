package com.popcorn.order.entity;

import java.util.UUID;

import com.popcorn.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 주문 아이템 엔티티 (JPA)
 * p_order_goods 테이블과 매핑
 *
 * [초보자 가이드]
 * 이 클래스는 주문에 포함된 각각의 상품 정보를 저장합니다.
 * 하나의 주문(Order)은 여러개의 주문 아이템(OrderItem)을 가질 수 있습니다.
 * 예: 팝콘 3개 + 음료 2개 = 2개의 OrderItem
 */
@Entity
@Table(name = "p_order_goods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderItem extends BaseEntity {

    /** 주문 아이템 ID (Primary Key) */
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "order_goods_id")
    private UUID id;

    /** 주문 ID - 이 아이템이 속한 주문의 ID */
    @Column(name = "order_id")
    private UUID orderId;

    /** 팝업 ID - 이 아이템이 속한 팝업의 ID */
    @Column(name = "popup_id")
    private UUID popupId;

    /** 주문 아이템 타입 (예약/굿즈) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::orders.itemtype")
    @Column(name = "item_type", columnDefinition = "orders.itemtype")
    private ItemType orderItemType;

    /** 세션 옵션 ID - 예약형 상품의 경우 시간 슬롯 정보 */
    @Column(name = "schedule_id")
    private UUID sessionOptionId;

    /** 굿즈 변형 ID - 구매형 상품의 상품 정보 (색상, 사이즈 등) */
    @Column(name = "goods_variant_id")
    private UUID goodsId;

    /** 수량 - 주문한 개수 */
    @Column(name = "qty")
    private Integer qty;

    /** 단가 - 개당 가격 (원 단위) */
    @Column(name = "unit_price")
    private Integer unitPrice;

    /** 라인 금액 - 이 아이템의 총 금액 (단가 × 수량) */
    @Column(name = "price")
    private Integer lineAmount;

    // ========================= 편의 메서드 =========================

    /**
     * 예약형 아이템인지 확인
     * @return 예약 아이템이면 true
     *
     * [초보자 가이드]
     * 예약형은 시간과 장소가 정해진 서비스 (팝업 참여 예약 등)
     */
    public boolean isReservationType() {
        return ItemType.RESERVATION.equals(orderItemType);
    }

    /**
     * 굿즈형 아이템인지 확인
     * @return 굿즈 아이템이면 true
     *
     * [초보자 가이드]
     * 굿즈형은 물리적인 상품 (티셔츠, 굿즈 등)
     */
    public boolean isGoodsType() {
        return ItemType.GOODS.equals(orderItemType);
    }

}
