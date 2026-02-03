package com.popcorn.order.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.popcorn.order.client.StoreServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 가격 조회 서비스 (HTTP 동기식)
 * Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
 */
@Service
@RequiredArgsConstructor
@Slf4j
// @ConditionalOnProperty(name = "external.http.enabled", havingValue = "true")  // 임시 비활성화
public class OrderPriceLookupService {

    private final StoreServiceClient storeServiceClient;

    /**
     * 세션 가격 조회 (HTTP 동기식)
     * 이전 Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
     */
    public Integer requestSessionPrice(UUID sessionId) {
        try {
            log.info("💰 [동기] 세션 가격 조회 시작 - sessionId: {}", sessionId);
            Integer price = storeServiceClient.getSessionPrice(sessionId);

            if (price != null) {
                log.info("✅ [동기] 세션 가격 조회 성공 - sessionId: {}, price: {}원", sessionId, price);
            } else {
                log.warn("⚠️ [동기] 세션 가격 조회 결과 없음 - sessionId: {}", sessionId);
            }

            return price;
        } catch (Exception e) {
            log.error("❌ [동기] 세션 가격 조회 실패 - sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 굿즈 가격 조회 (HTTP 동기식)
     */
    public Integer requestGoodsPrice(UUID goodsId) {
        try {
            log.info("🎁 [동기] 굿즈 가격 조회 시작 - goodsId: {}", goodsId);
            Integer price = storeServiceClient.getGoodsPrice(goodsId);

            if (price != null) {
                log.info("✅ [동기] 굿즈 가격 조회 성공 - goodsId: {}, price: {}원", goodsId, price);
            } else {
                log.warn("⚠️ [동기] 굿즈 가격 조회 결과 없음 - goodsId: {}", goodsId);
            }

            return price;
        } catch (Exception e) {
            log.error("❌ [동기] 굿즈 가격 조회 실패 - goodsId: {}, error: {}", goodsId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 세션과 굿즈 가격을 병렬로 조회 (성능 최적화)
     * 동기식이지만 병렬 처리로 성능 향상
     */
    public CompletableFuture<StoreServiceClient.PriceResult> requestSessionAndGoodsPrice(UUID sessionId, UUID goodsId) {
        log.info("🚀 [동기] 병렬 가격 조회 시작 - sessionId: {}, goodsId: {}", sessionId, goodsId);
        return storeServiceClient.getSessionAndGoodsPrice(sessionId, goodsId);
    }
}
