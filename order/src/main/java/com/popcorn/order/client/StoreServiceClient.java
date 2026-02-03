package com.popcorn.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.popcorn.order.client.dto.StoreApiResponse;
import com.popcorn.order.client.dto.PriceResponse;
import com.popcorn.order.client.dto.PriceResult;
import com.popcorn.order.dto.store.PopupInfoResponse;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Store 서비스와의 동기 HTTP 통신 클라이언트
 * Redis Stream 기반 비동기 방식을 HTTP 동기 방식으로 변경
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreServiceClient {

    private final WebClient defaultWebClient;

    @Value("${external.services.store.base-url}")
    private String storeServiceBaseUrl;

    @Value("${order.price-lookup.timeout-ms:2500}")
    private long timeoutMs;

    // TypeReference를 정적 필드로 정의하여 익명 클래스 문제 해결
    private static final ParameterizedTypeReference<StoreApiResponse<PriceResponse>> PRICE_RESPONSE_TYPE =
            new ParameterizedTypeReference<StoreApiResponse<PriceResponse>>() {};

    private static final ParameterizedTypeReference<StoreApiResponse<PopupInfoResponse>> POPUP_RESPONSE_TYPE =
            new ParameterizedTypeReference<StoreApiResponse<PopupInfoResponse>>() {};

    /**
     * 세션 가격 조회 (동기식)
     * Store 서비스의 실제 API 호출
     */
    public Integer getSessionPrice(UUID sessionId) {
        try {
            log.info("💰 [HTTP] 세션 가격 조회 요청 - sessionId: {}", sessionId);

            // 현재 요청의 Authorization 헤더 가져오기
            String currentAuthHeader = getCurrentAuthorizationHeader();

            StoreApiResponse<PriceResponse> response = defaultWebClient
                    .get()
                    .uri(storeServiceBaseUrl + "/api/stores/v1/sessions/{sessionId}/price", sessionId)
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
                        log.warn("💰 [HTTP] 세션 가격 정보 없음 - sessionId: {}", sessionId);
                        return clientResponse.createException();
                    })
                    .bodyToMono(PRICE_RESPONSE_TYPE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response != null && response.getData() != null && response.getData().getPrice() != null) {
                log.info("✅ [HTTP] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, response.getData().getPrice());
                return response.getData().getPrice();
            } else {
                log.warn("⚠️ [HTTP] 세션 가격 응답이 null - sessionId: {}", sessionId);
                return null;
            }

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("💰 [HTTP] 세션 가격 정보 없음 - sessionId: {}", sessionId);
                return null;
            } else {
                log.error("❌ [HTTP] 세션 가격 조회 실패 - sessionId: {}, status: {}, error: {}",
                        sessionId, e.getStatusCode(), e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("❌ [HTTP] 세션 가격 조회 예외 - sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 굿즈 가격 조회 (동기식)
     * Store 서비스의 실제 API 호출
     */
    public Integer getGoodsPrice(UUID goodsId) {
        try {
            log.info("🎁 [HTTP] 굿즈 가격 조회 요청 - goodsId: {}", goodsId);

            // 현재 요청의 Authorization 헤더 가져오기
            String currentAuthHeader = getCurrentAuthorizationHeader();

            StoreApiResponse<PriceResponse> response = defaultWebClient
                    .get()
                    .uri(storeServiceBaseUrl + "/api/stores/v1/goods/{goodsId}/price", goodsId)
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
                        log.warn("🎁 [HTTP] 굿즈 가격 정보 없음 - goodsId: {}", goodsId);
                        return clientResponse.createException();
                    })
                    .bodyToMono(PRICE_RESPONSE_TYPE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response != null && response.getData() != null && response.getData().getPrice() != null) {
                log.info("✅ [HTTP] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, response.getData().getPrice());
                return response.getData().getPrice();
            } else {
                log.warn("⚠️ [HTTP] 굿즈 가격 응답이 null - goodsId: {}", goodsId);
                return null;
            }

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("🎁 [HTTP] 굿즈 가격 정보 없음 - goodsId: {}", goodsId);
                return null;
            } else {
                log.error("❌ [HTTP] 굿즈 가격 조회 실패 - goodsId: {}, status: {}, error: {}",
                        goodsId, e.getStatusCode(), e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("❌ [HTTP] 굿즈 가격 조회 예외 - goodsId: {}, error: {}", goodsId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 팝업 정보 조회 (동기식)
     * Store 서비스의 실제 API 호출
     */
    public PopupInfoResponse getPopupInfo(UUID popupId) {
        try {
            log.info("🏪 [HTTP] 팝업 정보 조회 요청 - popupId: {}", popupId);

            // 현재 요청의 Authorization 헤더 가져오기
            String currentAuthHeader = getCurrentAuthorizationHeader();

            StoreApiResponse<PopupInfoResponse> response = defaultWebClient
                    .get()
                    .uri(storeServiceBaseUrl + "/api/stores/v1/popups/{popupId}", popupId)
                    .headers(headers -> {
                        if (currentAuthHeader != null) {
                            headers.set("Authorization", currentAuthHeader);
                        }
                        headers.set("X-Internal-Service", "order-service");
                        headers.set("X-Internal-Call", "true");
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
                        log.warn("🏪 [HTTP] 팝업 정보 없음 - popupId: {}", popupId);
                        return clientResponse.createException();
                    })
                    .bodyToMono(POPUP_RESPONSE_TYPE)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response != null && response.getData() != null) {
                log.info("✅ [HTTP] 팝업 정보 조회 성공 - popupId: {}, title: {}",
                        popupId, response.getData().getTitle());
                return response.getData();
            } else {
                log.warn("⚠️ [HTTP] 팝업 정보 응답이 null - popupId: {}", popupId);
                return null;
            }

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("🏪 [HTTP] 팝업 정보 없음 - popupId: {}", popupId);
                return null;
            } else {
                log.error("❌ [HTTP] 팝업 정보 조회 실패 - popupId: {}, status: {}, error: {}",
                        popupId, e.getStatusCode(), e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("❌ [HTTP] 팝업 정보 조회 예외 - popupId: {}, error: {}", popupId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 세션과 굿즈 가격을 병렬로 조회 (성능 최적화)
     */
    public CompletableFuture<PriceResult> getSessionAndGoodsPrice(UUID sessionId, UUID goodsId) {
        log.info("🚀 [HTTP] 병렬 가격 조회 시작 - sessionId: {}, goodsId: {}", sessionId, goodsId);

        // 병렬 조회를 위한 비동기 실행
        CompletableFuture<Integer> sessionPriceFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return getSessionPrice(sessionId);
            } catch (Exception e) {
                log.error("❌ [HTTP] 세션 가격 조회 실패 - sessionId: {}", sessionId, e);
                return null;
            }
        });

        CompletableFuture<Integer> goodsPriceFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return getGoodsPrice(goodsId);
            } catch (Exception e) {
                log.error("❌ [HTTP] 굿즈 가격 조회 실패 - goodsId: {}", goodsId, e);
                return null;
            }
        });

        return CompletableFuture.allOf(sessionPriceFuture, goodsPriceFuture)
                .thenApply(v -> {
                    Integer sessionPrice = sessionPriceFuture.join();
                    Integer goodsPrice = goodsPriceFuture.join();

                    log.info("✅ [HTTP] 병렬 가격 조회 완료 - sessionPrice: {}원, goodsPrice: {}원",
                            sessionPrice, goodsPrice);

                    return new PriceResult(sessionPrice, goodsPrice);
                });
    }


    /**
     * 가격 응답 DTO
     */
    public static class PriceResponse {
        private Integer price;
        private String currency;
        private boolean available;

        // Getters and Setters
        public Integer getPrice() { return price; }
        public void setPrice(Integer price) { this.price = price; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        @Override
        public String toString() {
            return "PriceResponse{" +
                    "price=" + price +
                    ", currency='" + currency + '\'' +
                    ", available=" + available +
                    '}';
        }
    }

    /**
     * 병렬 가격 조회 결과 DTO
     */
    public static class PriceResult {
        private final Integer sessionPrice;
        private final Integer goodsPrice;

        public PriceResult(Integer sessionPrice, Integer goodsPrice) {
            this.sessionPrice = sessionPrice;
            this.goodsPrice = goodsPrice;
        }

        public Integer getSessionPrice() { return sessionPrice; }
        public Integer getGoodsPrice() { return goodsPrice; }

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