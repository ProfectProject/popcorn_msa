package com.example.orderquery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 📊 OrderQuery 전용 캐시 최적화 설정
 * - 대시보드 쿼리 캐싱 전략
 * - 지능적 TTL 설정
 * - 메모리 효율적 캐시 관리
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
@Slf4j
public class OrderQueryCacheConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectMapper objectMapper;

    /**
     * 🎯 OrderQuery 전용 캐시 매니저
     * - 대시보드별 차별화된 TTL 설정
     * - JSON 직렬화 최적화
     * - 메모리 사용량 최적화
     */
    @Bean
    public CacheManager orderQueryCacheManager() {
        log.info("🚀 [캐시 설정] OrderQuery 전용 캐시 매니저 초기화");

        // === 📊 기본 캐시 설정 ===
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
            .entryTtl(Duration.ofMinutes(10))  // 기본 TTL: 10분
            .disableCachingNullValues()
            .prefixCacheNameWith("orderquery:");

        // === 🎯 캐시별 차별화된 TTL 설정 ===
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 🏠 메인 대시보드 - 자주 업데이트되는 데이터
        cacheConfigurations.put("optimizedDashboard", defaultConfig
            .entryTtl(Duration.ofMinutes(5))  // 5분 TTL
            .prefixCacheNameWith("dashboard:main:"));

        // 🏪 스토어별 대시보드 - 중간 빈도 업데이트
        cacheConfigurations.put("optimizedDashboardByStore", defaultConfig
            .entryTtl(Duration.ofMinutes(7))  // 7분 TTL
            .prefixCacheNameWith("dashboard:store:"));

        // 📈 상세 통계 - 무거운 집계 쿼리
        cacheConfigurations.put("optimizedStatistics", defaultConfig
            .entryTtl(Duration.ofMinutes(15))  // 15분 TTL
            .prefixCacheNameWith("statistics:"));

        // 📋 주문 목록 - 자주 변경되는 데이터
        cacheConfigurations.put("orderList", defaultConfig
            .entryTtl(Duration.ofMinutes(3))   // 3분 TTL
            .prefixCacheNameWith("orders:list:"));

        // 🛍️ 인기 상품 - 상대적으로 안정적인 데이터
        cacheConfigurations.put("popularItems", defaultConfig
            .entryTtl(Duration.ofMinutes(20))  // 20분 TTL
            .prefixCacheNameWith("items:popular:"));

        // 📊 상태별 통계 - 실시간성이 중요한 데이터
        cacheConfigurations.put("statusSummary", defaultConfig
            .entryTtl(Duration.ofMinutes(2))   // 2분 TTL
            .prefixCacheNameWith("status:summary:"));

        // ⏰ 시간대별 분포 - 안정적인 패턴 데이터
        cacheConfigurations.put("hourlyDistribution", defaultConfig
            .entryTtl(Duration.ofMinutes(30))  // 30분 TTL
            .prefixCacheNameWith("hourly:dist:"));

        // 💳 결제 통계 - 중간 빈도 업데이트
        cacheConfigurations.put("paymentStats", defaultConfig
            .entryTtl(Duration.ofMinutes(10))  // 10분 TTL
            .prefixCacheNameWith("payment:stats:"));

        // 📈 트렌드 분석 - 장기간 안정적인 데이터
        cacheConfigurations.put("trendAnalysis", defaultConfig
            .entryTtl(Duration.ofHours(1))     // 1시간 TTL
            .prefixCacheNameWith("trend:analysis:"));

        // 🚨 긴급 알림 - 실시간 데이터
        cacheConfigurations.put("urgentAlerts", defaultConfig
            .entryTtl(Duration.ofMinutes(1))   // 1분 TTL
            .prefixCacheNameWith("urgent:alerts:"));

        // === 🔧 캐시 매니저 빌드 ===
        RedisCacheManager cacheManager = RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()  // 트랜잭션 지원
            .build();

        log.info("🚀 [캐시 설정] OrderQuery 캐시 매니저 설정 완료 - {} 개 캐시 타입 등록",
            cacheConfigurations.size());

        return cacheManager;
    }

    /**
     * 🔧 캐시 키 생성 전략 최적화
     */
    @Bean
    public org.springframework.cache.interceptor.KeyGenerator optimizedKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder keyBuilder = new StringBuilder();

            // 클래스명 추가
            keyBuilder.append(target.getClass().getSimpleName()).append(":");

            // 메서드명 추가
            keyBuilder.append(method.getName());

            // 파라미터 추가 (NULL 안전)
            for (Object param : params) {
                if (param != null) {
                    keyBuilder.append(":").append(param.toString());
                } else {
                    keyBuilder.append(":null");
                }
            }

            String key = keyBuilder.toString();

            // 키 길이 제한 (Redis 키 길이 최적화)
            if (key.length() > 200) {
                int hashCode = key.hashCode();
                key = key.substring(0, 180) + ":hash:" + Math.abs(hashCode);
            }

            log.debug("🔑 [캐시 키] 생성: {}", key);
            return key;
        };
    }

    /**
     * 📊 캐시 성능 모니터링 빈
     */
    @Bean
    public CachePerformanceMonitor cachePerformanceMonitor() {
        return new CachePerformanceMonitor();
    }

    /**
     * 🔍 캐시 성능 모니터링 클래스
     */
    public static class CachePerformanceMonitor {

        private long cacheHits = 0;
        private long cacheMisses = 0;
        private long totalQueries = 0;

        public void recordCacheHit() {
            cacheHits++;
            totalQueries++;
        }

        public void recordCacheMiss() {
            cacheMisses++;
            totalQueries++;
        }

        public double getCacheHitRatio() {
            return totalQueries > 0 ? (double) cacheHits / totalQueries : 0.0;
        }

        public void logPerformanceStats() {
            if (totalQueries % 100 == 0 && totalQueries > 0) { // 100회마다 로깅
                log.info("📊 [캐시 성능] Hit ratio: {:.2f}% (Hits: {}, Misses: {}, Total: {})",
                    getCacheHitRatio() * 100, cacheHits, cacheMisses, totalQueries);
            }
        }

        public void resetStats() {
            cacheHits = 0;
            cacheMisses = 0;
            totalQueries = 0;
        }
    }
}