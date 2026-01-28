package com.popcorn.order.service;

import com.popcorn.order.dto.user.UserAddressResponse;
import com.popcorn.order.event.UserAddressLookupRequestedEvent;
import com.popcorn.order.event.UserAddressLookupResponseEvent;
import com.popcorn.order.event.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Order 서비스의 User 주소 조회 서비스 (이벤트 기반)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderUserLookupService {

    private final RedisEventPublisher redisEventPublisher;

    // 비동기 응답 대기를 위한 맵
    private final ConcurrentHashMap<String, CompletableFuture<UserAddressLookupResponseEvent>> pendingRequests = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${order.user-lookup.timeout-ms:1200}")
    private long timeoutMs;

    /**
     * 사용자 기본 주소 조회 (이벤트 기반)
     */
    public Optional<UserAddressResponse> getDefaultAddress(Long userId) {
        try {
            String correlationId = UUID.randomUUID().toString();

            // 비동기 응답 대기를 위한 CompletableFuture 생성
            CompletableFuture<UserAddressLookupResponseEvent> future = new CompletableFuture<>();
            pendingRequests.put(correlationId, future);

            // User 주소 조회 요청 이벤트 발행
            UserAddressLookupRequestedEvent requestEvent = UserAddressLookupRequestedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .correlationId(correlationId)
                    .requestType("DEFAULT_ADDRESS")
                    .userId(userId)
                    .requestedAt(LocalDateTime.now())
                    .build();

            redisEventPublisher.publishUserAddressLookupRequest(requestEvent);
            log.info("사용자 주소 조회 요청 이벤트 발행 - userId: {}, correlationId: {}", userId, correlationId);

            // 응답 대기 (짧은 타임아웃)
            UserAddressLookupResponseEvent response = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (response.isSuccess() && response.getAddresses() != null && !response.getAddresses().isEmpty()) {
                return response.getAddresses().stream()
                        .filter(address -> Boolean.TRUE.equals(address.getIsDefault()))
                        .findFirst();
            }

            return Optional.empty();

        } catch (Exception e) {
            log.warn("사용자 주소 조회 응답 대기 실패 - userId: {}, error: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 사용자 기본 주소 조회 요청만 발행 (비동기 보강용)
     */
    public void requestDefaultAddressAsync(Long userId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            UserAddressLookupRequestedEvent requestEvent = UserAddressLookupRequestedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .correlationId(correlationId)
                    .requestType("DEFAULT_ADDRESS")
                    .userId(userId)
                    .requestedAt(LocalDateTime.now())
                    .build();

            redisEventPublisher.publishUserAddressLookupRequest(requestEvent);
            log.info("사용자 주소 조회 요청 이벤트 발행(비동기) - userId: {}, correlationId: {}", userId, correlationId);
        } catch (Exception e) {
            log.warn("사용자 주소 조회 요청 이벤트 발행 실패 - userId: {}, error: {}", userId, e.getMessage());
        }
    }

    /**
     * User 서비스로부터 주소 조회 응답 처리
     */
    public void handleUserAddressLookupResponse(UserAddressLookupResponseEvent response) {
        String correlationId = response.getCorrelationId();
        CompletableFuture<UserAddressLookupResponseEvent> future = pendingRequests.remove(correlationId);

        if (future != null) {
            log.info("사용자 주소 조회 응답 처리 완료 - correlationId: {}, success: {}",
                    correlationId, response.isSuccess());
            future.complete(response);
        } else {
            log.warn("해당하는 주소 조회 요청을 찾을 수 없음 - correlationId: {}", correlationId);
        }
    }
}
