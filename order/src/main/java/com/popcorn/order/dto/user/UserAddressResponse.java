package com.popcorn.order.dto.user;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * User 서비스 주소 응답 DTO
 */
@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAddressResponse {
    private UUID addrId;
    private Long userId;
    private String addrName;
    private String address1;
    private String address2;
    private String postalCode;
    private Boolean isDefault;

    @JsonCreator
    public UserAddressResponse(
            @JsonProperty("addrId") UUID addrId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("addrName") String addrName,
            @JsonProperty("address1") String address1,
            @JsonProperty("address2") String address2,
            @JsonProperty("postalCode") String postalCode,
            @JsonProperty("isDefault") Boolean isDefault
    ) {
        this.addrId = addrId;
        this.userId = userId;
        this.addrName = addrName;
        this.address1 = address1;
        this.address2 = address2;
        this.postalCode = postalCode;
        this.isDefault = isDefault;
    }

    /**
     * 빈 주소 객체인지 확인 (캐싱용)
     */
    public boolean isEmpty() {
        return addrId == null && userId == null && addrName == null;
    }

    /**
     * 빈 주소 객체 생성 (캐싱용 - "주소 없음" 상태)
     */
    public static UserAddressResponse empty() {
        return UserAddressResponse.builder().build();
    }

    /**
     * 캐시 조회용 getter (addrId 대신 addressId 사용하는 곳을 위해)
     */
    public UUID getAddressId() {
        return this.addrId;
    }
}
