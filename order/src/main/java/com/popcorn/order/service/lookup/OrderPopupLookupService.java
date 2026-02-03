package com.popcorn.order.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.popcorn.order.dto.store.PopupInfoResponse;
import com.popcorn.order.event.lookup.PopupInfoLookupRequestedEvent;
import com.popcorn.order.event.lookup.PopupInfoLookupResponseEvent;
import com.popcorn.order.event.publisher.RedisEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Order 서비스의 Popup 정보 조회 서비스 (개선된 BaseEvent 구조 사용)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPopupLookupService {

    private final RedisEventPublisher redisEventPublisher;
    private final ConcurrentHashMap<String, CompletableFuture<PopupInfoLookupResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${order.popup-lookup.timeout-ms:3000}")
    private long timeoutMs;

    /**
     * 팝업 정보 조회 (개선된 BaseEvent 구조 사용)
     */
    public Optional<PopupInfoResponse> getPopupInfo(UUID popupId) {
        try {
            String correlationId = UUID.randomUUID().toString();

            CompletableFuture<PopupInfoLookupResponseEvent> future = new CompletableFuture<>();
            pendingRequests.put(correlationId, future);

            // 개선된 BaseEvent 구조 사용
            PopupInfoLookupRequestedEvent requestEvent = PopupInfoLookupRequestedEvent.create(popupId, correlationId);

            redisEventPublisher.publishPopupInfoLookupRequestedEvent(
                requestEvent.getEventId().toString(),
                requestEvent.getRequestCorrelationId(),
                requestEvent.getPopupId());

            PopupInfoLookupResponseEvent response = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (response.isSuccess()) {
                return Optional.of(PopupInfoResponse.builder()
                        .popupId(response.getPopupId())
                        .title(response.getPopupName())
                        .storeId(UUID.fromString(response.getStoreId()))
                        .build());
            } else {
                log.warn("🏬 팝업 정보 조회 실패 - popupId: {}, message: {}", popupId, response.getMessage());
                return Optional.empty();
            }

        } catch (Exception e) {
            log.warn("🏬 팝업 정보 조회 응답 대기 실패 - popupId: {}, error: {}", popupId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 팝업 정보 조회 응답 처리
     */
    public void handlePopupInfoLookupResponse(PopupInfoLookupResponseEvent response) {
        String correlationId = response.getRequestCorrelationId();
        CompletableFuture<PopupInfoLookupResponseEvent> future = pendingRequests.remove(correlationId);

        if (future != null) {
            log.info("🏬 팝업 정보 조회 응답 처리 완료 - correlationId: {}, success: {}",
                    correlationId, response.isSuccess());
            future.complete(response);
        } else {
            log.warn("🏬 팝업 정보 조회 응답 - 대기 중인 요청 없음: correlationId: {}", correlationId);
        }
    }
}