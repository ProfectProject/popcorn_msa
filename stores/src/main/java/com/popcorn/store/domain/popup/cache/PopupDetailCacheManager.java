package com.popcorn.store.domain.popup.cache;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.service.PopupDetailCacheService;
import com.popcorn.store.domain.popup.repository.PopupQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 향상된 PopupDetail 캐시 관리자
 * - 캐시 워밍 (인기 팝업 사전 로드)
 * - 스마트 캐시 무효화
 * - 성능 모니터링
 */
@Component
@RequiredArgsConstructor
public class PopupDetailCacheManager {

    private static final Logger log = LoggerFactory.getLogger(PopupDetailCacheManager.class);

    private static final String POPUP_DETAIL_KEY_FORMAT = "popup:%s:detail:v2";
    private static final String POPUP_METRICS_KEY = "popup:cache:metrics";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PopupDetailCacheService popupDetailCacheService;
    private final PopupQueryRepository popupQueryRepository;

    public String buildDetailKey(UUID popupId) {
        return String.format(POPUP_DETAIL_KEY_FORMAT, popupId);
    }

    /**
     * 캐시 무효화 - 이전과 동일
     */
    public void evictDetail(UUID popupId) {
        if (popupId == null) {
            return;
        }
        String cacheKey = buildDetailKey(popupId);
        redisTemplate.delete(cacheKey);
        log.debug("🗑️ [캐시 무효화] popupId: {}, key: {}", popupId, cacheKey);

        // 메트릭 업데이트
        incrementMetric("cache.evictions");
    }

    /**
     * 💡 캐시 워밍: 애플리케이션 시작 시 인기 팝업 캐시 로드
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCache() {
        log.info("🔥 [캐시 워밍] 인기 팝업 사전 로드 시작");

        try {
            List<UUID> activePopupIds = popupQueryRepository.findActivePopupIds(20); // 상위 20개

            CompletableFuture[] futures = activePopupIds.stream()
                .map(popupId -> CompletableFuture.runAsync(() -> {
                    try {
                        PopupDetailQuery query = PopupDetailQuery.builder()
                            .popupId(popupId)
                            .build();
                        popupDetailCacheService.getPopupDetailCached(query);
                        log.debug("✅ [캐시 워밍] 완료: popupId={}", popupId);
                    } catch (Exception e) {
                        log.warn("⚠️ [캐시 워밍] 실패: popupId={}, error={}", popupId, e.getMessage());
                    }
                }))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).get(); // 모든 작업 완료 대기

            log.info("🔥 [캐시 워밍] 완료: {} 개 팝업 사전 로드", activePopupIds.size());
            incrementMetric("cache.warmup.success");

        } catch (Exception e) {
            log.error("❌ [캐시 워밍] 실패", e);
            incrementMetric("cache.warmup.failure");
        }
    }

    /**
     * 📊 스케줄링된 캐시 재워밍 (1시간마다)
     */
    @Scheduled(fixedDelay = 3600000) // 1시간
    @Async
    public void scheduledCacheWarmup() {
        log.info("⏰ [스케줄 캐시 워밍] 시작");
        warmUpCache();
    }

    /**
     * 🎯 스마트 캐시 프리로드 - 특정 팝업 미리 로드
     */
    @Async
    public CompletableFuture<Void> preloadPopup(UUID popupId) {
        return CompletableFuture.runAsync(() -> {
            try {
                String cacheKey = buildDetailKey(popupId);

                // 이미 캐시되어 있는지 확인
                if (redisTemplate.hasKey(cacheKey)) {
                    log.debug("✅ [프리로드] 이미 캐시됨: popupId={}", popupId);
                    return;
                }

                // 캐시 로드
                PopupDetailQuery query = PopupDetailQuery.builder()
                    .popupId(popupId)
                    .build();
                popupDetailCacheService.getPopupDetailCached(query);

                log.debug("🚀 [프리로드] 완료: popupId={}", popupId);
                incrementMetric("cache.preload.success");

            } catch (Exception e) {
                log.warn("⚠️ [프리로드] 실패: popupId={}, error={}", popupId, e.getMessage());
                incrementMetric("cache.preload.failure");
            }
        });
    }

    /**
     * 🧹 캐시 정리 - 만료된 캐시 정리 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void cleanupExpiredCache() {
        log.info("🧹 [캐시 정리] 만료된 캐시 정리 시작");

        try {
            // popup:*:detail:v2 패턴의 키들 중 TTL이 없는 것들 정리
            var keys = redisTemplate.keys("popup:*:detail:v2");
            int cleanedCount = 0;

            if (keys != null) {
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl == -1) { // TTL이 없는 키
                        redisTemplate.expire(key, Duration.ofMinutes(30)); // 30분 TTL 설정
                        cleanedCount++;
                    }
                }
            }

            log.info("🧹 [캐시 정리] 완료: {} 개 키에 TTL 설정", cleanedCount);
            incrementMetric("cache.cleanup.success");

        } catch (Exception e) {
            log.error("❌ [캐시 정리] 실패", e);
            incrementMetric("cache.cleanup.failure");
        }
    }

    /**
     * 📈 캐시 메트릭 증가
     */
    private void incrementMetric(String metricName) {
        try {
            String key = POPUP_METRICS_KEY + ":" + metricName;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofDays(1)); // 1일 보존
        } catch (Exception e) {
            // 메트릭 실패는 로깅만 하고 넘어감
            log.debug("메트릭 업데이트 실패: {}", metricName);
        }
    }

    /**
     * 📊 캐시 메트릭 조회
     */
    public Long getMetric(String metricName) {
        try {
            String key = POPUP_METRICS_KEY + ":" + metricName;
            String value = (String) redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.debug("메트릭 조회 실패: {}", metricName);
            return 0L;
        }
    }
}
