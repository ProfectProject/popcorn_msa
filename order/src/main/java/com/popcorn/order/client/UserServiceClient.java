package com.popcorn.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.core.ParameterizedTypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.popcorn.order.client.dto.UserAddressResponse;

/**
 * Users 서비스와의 동기 HTTP 통신 클라이언트
 * Redis Stream 기반 비동기 방식을 HTTP 동기 방식으로 변경
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final WebClient defaultWebClient;

    @Value("${external.services.users.base-url}")
    private String usersServiceBaseUrl;

    @Value("${order.user-lookup.timeout-ms:1500}")
    private long timeoutMs;

    /**
     * 사용자 기본 주소 조회 (동기식)
     * Users 서비스의 실제 API 경로 사용: /api/users/v1/users/{userId}/addresses
     */
    public Optional<UserAddressResponse> getDefaultAddress(Long userId) {
        try {
            log.info("🏠 [HTTP] 사용자 주소 목록 조회 요청 - userId: {}", userId);

            // 현재 요청의 Authorization 헤더 가져오기
            String currentAuthHeader = getCurrentAuthorizationHeader();

            List<UserAddressResponse> addressList = defaultWebClient
                    .get()
                    .uri(usersServiceBaseUrl + "/api/users/v1/users/{userId}/addresses", userId)
                    .header("X-Internal-Service", "order-service")
                    .header("X-Internal-Call", "true")
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
                        log.warn("🏠 [HTTP] 사용자 주소 목록 없음 - userId: {}", userId);
                        return clientResponse.createException();
                    })
                    .bodyToMono(new ParameterizedTypeReference<List<UserAddressResponse>>() {})
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (addressList != null && !addressList.isEmpty()) {
                log.debug("🏠 [HTTP] 사용자 주소 목록 조회 성공 - userId: {}, 주소 개수: {}", userId, addressList.size());

                // 기본 주소 찾기 (isDefault가 true인 주소)
                Optional<UserAddressResponse> defaultAddress = addressList.stream()
                        .filter(UserAddressResponse::isDefault)
                        .findFirst();

                if (defaultAddress.isPresent()) {
                    log.info("✅ [HTTP] 사용자 기본 주소 조회 성공 - userId: {}, addressName: {}",
                            userId, defaultAddress.get().getAddrName());
                    return defaultAddress;
                } else {
                    log.warn("⚠️ [HTTP] 기본 주소가 설정되지 않음 - userId: {}", userId);
                    return Optional.empty();
                }
            } else {
                log.warn("⚠️ [HTTP] 사용자 주소 목록이 비어있음 - userId: {}", userId);
                return Optional.empty();
            }

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("🏠 [HTTP] 사용자 주소 목록 없음 - userId: {}", userId);
                return Optional.empty();
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.warn("🏠 [HTTP] 사용자 주소 조회 권한 없음 - userId: {}, 서비스 간 인증 설정 확인 필요", userId);
                return Optional.empty();
            } else {
                log.error("❌ [HTTP] 사용자 주소 조회 실패 - userId: {}, status: {}, error: {}",
                        userId, e.getStatusCode(), e.getMessage());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("❌ [HTTP] 사용자 주소 조회 예외 - userId: {}, error: {}", userId, e.getMessage(), e);
            return Optional.empty();
        }
    }


    /**
     * 현재 요청의 Authorization 헤더 추출
     */
    private String getCurrentAuthorizationHeader() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest().getHeader("Authorization");
        } catch (Exception e) {
            log.debug("Authorization 헤더 추출 실패: {}", e.getMessage());
            return null;
        }
    }
}