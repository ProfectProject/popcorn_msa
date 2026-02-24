package com.popcorn.store.domain.popup.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;

import lombok.RequiredArgsConstructor;

/**
 * 향상된 PopupDetail 캐시 서비스
 * - 지능적 TTL 설정
 * - 실시간 데이터 분리
 * - 캐시 성능 최적화
 */
@Service
@RequiredArgsConstructor
public class PopupDetailCacheService {

    private static final Logger log = LoggerFactory.getLogger(PopupDetailCacheService.class);
    private static final String POPUP_DETAIL_CACHE_KEY_FORMAT = "popup:%s:detail:v2";

    private final PopupQueryService popupQueryService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, LocalCacheEntry> localFallbackCache = new ConcurrentHashMap<>();

    @Value("${popup.detail.local-cache-ttl-seconds:60}")
    private int localCacheTtlSeconds;

    /**
     * 📊 지능적 TTL을 가진 팝업 상세 캐시
     */
    public PopupDetailResponse getPopupDetailCached(PopupDetailQuery query) {
        UUID popupId = query.getPopupId();
        if (popupId == null) {
            return null;
        }

        PopupDetailResponse localCached = getLocalFallback(popupId);
        if (localCached != null) {
            return localCached;
        }

        PopupDetailResponse cached = getCachedPopupDetail(popupId);
        if (cached != null) {
            putLocalFallback(cached);
            return cached;
        }

        PopupDetailResponse response;
        try {
            // 1. 캐시 미스 시 DB 조회
            response = popupQueryService.getPopupDetail(query);
            if (response == null) {
                return null;
            }
        } catch (Exception e) {
            PopupDetailResponse stale = getLocalFallback(popupId);
            if (stale != null) {
                log.warn("⚠️ [로컬 fallback 반환] popupId={}, cause={}", popupId, e.getMessage());
                return stale;
            }
            throw e;
        }

        // 2. 지능적 TTL 계산
        int intelligentTtl = calculateIntelligentTtl(response);

        // 3. 수동으로 캐시 저장 (동적 TTL 적용)
        cacheWithIntelligentTtl(response, intelligentTtl);
        putLocalFallback(response);

        // 최초 조회에서는 DB에서 받은 값을 그대로 반환하여 중복 DB fallback을 줄인다.
        return response;
    }

    private PopupDetailResponse getCachedPopupDetail(UUID popupId) {
        String key = buildCacheKey(popupId);

        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }

            log.debug("✅ [캐시 히트] popupId={}, key={}", popupId, key);
            if (cached instanceof String jsonString) {
                return objectMapper.readValue(jsonString, PopupDetailResponse.class);
            }
            return objectMapper.convertValue(cached, PopupDetailResponse.class);
        } catch (Exception e) {
            log.warn("⚠️ [캐시 조회 실패] popupId={}, key={}, error={}", popupId, key, e.getMessage());
            return null;
        }
    }

    public String buildCacheKey(UUID popupId) {
        return String.format(POPUP_DETAIL_CACHE_KEY_FORMAT, popupId);
    }

    private PopupDetailResponse getLocalFallback(UUID popupId) {
        LocalCacheEntry entry = localFallbackCache.get(popupId);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis < System.currentTimeMillis()) {
            localFallbackCache.remove(popupId);
            return null;
        }
        return entry.response;
    }

    private void putLocalFallback(PopupDetailResponse response) {
        if (response == null || response.getId() == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + Math.max(1, localCacheTtlSeconds) * 1000L;
        localFallbackCache.put(response.getId(), new LocalCacheEntry(response, expiresAt));
    }

    private record LocalCacheEntry(PopupDetailResponse response, long expiresAtMillis) {}

    /**
     * 🧠 지능적 TTL 계산 로직
     */
    private int calculateIntelligentTtl(PopupDetailResponse response) {
        LocalDateTime now = LocalDateTime.now();
        PopupStatus status = response.getStatus();

        // 상태별 기본 TTL
        int baseTtl = switch (status) {
            case OPEN -> 300;            // 5분 (오픈 상태)
            case APPROVED -> 450;        // 7.5분 (승인됨)
            case DRAFT -> 1800;          // 30분 (초안)
            case REQUEST -> 900;         // 15분 (요청됨)
            case CLOSED -> 3600;         // 1시간 (종료)
            case CANCELLED, HIDDEN -> 0; // 캐시하지 않음
            default -> 600;              // 10분 (기본값)
        };

        // 이벤트 시간에 따른 동적 TTL 조정
        if (response.getEventStartAt() != null && response.getEventEndAt() != null) {
            LocalDateTime eventStart = response.getEventStartAt();
            LocalDateTime eventEnd = response.getEventEndAt();

            if (now.isBefore(eventStart)) {
                // 이벤트 시작 전: 긴 TTL
                long hoursUntilStart = ChronoUnit.HOURS.between(now, eventStart);
                if (hoursUntilStart > 24) {
                    baseTtl = Math.min(baseTtl * 4, 7200); // 최대 2시간
                } else if (hoursUntilStart > 1) {
                    baseTtl = Math.min(baseTtl * 2, 3600); // 최대 1시간
                }
            } else if (now.isAfter(eventEnd)) {
                // 이벤트 종료 후: 매우 긴 TTL
                baseTtl = Math.min(baseTtl * 6, 14400); // 최대 4시간
            } else {
                // 이벤트 진행 중: 짧은 TTL
                baseTtl = Math.max(baseTtl / 2, 180); // 최소 3분
            }
        }

        log.debug("🧠 [지능적 TTL] popupId={}, status={}, ttl={}초",
            response.getId(), status, baseTtl);

        return baseTtl;
    }

    /**
     * 🎯 동적 TTL로 캐시 저장
     */
    private void cacheWithIntelligentTtl(PopupDetailResponse response, int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }

        String key = buildCacheKey(response.getId());

        // remainingCapacity는 실시간성이 필요한 값이라 캐시에서 제외
        PopupDetailResponse cachedResponse = stripRemainingCapacity(response);

        try {
            String jsonValue = objectMapper.writeValueAsString(cachedResponse);
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofSeconds(ttlSeconds));
            log.debug("💾 [캐시 저장] popupId={}, ttl={}초, key={}", response.getId(), ttlSeconds, key);
        } catch (Exception e) {
            log.warn("⚠️ [캐시 저장 실패] popupId={}, key={}, error={}", response.getId(), key, e.getMessage());
        }
    }

    /**
     * 🔍 실시간 데이터 분리 (기존 로직 개선)
     */
    private PopupDetailResponse stripRemainingCapacity(PopupDetailResponse response) {
        if (response == null || response.getSchedules() == null) {
            return response;
        }

        // schedules의 remainingCapacity를 null로 비워 캐시에 저장될 값에서 제외한다.
        List<PopupScheduleListResponse.ItemDto> schedules = response.getSchedules().stream()
                .map(item -> PopupScheduleListResponse.ItemDto.builder()
                        .id(item.getId())
                        .startAt(item.getStartAt())
                        .endAt(item.getEndAt())
                        .price(item.getPrice())
                        .capacity(item.getCapacity())
                        .remainingCapacity(null)  // 🔥 실시간 데이터 제외
                        .isActive(item.getIsActive())
                        .build())
                .toList();

        return PopupDetailResponse.builder()
                .id(response.getId())
                .storeId(response.getStoreId())
                .title(response.getTitle())
                .description(response.getDescription())
                .category(response.getCategory())
                .status(response.getStatus())
                .reservationOpenAt(response.getReservationOpenAt())
                .addressRoad(response.getAddressRoad())
                .addressDetail(response.getAddressDetail())
                .eventStartAt(response.getEventStartAt())
                .eventEndAt(response.getEventEndAt())
                .schedules(schedules)
                .build();
    }

    /**
     * 🚀 실시간 데이터 보강 (remainingCapacity 추가)
     */
    public PopupDetailResponse enrichWithRealTimeData(PopupDetailResponse cachedResponse) {
        if (cachedResponse == null || cachedResponse.getSchedules() == null) {
            return cachedResponse;
        }

        try {
            // 실시간 스케줄 정보 조회 (remainingCapacity만)
            PopupDetailResponse liveResponse = popupQueryService.getPopupDetail(
                PopupDetailQuery.builder().popupId(cachedResponse.getId()).build()
            );

            if (liveResponse == null || liveResponse.getSchedules() == null) {
                return cachedResponse;
            }

            // remainingCapacity만 실시간으로 보강
            List<PopupScheduleListResponse.ItemDto> enrichedSchedules = cachedResponse.getSchedules().stream()
                .map(cachedSchedule -> {
                    // 실시간 데이터에서 동일한 ID의 스케줄 찾기
                    var liveSchedule = liveResponse.getSchedules().stream()
                        .filter(live -> live.getId().equals(cachedSchedule.getId()))
                        .findFirst();

                    return liveSchedule
                        .map(live -> PopupScheduleListResponse.ItemDto.builder()
                            .id(cachedSchedule.getId())
                            .startAt(cachedSchedule.getStartAt())
                            .endAt(cachedSchedule.getEndAt())
                            .price(cachedSchedule.getPrice())
                            .capacity(cachedSchedule.getCapacity())
                            .remainingCapacity(live.getRemainingCapacity()) // 🔥 실시간 데이터 보강
                            .isActive(cachedSchedule.getIsActive())
                            .build())
                        .orElse(cachedSchedule);
                })
                .toList();

            return PopupDetailResponse.builder()
                .id(cachedResponse.getId())
                .storeId(cachedResponse.getStoreId())
                .title(cachedResponse.getTitle())
                .description(cachedResponse.getDescription())
                .category(cachedResponse.getCategory())
                .status(cachedResponse.getStatus())
                .reservationOpenAt(cachedResponse.getReservationOpenAt())
                .addressRoad(cachedResponse.getAddressRoad())
                .addressDetail(cachedResponse.getAddressDetail())
                .eventStartAt(cachedResponse.getEventStartAt())
                .eventEndAt(cachedResponse.getEventEndAt())
                .schedules(enrichedSchedules) // 🔥 실시간 데이터 보강됨
                .build();

        } catch (Exception e) {
            log.warn("⚠️ [실시간 보강] 실패 - popupId: {}, 캐시된 데이터 반환",
                cachedResponse.getId(), e);
            return cachedResponse;
        }
    }
}
