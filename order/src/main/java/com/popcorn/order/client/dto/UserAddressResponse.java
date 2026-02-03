package com.popcorn.order.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Users 서비스에서 반환하는 사용자 주소 정보 DTO
 */
public class UserAddressResponse {
    @JsonProperty("addrId")  // Users 서비스는 "addrId" 필드명 사용
    private String addressId;
    private String addrName;
    private String address1;
    private String address2;
    private String postalCode;

    @JsonProperty("isDefault") // Users 서비스는 "isDefault" 필드명 사용
    private boolean isDefault;

    // 기본 생성자
    public UserAddressResponse() {}

    // Getters and Setters
    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public String getAddrName() {
        return addrName;
    }

    public void setAddrName(String addrName) {
        this.addrName = addrName;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    public String getAddress2() {
        return address2;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault") // JSON 직렬화 시에도 "isDefault" 사용
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    @Override
    public String toString() {
        return "UserAddressResponse{" +
                "addressId='" + addressId + '\'' +
                ", addrName='" + addrName + '\'' +
                ", address1='" + address1 + '\'' +
                ", address2='" + address2 + '\'' +
                ", postalCode='" + postalCode + '\'' +
                ", isDefault=" + isDefault +
                '}';
    }
}