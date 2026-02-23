package com.popcorn.store.domain.popup.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 팝업 목록 전용 캐시 서비스
 * 자주 조회되는 팝업 목록에 대한 고속 캐싱 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopupListCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String POPUP_LIST_CACHE_KEY = "popup:list:%s";
    private static final String POPULAR_POPUP_LIST_KEY = "popup:list:popular";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10); // 10분 캐시
    private static final Duration POPULAR_CACHE_TTL = Duration.ofMinutes(5); // 인기 목록은 5분 캐시

    /**
     * 팝업 목록 캐시 조회
     */
    public PopupListResponse getCachedPopupList(String cacheKey) {
        try {
            String key = String.format(POPUP_LIST_CACHE_KEY, cacheKey);
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached == null) {
                return null;
            }

            if (cached instanceof String jsonString) {
                return objectMapper.readValue(jsonString, PopupListResponse.class);
            }

            return objectMapper.convertValue(cached, PopupListResponse.class);

        } catch (Exception e) {
            log.warn("⚠️ 팝업 목록 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 팝업 목록 캐시 저장
     */
    public void cachePopupList(String cacheKey, PopupListResponse response) {
        try {
            String key = String.format(POPUP_LIST_CACHE_KEY, cacheKey);
            String jsonValue = objectMapper.writeValueAsString(response);

            redisTemplate.opsForValue().set(key, jsonValue, CACHE_TTL);
            log.debug("✅ 팝업 목록 캐시 저장 완료: {}", key);

        } catch (Exception e) {
            log.warn("⚠️ 팝업 목록 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 인기 팝업 목록 캐시 조회 (키워드, 필터 없는 기본 목록)
     */
    public PopupListResponse getPopularPopupList() {
        try {
            Object cached = redisTemplate.opsForValue().get(POPULAR_POPUP_LIST_KEY);

            if (cached == null) {
                return null;
            }

            if (cached instanceof String jsonString) {
                return objectMapper.readValue(jsonString, PopupListResponse.class);
            }

            return objectMapper.convertValue(cached, PopupListResponse.class);

        } catch (Exception e) {
            log.warn("⚠️ 인기 팝업 목록 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 인기 팝업 목록 캐시 저장
     */
    public void cachePopularPopupList(PopupListResponse response) {
        try {
            String jsonValue = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(POPULAR_POPUP_LIST_KEY, jsonValue, POPULAR_CACHE_TTL);
            log.debug("✅ 인기 팝업 목록 캐시 저장 완료");

        } catch (Exception e) {
            log.warn("⚠️ 인기 팝업 목록 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 팝업 목록 캐시 삭제
     */
    public void evictPopupListCache(String cacheKey) {
        try {
            String key = String.format(POPUP_LIST_CACHE_KEY, cacheKey);
            redisTemplate.delete(key);
            log.debug("🗑️ 팝업 목록 캐시 삭제: {}", key);

        } catch (Exception e) {
            log.warn("⚠️ 팝업 목록 캐시 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 모든 팝업 목록 캐시 삭제 (팝업 정보 변경 시 사용)
     */
    public void evictAllPopupListCache() {
        try {
            // 패턴 매칭으로 모든 팝업 목록 캐시 삭제
            String pattern = String.format(POPUP_LIST_CACHE_KEY, "*");
            var keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("🗑️ 전체 팝업 목록 캐시 삭제: {} 개", keys.size());
            }

            // 인기 팝업 목록 캐시도 삭제
            redisTemplate.delete(POPULAR_POPUP_LIST_KEY);

        } catch (Exception e) {
            log.warn("⚠️ 전체 팝업 목록 캐시 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 캐시 키 생성 유틸리티
     */
    public String generateCacheKey(Long regionId, String category, String keyword,
                                   java.util.UUID storeId, Integer page, Integer size) {
        return String.format("%s_%s_%s_%s_%s_%s",
                regionId, category, keyword, storeId, page, size);
    }
}