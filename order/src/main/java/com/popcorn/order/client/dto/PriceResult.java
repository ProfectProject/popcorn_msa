package com.popcorn.order.client.dto;

/**
 * 병렬 가격 조회 결과를 담는 DTO
 * 세션 가격과 굿즈 가격을 함께 조회한 결과를 포함합니다.
 */
public class PriceResult {
    private final Integer sessionPrice;
    private final Integer goodsPrice;

    public PriceResult(Integer sessionPrice, Integer goodsPrice) {
        this.sessionPrice = sessionPrice;
        this.goodsPrice = goodsPrice;
    }

    public Integer getSessionPrice() {
        return sessionPrice;
    }

    public Integer getGoodsPrice() {
        return goodsPrice;
    }

    /**
     * 총 가격 계산 (세션 가격 + 굿즈 가격)
     * null인 경우 0으로 처리
     */
    public Integer getTotalPrice() {
        int session = sessionPrice != null ? sessionPrice : 0;
        int goods = goodsPrice != null ? goodsPrice : 0;
        return session + goods;
    }

    @Override
    public String toString() {
        return "PriceResult{" +
                "sessionPrice=" + sessionPrice +
                ", goodsPrice=" + goodsPrice +
                ", totalPrice=" + getTotalPrice() +
                '}';
    }
}