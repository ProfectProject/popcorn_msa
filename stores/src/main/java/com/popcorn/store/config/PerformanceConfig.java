package com.popcorn.store.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * 🚀 과부하 대응 성능 최적화 설정
 * - 캐싱: 자주 조회되는 팝업 정보 메모리 캐시
 * - 재시도: 일시적 장애 시 자동 재시도
 */
@Configuration
@EnableCaching
@EnableRetry
public class PerformanceConfig {

    /**
     * 🚀 Caffeine 캐시 매니저 설정
     * - popup-list: 팝업 목록 (5분 캐시, 최대 10,000개)
     * - popup-detail: 팝업 상세 (10분 캐시, 최대 50,000개)
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 기본 설정: 5분 캐시, 최대 1000개
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());

        return cacheManager;
    }

    /**
     * 🚀 팝업 목록용 캐시 설정
     */
    @Bean
    public CacheManager popupListCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("popup-list");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)           // 더 많은 목록 캐시
                .expireAfterWrite(Duration.ofMinutes(5))  // 5분 캐시
                .recordStats());
        return cacheManager;
    }

    /**
     * 🚀 팝업 상세용 캐시 설정
     */
    @Bean("popupDetailCaffeineCacheManager")
    public CacheManager popupDetailCaffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("popup-detail");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(50000)           // 많은 상세 정보 캐시
                .expireAfterWrite(Duration.ofMinutes(10)) // 10분 캐시
                .recordStats());
        return cacheManager;
    }
}
