package com.popcorn.order.service.cache;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.popcorn.order.annotation.PerformanceMonitoring;
import com.popcorn.order.dto.response.OrderDetailResponse;
import com.popcorn.order.dto.response.OrderSummaryResponse;
import com.popcorn.order.dto.payment.PaymentUrlResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 서비스 캐시 관리
 *
 * 주문 관련 데이터의 캐시 생성, 조회, 갱신, 삭제를 담당합니다.
 * Spring Cache 추상화와 Redis를 활용하여 성능을 최적화합니다.
 *
 * 캐시 전략:
 * 1. Cache-Aside: 애플리케이션에서 직접 캐시 관리
 * 2. Write-Through: 데이터 변경 시 캐시도 함께 업데이트
 * 3. Cache-Eviction: 무효화된 데이터는 즉시 캐시에서 제거
 *
 * 캐시 키 설계 원칙:
 * - 의미 있는 네이밍 (order:detail:{orderId})
 * - 계층적 구조 (서비스:타입:식별자)
 * - 버전 정보 포함 (스키마 변경 시 대응)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 캐시 키 프리픽스 상수
    private static final String CACHE_KEY_PREFIX = "order:";
    private static final String ORDER_DETAIL_PREFIX = CACHE_KEY_PREFIX + "detail:";
    private static final String ORDER_LIST_PREFIX = CACHE_KEY_PREFIX + "list:user:";
    private static final String ORDER_STATS_PREFIX = CACHE_KEY_PREFIX + "stats:";
    private static final String MY_ORDERS_PREFIX = "my-orders::";
    private static final String PAYMENT_URL_PREFIX = CACHE_KEY_PREFIX + "payment-url:";

    /**
     * 주문 상세 정보 캐시 조회
     *
     * Spring의 @Cacheable 어노테이션을 사용하여 자동 캐시 처리합니다.
     * 캐시에 데이터가 있으면 메서드를 실행하지 않고 캐시된 값을 반환합니다.
     *
     * @param orderId 주문 ID
     * @return 캐시된 주문 상세 정보 (없으면 null)
     */
    @Cacheable(value = "orderDetail", key = "#orderId.toString()")
    @PerformanceMonitoring(threshold = 100, category = "cache")
    public OrderDetailResponse getOrderDetailFromCache(UUID orderId) {
        log.debug("주문 상세 캐시 조회 - 주문ID: {}", orderId);
        // 실제로는 이 메서드가 호출되지 않음 (캐시 히트 시)
        // 캐시 미스 시에만 실행되어 DB에서 데이터 조회
        return null;  // 실제 구현에서는 DB 조회 로직이 들어감
    }

    /**
     * 주문 상세 정보 캐시에 저장
     *
     * @CachePut은 메서드를 항상 실행하고 결과를 캐시에 저장합니다.
     * 데이터 변경 후 캐시를 갱신할 때 사용합니다.
     */
    @CachePut(value = "orderDetail", key = "#orderDetail.orderId.toString()")
    @PerformanceMonitoring(threshold = 200, category = "cache")
    public OrderDetailResponse putOrderDetailToCache(OrderDetailResponse orderDetail) {
        log.debug("주문 상세 캐시 저장 - 주문ID: {}, 주문번호: {}",
                orderDetail.getOrderId(), orderDetail.getOrderNo());
        return orderDetail;
    }

    /**
     * 주문 상세 정보 캐시에서 제거
     *
     * 주문이 수정되거나 삭제되었을 때 캐시 무효화를 위해 사용합니다.
     */
    @CacheEvict(value = "orderDetail", key = "#orderId.toString()")
    @PerformanceMonitoring(threshold = 50, category = "cache")
    public void evictOrderDetailFromCache(UUID orderId) {
        log.debug("주문 상세 캐시 제거 - 주문ID: {}", orderId);
    }

    /**
     * 사용자별 주문 목록 캐시 조회
     *
     * 페이징 파라미터까지 포함하여 캐시 키를 구성합니다.
     * SpEL(Spring Expression Language)을 사용하여 복합 키를 생성합니다.
     */
    @Cacheable(value = "orderList",
               key = "#userId + ':' + #page + ':' + #size + ':' + #sort")
    @PerformanceMonitoring(threshold = 300, category = "cache")
    public List<OrderSummaryResponse> getUserOrderListFromCache(Long userId, int page, int size, String sort) {
        log.debug("사용자 주문 목록 캐시 조회 - 사용자ID: {}, 페이지: {}/{}", userId, page, size);
        return null;  // 실제로는 DB 조회 로직
    }

    /**
     * 사용자별 주문 목록 캐시 무효화
     *
     * 해당 사용자의 모든 주문 목록 캐시를 제거합니다.
     * 와일드카드 패턴을 사용하여 관련된 모든 캐시를 찾아서 삭제합니다.
     */
    @PerformanceMonitoring(threshold = 100, category = "cache")
    public void evictUserOrderListCache(Long userId) {
        String pattern = ORDER_LIST_PREFIX + userId + ":*";

        try {
            // Redis KEYS 명령어로 패턴 매칭하여 키 조회
            // 주의: KEYS 명령어는 성능상 문제가 있으므로 프로덕션에서는 SCAN 사용 권장
            var keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("사용자 주문 목록 캐시 제거 - 사용자ID: {}, 제거된 키 개수: {}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("사용자 주문 목록 캐시 제거 실패 - 사용자ID: {}, 오류: {}", userId, e.getMessage());
        }
    }

    /**
     * 결제 URL 저장 (예약 성공 이후 생성된 링크)
     */
    @PerformanceMonitoring(threshold = 100, category = "cache")
    public void storePaymentUrl(UUID orderId, PaymentUrlResponse paymentUrlResponse) {
        if (orderId == null || paymentUrlResponse == null) {
            return;
        }
        String key = PAYMENT_URL_PREFIX + orderId;
        long ttlSeconds = 1800; // 기본 30분
        if (paymentUrlResponse.getExpiresAt() != null) {
            long seconds = java.time.Duration.between(
                    java.time.LocalDateTime.now(), paymentUrlResponse.getExpiresAt()).getSeconds();
            ttlSeconds = Math.max(seconds, 60);
        }
        redisTemplate.opsForValue().set(key, paymentUrlResponse, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 결제 URL 조회
     */
    @PerformanceMonitoring(threshold = 50, category = "cache")
    public PaymentUrlResponse getPaymentUrl(UUID orderId) {
        if (orderId == null) {
            return null;
        }
        String key = PAYMENT_URL_PREFIX + orderId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof PaymentUrlResponse response) {
            return response;
        }
        return null;
    }

    /**
     * 내 주문 타임라인 캐시 무효화
     *
     * OrderQueryService의 "my-orders" 캐시를 사용자 기준으로 삭제합니다.
     */
    @PerformanceMonitoring(threshold = 100, category = "cache")
    public void evictMyOrdersCache(Long userId) {
        String pattern = MY_ORDERS_PREFIX + userId + ":*";

        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("내 주문 타임라인 캐시 제거 - 사용자ID: {}, 제거된 키 개수: {}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("내 주문 타임라인 캐시 제거 실패 - 사용자ID: {}, 오류: {}", userId, e.getMessage());
        }
    }

    /**
     * 결제 URL 캐시 삭제 (결제 완료/취소 시)
     */
    @PerformanceMonitoring(threshold = 100, category = "cache")
    public void evictPaymentUrlCache(UUID orderId) {
        if (orderId == null) {
            return;
        }

        String key = PAYMENT_URL_PREFIX + orderId;
        try {
            redisTemplate.delete(key);
            log.debug("🗑️ 결제 URL 캐시 삭제 완료 - 주문ID: {}, key: {}", orderId, key);
        } catch (Exception e) {
            log.error("⚠️ 결제 URL 캐시 삭제 실패 - 주문ID: {}, 오류: {}", orderId, e.getMessage());
        }
    }

    /**
     * 주문 통계 데이터 캐시
     *
     * 계산 비용이 높은 통계 데이터를 장기간 캐시합니다.
     * TTL을 길게 설정하여 불필요한 재계산을 방지합니다.
     */
    @Cacheable(value = "orderStatistics", key = "#statsType + ':' + #period")
    @PerformanceMonitoring(threshold = 1000, category = "cache")
    public Map<String, Object> getOrderStatisticsFromCache(String statsType, String period) {
        log.debug("주문 통계 캐시 조회 - 타입: {}, 기간: {}", statsType, period);
        return null;  // 실제로는 복잡한 통계 계산 로직
    }

    /**
     * 캐시 워밍업
     *
     * 시스템 시작 시나 캐시 클리어 후에 자주 사용되는 데이터를 미리 캐시에 로드합니다.
     * 사용자의 첫 요청 시 발생하는 지연(Cold Start)을 방지합니다.
     */
    @PerformanceMonitoring(threshold = 5000, category = "cache")
    public void warmUpCache() {
        log.info("캐시 워밍업 시작");

        try {
            // 최근 24시간 내 주문이 많은 사용자들의 데이터를 미리 캐시
            List<Long> activeUsers = getActiveUserIds();  // 구현 필요

            for (Long userId : activeUsers) {
                // 첫 페이지 데이터를 미리 캐시
                getUserOrderListFromCache(userId, 0, 10, "createdAt");
            }

            // 자주 조회되는 통계 데이터 미리 캐시
            getOrderStatisticsFromCache("daily", "today");
            getOrderStatisticsFromCache("hourly", "today");

            log.info("캐시 워밍업 완료 - 대상 사용자: {}명", activeUsers.size());

        } catch (Exception e) {
            log.error("캐시 워밍업 실패", e);
        }
    }

    /**
     * 캐시 상태 모니터링
     *
     * 캐시 히트율, 사용량, 성능 등을 모니터링합니다.
     * 관리자 대시보드나 알림 시스템에서 활용할 수 있습니다.
     */
    @PerformanceMonitoring(threshold = 500, category = "cache")
    public Map<String, Object> getCacheStatistics() {
        try {
            // Redis INFO 명령어로 통계 정보 수집
            var info = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .info("stats");

            // 캐시별 키 개수 계산
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("orderDetailCacheSize", countCacheKeys(ORDER_DETAIL_PREFIX + "*"));
            stats.put("orderListCacheSize", countCacheKeys(ORDER_LIST_PREFIX + "*"));
            stats.put("orderStatsCacheSize", countCacheKeys(ORDER_STATS_PREFIX + "*"));
            stats.put("redisInfo", info);
            stats.put("timestamp", System.currentTimeMillis());

            log.debug("캐시 통계 수집 완료 - 상세:{}, 목록:{}, 통계:{}",
                    stats.get("orderDetailCacheSize"),
                    stats.get("orderListCacheSize"),
                    stats.get("orderStatsCacheSize"));

            return stats;

        } catch (Exception e) {
            log.error("캐시 통계 수집 실패", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 모든 주문 캐시 클리어
     *
     * 시스템 점검이나 데이터 마이그레이션 시 사용합니다.
     * 주의: 프로덕션 환경에서는 신중하게 사용해야 합니다.
     */
    @CacheEvict(value = {"orderDetail", "orderList", "orderStatistics"}, allEntries = true)
    @PerformanceMonitoring(threshold = 1000, category = "cache")
    public void clearAllOrderCache() {
        log.warn("모든 주문 캐시 클리어 실행");

        // 패턴 기반 캐시 클리어 (Spring Cache가 처리하지 못하는 부분)
        clearCacheByPattern(CACHE_KEY_PREFIX + "*");
    }

    /**
     * 만료된 캐시 정리
     *
     * Redis의 자동 만료 기능 외에 애플리케이션 레벨에서도 주기적으로 정리합니다.
     * 메모리 사용량 최적화와 성능 향상에 도움이 됩니다.
     */
    @PerformanceMonitoring(threshold = 2000, category = "cache")
    public int cleanExpiredCache() {
        int cleanedCount = 0;

        try {
            // TTL이 0이거나 음수인 키들을 찾아서 제거
            var allKeys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");

            if (allKeys != null) {
                for (String key : allKeys) {
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl <= 0) {
                        redisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
            }

            log.info("만료된 캐시 정리 완료 - 정리된 키: {}개", cleanedCount);

        } catch (Exception e) {
            log.error("만료된 캐시 정리 실패", e);
        }

        return cleanedCount;
    }

    // ========================= 유틸리티 메서드 =========================

    /**
     * 활성 사용자 ID 목록 조회 (캐시 워밍업용)
     */
    private List<Long> getActiveUserIds() {
        // TODO: 실제 구현 - 최근 활성 사용자 조회 로직
        return List.of(1L, 2L, 3L, 4L, 5L);
    }

    /**
     * 패턴에 매칭되는 캐시 키 개수 계산
     */
    private long countCacheKeys(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("캐시 키 개수 계산 실패 - 패턴: {}", pattern, e);
            return 0;
        }
    }

    /**
     * 패턴 기반 캐시 클리어
     */
    private void clearCacheByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("패턴 기반 캐시 클리어 완료 - 패턴: {}, 삭제된 키: {}개", pattern, keys.size());
            }
        } catch (Exception e) {
            log.error("패턴 기반 캐시 클리어 실패 - 패턴: {}", pattern, e);
        }
    }

}
