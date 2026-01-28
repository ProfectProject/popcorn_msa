package com.popcorn.order.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.popcorn.order.event.PriceLookupRequestedEvent;
import com.popcorn.order.event.PriceLookupResponseEvent;
import com.popcorn.order.event.RedisEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 가격 조회 요청/응답을 Redis 이벤트로 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPriceLookupService {

    private final RedisEventPublisher redisEventPublisher;

    private final ConcurrentHashMap<String, CompletableFuture<PriceLookupResponseEvent>> pendingResponses =
            new ConcurrentHashMap<>();

    @Value("${order.price-lookup.timeout-ms:200}")
    private long timeoutMs;

    public Integer requestSessionPrice(UUID sessionId) {
        PriceLookupResponseEvent response = requestPrice(
                PriceLookupRequestedEvent.forSession(sessionId, UUID.randomUUID().toString())
        );
        if (response != null && response.isSuccess() && response.getPrice() != null) {
            return response.getPrice();
        }
        return null;
    }

    public Integer requestGoodsPrice(UUID goodsId) {
        PriceLookupResponseEvent response = requestPrice(
                PriceLookupRequestedEvent.forGoods(goodsId, UUID.randomUUID().toString())
        );
        if (response != null && response.isSuccess() && response.getPrice() != null) {
            return response.getPrice();
        }
        return null;
    }

    public void handlePriceLookupResponse(PriceLookupResponseEvent response) {
        if (response == null || response.getCorrelationId() == null) {
            return;
        }
        CompletableFuture<PriceLookupResponseEvent> future = pendingResponses.remove(response.getCorrelationId());
        if (future != null) {
            future.complete(response);
        } else {
            log.debug("가격 조회 응답 매칭 실패 - correlationId: {}", response.getCorrelationId());
        }
    }

    private PriceLookupResponseEvent requestPrice(PriceLookupRequestedEvent request) {
        String correlationId = request.getCorrelationId();
        CompletableFuture<PriceLookupResponseEvent> future = new CompletableFuture<>();
        pendingResponses.put(correlationId, future);

        try {
            redisEventPublisher.publishPriceLookupRequestedEvent(request);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("가격 조회 응답 대기 실패 - correlationId: {}, error: {}",
                    correlationId, e.getMessage());
            return null;
        } finally {
            pendingResponses.remove(correlationId);
        }
    }
}
