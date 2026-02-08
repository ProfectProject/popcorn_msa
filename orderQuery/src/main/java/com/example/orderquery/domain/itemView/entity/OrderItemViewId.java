package com.example.orderquery.domain.itemView.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class OrderItemViewId implements Serializable {

    @Column(name = "popup_id", nullable = false)
    private UUID popupId;

    @Column(name = "order_goods_id", nullable = false)
    private UUID orderGoodsId;
}
