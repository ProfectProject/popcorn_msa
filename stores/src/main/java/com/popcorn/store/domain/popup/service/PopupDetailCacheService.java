package com.popcorn.store.domain.popup.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.popcorn.common.annotation.RedisCacheResult;
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

    private final PopupQueryService popupQueryService;

    /**
     * 📊 지능적 TTL을 가진 팝업 상세 캐시
     */
    public PopupDetailResponse getPopupDetailCached(PopupDetailQuery query) {
        UUID popupId = query.getPopupId();

        // 1. DB에서 조회
        PopupDetailResponse response = popupQueryService.getPopupDetail(query);
        if (response == null) {
            return null;
        }

        // 2. 지능적 TTL 계산
        int intelligentTtl = calculateIntelligentTtl(response);

        // 3. 수동으로 캐시 저장 (동적 TTL 적용)
        return cacheWithIntelligentTtl(response, intelligentTtl);
    }

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
    @RedisCacheResult(
        cacheName = "popup",
        keyExpression = "#response.id + ':detail:v2'",
        condition = "#ttlSeconds > 0"
    )
    private PopupDetailResponse cacheWithIntelligentTtl(PopupDetailResponse response, int ttlSeconds) {
        // remainingCapacity는 실시간성이 필요한 값이라 캐시에서 제외
        PopupDetailResponse cachedResponse = stripRemainingCapacity(response);

        log.debug("💾 [캐시 저장] popupId={}, ttl={}초", response.getId(), ttlSeconds);

        return cachedResponse;
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
