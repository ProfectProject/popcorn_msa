package com.popcorn.order.service;

import com.popcorn.order.dto.user.UserAddressResponse;
import com.popcorn.order.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Order 서비스의 User 주소 조회 서비스 (HTTP 동기식)
 * Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
 */
@Service
@RequiredArgsConstructor
@Slf4j
// @ConditionalOnProperty(name = "external.http.enabled", havingValue = "true")  // 임시 비활성화
public class OrderUserLookupService {

    private final UserServiceClient userServiceClient;

    /**
     * 사용자 기본 주소 조회 (HTTP 동기식)
     * 이전 Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
     */
    public Optional<UserAddressResponse> getDefaultAddress(Long userId) {
        try {
            log.info("🏠 [동기] 사용자 기본 주소 조회 시작 - userId: {}", userId);

            Optional<com.popcorn.order.client.dto.UserAddressResponse> clientResponse = userServiceClient.getDefaultAddress(userId);

            if (clientResponse.isPresent()) {
                // 클라이언트 응답을 기존 DTO로 변환
                com.popcorn.order.client.dto.UserAddressResponse response = clientResponse.get();
                UserAddressResponse userAddress = convertToUserAddressResponse(response);

                log.info("✅ [동기] 사용자 기본 주소 조회 성공 - userId: {}, addressName: {}",
                        userId, userAddress.getAddrName());
                return Optional.of(userAddress);
            } else {
                log.warn("⚠️ [동기] 사용자 기본 주소 없음 - userId: {}", userId);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("❌ [동기] 사용자 주소 조회 실패 - userId: {}, error: {}", userId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 클라이언트 응답을 기존 DTO로 변환
     */
    private UserAddressResponse convertToUserAddressResponse(com.popcorn.order.client.dto.UserAddressResponse clientResponse) {
        return UserAddressResponse.builder()
                .addrId(clientResponse.getAddressId() != null ? java.util.UUID.fromString(clientResponse.getAddressId()) : null)
                .addrName(clientResponse.getAddrName())
                .address1(clientResponse.getAddress1())
                .address2(clientResponse.getAddress2())
                .postalCode(clientResponse.getPostalCode())
                .isDefault(clientResponse.isDefault())
                .build();
    }
}
