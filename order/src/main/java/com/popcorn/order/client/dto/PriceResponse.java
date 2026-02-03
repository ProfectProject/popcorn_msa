package com.popcorn.order.client.dto;

/**
 * Store 서비스에서 반환하는 가격 정보 DTO
 */
public class PriceResponse {
    private String goodsId;
    private String productName;
    private Integer price;
    private Integer originalPrice;
    private Integer discountRate;
    private Integer stockQuantity;
    private String status;
    private String currency;
    private boolean available;

    // 기본 생성자
    public PriceResponse() {}

    // Getters and Setters
    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Integer originalPrice) {
        this.originalPrice = originalPrice;
    }

    public Integer getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(Integer discountRate) {
        this.discountRate = discountRate;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return "PriceResponse{" +
                "goodsId='" + goodsId + '\'' +
                ", productName='" + productName + '\'' +
                ", price=" + price +
                ", originalPrice=" + originalPrice +
                ", discountRate=" + discountRate +
                ", stockQuantity=" + stockQuantity +
                ", status='" + status + '\'' +
                ", currency='" + currency + '\'' +
                ", available=" + available +
                '}';
    }
}