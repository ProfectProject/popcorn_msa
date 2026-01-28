package com.popcorn.order.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.order.dto.payment.CreatePaymentResponse;
import com.popcorn.order.dto.payment.PaymentUrlResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 성능 극한 최적화 캐싱 서비스
 *
 * 🚀 목표: 결제 처리 시간 90% 단축 (2000ms → 200ms)
 *
 * 캐싱 전략:
 * - 결제 토큰: 30분 TTL (결제 만료 시간과 동일)
 * - 결제 URL: 즉시 반환, 30분 TTL
 * - 결제 상태: 5분 TTL (빠른 동기화)
 * - 고객 결제 정보: 1시간 TTL
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    private static final String PAYMENT_TOKEN_PREFIX = "payment:token:";
    private static final String PAYMENT_URL_PREFIX = "payment:url:";
    private static final String PAYMENT_STATUS_PREFIX = "payment:status:";
    private static final String CUSTOMER_PAYMENT_PREFIX = "payment:customer:";

    /**
     * 초고속 결제 토큰 캐싱 (목표: 10ms 이내)
     */
    @Cacheable(value = "payment-tokens", key = "#orderId", unless = "#result == null")
    public String getCachedPaymentToken(UUID orderId) {
        try {
            String cacheKey = PAYMENT_TOKEN_PREFIX + orderId;
            String cachedToken = redisTemplate.opsForValue().get(cacheKey);

            if (cachedToken != null) {
                log.debug("🚀 결제 토큰 캐시 히트 - 주문ID: {}, 토큰길이: {}자", orderId, cachedToken.length());
                return cachedToken;
            }

            log.debug("⚡ 결제 토큰 캐시 미스 - 주문ID: {}", orderId);
            return null;
        } catch (Exception e) {
            log.warn("⚠️ 결제 토큰 캐시 조회 실패, 기본 처리 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * 결제 토큰 캐시 저장 (30분 TTL)
     */
    public void cachePaymentToken(UUID orderId, String token) {
        try {
            String cacheKey = PAYMENT_TOKEN_PREFIX + orderId;
            redisTemplate.opsForValue().set(cacheKey, token, Duration.ofMinutes(30));
            log.debug("💾 결제 토큰 캐시 저장 - 주문ID: {}, TTL: 30분", orderId);
        } catch (Exception e) {
            log.warn("⚠️ 결제 토큰 캐시 저장 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
        }
    }

    /**
     * 초고속 결제 URL 캐싱 (목표: 5ms 이내)
     */
    @Cacheable(value = "payment-urls", key = "#orderId", unless = "#result == null")
    public PaymentUrlResponse getCachedPaymentUrl(UUID orderId) {
        try {
            String cacheKey = PAYMENT_URL_PREFIX + orderId;
            String cachedData = redisTemplate.opsForValue().get(cacheKey);

            if (cachedData != null) {
                PaymentUrlResponse response = objectMapper.readValue(cachedData, PaymentUrlResponse.class);
                log.debug("🚀 결제 URL 캐시 히트 - 주문ID: {}, URL 길이: {}자", orderId,
                    response.getPaymentUrl() != null ? response.getPaymentUrl().length() : 0);
                return response;
            }

            log.debug("⚡ 결제 URL 캐시 미스 - 주문ID: {}", orderId);
            return null;
        } catch (Exception e) {
            log.warn("⚠️ 결제 URL 캐시 조회 실패, 기본 처리 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * 결제 URL 캐시 저장 (30분 TTL)
     */
    public void cachePaymentUrl(UUID orderId, PaymentUrlResponse response) {
        try {
            String cacheKey = PAYMENT_URL_PREFIX + orderId;
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, Duration.ofMinutes(30));
            log.debug("💾 결제 URL 캐시 저장 - 주문ID: {}, TTL: 30분", orderId);
        } catch (JsonProcessingException e) {
            log.warn("⚠️ 결제 URL 캐시 저장 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
        }
    }

    /**
     * 초고속 결제 상태 캐싱 (목표: 3ms 이내)
     */
    @Cacheable(value = "payment-status", key = "#orderId", unless = "#result == null")
    public String getCachedPaymentStatus(UUID orderId) {
        try {
            String cacheKey = PAYMENT_STATUS_PREFIX + orderId;
            String cachedStatus = redisTemplate.opsForValue().get(cacheKey);

            if (cachedStatus != null) {
                log.debug("🚀 결제 상태 캐시 히트 - 주문ID: {}, 상태: {}", orderId, cachedStatus);
                return cachedStatus;
            }

            return null;
        } catch (Exception e) {
            log.warn("⚠️ 결제 상태 캐시 조회 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * 결제 상태 캐시 저장 (5분 TTL - 빠른 동기화)
     */
    public void cachePaymentStatus(UUID orderId, String status) {
        try {
            String cacheKey = PAYMENT_STATUS_PREFIX + orderId;
            redisTemplate.opsForValue().set(cacheKey, status, Duration.ofMinutes(5));
            log.debug("💾 결제 상태 캐시 저장 - 주문ID: {}, 상태: {}, TTL: 5분", orderId, status);
        } catch (Exception e) {
            log.warn("⚠️ 결제 상태 캐시 저장 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
        }
    }

    /**
     * 고객별 결제 정보 캐싱 (1시간 TTL)
     */
    @Cacheable(value = "customer-payment", key = "#customerId", unless = "#result == null")
    public CustomerPaymentInfo getCachedCustomerPaymentInfo(Long customerId) {
        try {
            String cacheKey = CUSTOMER_PAYMENT_PREFIX + customerId;
            String cachedData = redisTemplate.opsForValue().get(cacheKey);

            if (cachedData != null) {
                CustomerPaymentInfo info = objectMapper.readValue(cachedData, CustomerPaymentInfo.class);
                log.debug("🚀 고객 결제 정보 캐시 히트 - 고객ID: {}", customerId);
                return info;
            }

            return null;
        } catch (Exception e) {
            log.warn("⚠️ 고객 결제 정보 캐시 조회 실패 - 고객ID: {}, 오류: {}", customerId, e.getMessage());
            return null;
        }
    }

    /**
     * 고객별 결제 정보 캐시 저장 (1시간 TTL)
     */
    public void cacheCustomerPaymentInfo(Long customerId, CustomerPaymentInfo info) {
        try {
            String cacheKey = CUSTOMER_PAYMENT_PREFIX + customerId;
            String jsonData = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(cacheKey, jsonData, Duration.ofHours(1));
            log.debug("💾 고객 결제 정보 캐시 저장 - 고객ID: {}, TTL: 1시간", customerId);
        } catch (JsonProcessingException e) {
            log.warn("⚠️ 고객 결제 정보 캐시 저장 실패 - 고객ID: {}, 오류: {}", customerId, e.getMessage());
        }
    }

    /**
     * 결제 완료 시 관련 캐시 무효화
     */
    @CacheEvict(value = {"payment-tokens", "payment-urls", "payment-status"}, key = "#orderId")
    public void evictPaymentCache(UUID orderId) {
        try {
            // 1. Redis 키 직접 삭제
            String tokenKey = PAYMENT_TOKEN_PREFIX + orderId;
            String urlKey = PAYMENT_URL_PREFIX + orderId;
            String statusKey = PAYMENT_STATUS_PREFIX + orderId;
            String orderPaymentUrlKey = "order:payment-url:" + orderId;

            redisTemplate.delete(tokenKey);
            redisTemplate.delete(urlKey);
            redisTemplate.delete(statusKey);
            redisTemplate.delete(orderPaymentUrlKey);

            // 2. Spring Cache Manager에서도 캐시 무효화
            try {
                var paymentUrlsCache = cacheManager.getCache("payment-urls");
                var paymentTokensCache = cacheManager.getCache("payment-tokens");
                var paymentStatusCache = cacheManager.getCache("payment-status");

                if (paymentUrlsCache != null) {
                    paymentUrlsCache.evict(orderId);
                }
                if (paymentTokensCache != null) {
                    paymentTokensCache.evict(orderId);
                }
                if (paymentStatusCache != null) {
                    paymentStatusCache.evict(orderId);
                }

                log.debug("🗑️ Spring Cache Manager 캐시 무효화 완료 - 주문ID: {}", orderId);
            } catch (Exception cacheEx) {
                log.warn("⚠️ Spring Cache Manager 무효화 실패 - 주문ID: {}, 오류: {}", orderId, cacheEx.getMessage());
            }

            log.debug("🗑️ 결제 관련 캐시 무효화 완료 - 주문ID: {} (Redis + Spring Cache)", orderId);
        } catch (Exception e) {
            log.warn("⚠️ 결제 캐시 무효화 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
        }
    }

    /**
     * 결제 통계 정보 (성능 모니터링용)
     */
    public PaymentCacheStats getPaymentCacheStats() {
        try {
            long tokenCount = redisTemplate.keys(PAYMENT_TOKEN_PREFIX + "*").size();
            long urlCount = redisTemplate.keys(PAYMENT_URL_PREFIX + "*").size();
            long statusCount = redisTemplate.keys(PAYMENT_STATUS_PREFIX + "*").size();
            long customerCount = redisTemplate.keys(CUSTOMER_PAYMENT_PREFIX + "*").size();

            return new PaymentCacheStats(tokenCount, urlCount, statusCount, customerCount);
        } catch (Exception e) {
            log.warn("⚠️ 결제 캐시 통계 조회 실패: {}", e.getMessage());
            return new PaymentCacheStats(0, 0, 0, 0);
        }
    }

    /**
     * 고객 결제 정보 DTO
     */
    public static class CustomerPaymentInfo {
        private String preferredMethod;
        private String customerKey;
        private LocalDateTime lastPaymentAt;

        public CustomerPaymentInfo() {}

        public CustomerPaymentInfo(String preferredMethod, String customerKey, LocalDateTime lastPaymentAt) {
            this.preferredMethod = preferredMethod;
            this.customerKey = customerKey;
            this.lastPaymentAt = lastPaymentAt;
        }

        // Getters and Setters
        public String getPreferredMethod() { return preferredMethod; }
        public void setPreferredMethod(String preferredMethod) { this.preferredMethod = preferredMethod; }
        public String getCustomerKey() { return customerKey; }
        public void setCustomerKey(String customerKey) { this.customerKey = customerKey; }
        public LocalDateTime getLastPaymentAt() { return lastPaymentAt; }
        public void setLastPaymentAt(LocalDateTime lastPaymentAt) { this.lastPaymentAt = lastPaymentAt; }
    }

    /**
     * 결제 캐시 통계 DTO
     */
    public static class PaymentCacheStats {
        private final long tokenCacheCount;
        private final long urlCacheCount;
        private final long statusCacheCount;
        private final long customerInfoCacheCount;

        public PaymentCacheStats(long tokenCacheCount, long urlCacheCount,
                                long statusCacheCount, long customerInfoCacheCount) {
            this.tokenCacheCount = tokenCacheCount;
            this.urlCacheCount = urlCacheCount;
            this.statusCacheCount = statusCacheCount;
            this.customerInfoCacheCount = customerInfoCacheCount;
        }

        public long getTokenCacheCount() { return tokenCacheCount; }
        public long getUrlCacheCount() { return urlCacheCount; }
        public long getStatusCacheCount() { return statusCacheCount; }
        public long getCustomerInfoCacheCount() { return customerInfoCacheCount; }
        public long getTotalCacheCount() {
            return tokenCacheCount + urlCacheCount + statusCacheCount + customerInfoCacheCount;
        }
    }
}