package com.popcorn.store.domain.popup.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.PopupScheduleListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleCapacity;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.dto.query.response.SessionPriceResponse;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.repository.PopupScheduleQueryRepository;
import com.popcorn.store.domain.popup.repository.PopupScheduleReservationRepository;
import com.popcorn.store.domain.popup.repository.view.PopupScheduleView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PopupService {

	private final PopupQueryService popupQueryService;
	private final PopupScheduleQueryService popupScheduleQueryService;
	private final PopupValidationService popupValidationService;
	private final PopupScheduleReservationRepository popupScheduleReservationRepository;
	private final PopupScheduleQueryRepository popupScheduleQueryRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final PopupDetailCacheService popupDetailCacheService;

	private static final String POPUP_SCHEDULE_REMAINING_KEY_FORMAT = "popup:schedule:%s:remaining";
	private static final boolean ENABLE_DB_REMAINING_FALLBACK = false;

	public PopupListResponse getPopups(PopupListQuery query) {
		PopupListQuery normalizedQuery = popupValidationService.normalizeListQuery(query);
		PopupListResponse response = popupQueryService.getPopups(normalizedQuery);

		return response;
	}

	public PopupDetailResponse getPopupDetail(PopupDetailQuery query) {
		popupValidationService.validateDetailQuery(query);

		PopupDetailResponse cached = popupDetailCacheService.getPopupDetailCached(query);

		return attachRemainingCapacity(query.getPopupId(), cached);
	}

	public PopupScheduleListResponse getProductSessions(PopupScheduleListQuery query) {
		PopupScheduleListQuery normalizedQuery = popupValidationService.normalizeSessionQuery(query);
		return popupScheduleQueryService.getProductSessions(normalizedQuery);
	}


	@Transactional
	public PopupScheduleCapacity cancelPopupScheduleReservation(UUID scheduleId, Integer quantity) {
		PopupScheduleCapacity capacity = popupScheduleReservationRepository.cancelCapacity(scheduleId, quantity);
		if (capacity == null) {
			throw PopupException.insufficientReservationCapacity();
		}
		return capacity;
	}

	@Transactional
	public PopupScheduleCapacity failPopupScheduleReservation(UUID scheduleId, Integer quantity) {
		PopupScheduleCapacity capacity = popupScheduleReservationRepository.failCapacity(scheduleId, quantity);
		if (capacity == null) {
			throw PopupException.insufficientReservationCapacity();
		}
		return capacity;
	}

	@Transactional
	public PopupScheduleCapacity completePopupScheduleReservation(UUID scheduleId, Integer quantity) {
		PopupScheduleCapacity capacity = popupScheduleReservationRepository.completeCapacity(scheduleId, quantity);
		if (capacity == null) {
			throw PopupException.insufficientReservationCapacity();
		}
		return capacity;
	}

	private PopupDetailResponse attachRemainingCapacity(UUID popupId, PopupDetailResponse response) {
		if (response == null || response.getSchedules() == null) {
			return response;
		}

		// Redis 카운터에서 remainingCapacity를 우선 조회한다. (bulk 조회)
		Map<UUID, Integer> remainingBySchedule = new HashMap<>();
		List<UUID> scheduleIds = response.getSchedules().stream()
				.filter(item -> item != null && item.getId() != null)
				.map(PopupScheduleListResponse.ItemDto::getId)
				.toList();
		Map<UUID, Integer> redisRemaining = getRemainingCapacityFromRedisBulk(scheduleIds);

		for (PopupScheduleListResponse.ItemDto item : response.getSchedules()) {
			if (item == null || item.getId() == null) {
				continue;
			}
			if (item.getRemainingCapacity() != null) {
				remainingBySchedule.put(item.getId(), item.getRemainingCapacity());
			}
			Integer remaining = redisRemaining.get(item.getId());
			if (remaining != null) {
				remainingBySchedule.put(item.getId(), remaining);
			}
		}

		// 모든 스케줄이 Redis 미스인 경우 DB fallback을 건너뛰어 고부하 시 DB 폭주를 방지한다.
		if (ENABLE_DB_REMAINING_FALLBACK && !remainingBySchedule.isEmpty() && remainingBySchedule.size() < scheduleIds.size()) {
			Map<UUID, Integer> fallback = loadRemainingCapacityFromDb(popupId);
			fallback.forEach(remainingBySchedule::putIfAbsent);
		}


		List<PopupScheduleListResponse.ItemDto> schedules = response.getSchedules().stream()
				.map(item -> {
					if (item == null || item.getId() == null) {
						return item;
					}
					Integer remaining = remainingBySchedule.get(item.getId());
					return PopupScheduleListResponse.ItemDto.builder()
							.id(item.getId())
							.startAt(item.getStartAt())
							.endAt(item.getEndAt())
							.price(item.getPrice())
							.capacity(item.getCapacity())
							.remainingCapacity(remaining)
							.isActive(item.getIsActive())
							.build();
				})
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

	private Map<UUID, Integer> getRemainingCapacityFromRedisBulk(List<UUID> scheduleIds) {
		Map<UUID, Integer> remainingBySchedule = new HashMap<>();
		if (scheduleIds == null || scheduleIds.isEmpty()) {
			return remainingBySchedule;
		}

		List<String> keys = scheduleIds.stream()
				.map(id -> String.format(POPUP_SCHEDULE_REMAINING_KEY_FORMAT, id))
				.toList();

		try {
			List<Object> values = redisTemplate.opsForValue().multiGet(keys);
			if (values == null || values.isEmpty()) {
				return remainingBySchedule;
			}

			for (int i = 0; i < scheduleIds.size() && i < values.size(); i++) {
				Object value = values.get(i);
				Integer parsed = parseRemainingCapacity(value);
				if (parsed != null) {
					remainingBySchedule.put(scheduleIds.get(i), parsed);
				}
			}
		} catch (Exception e) {
			log.warn("⚠️ Redis remainingCapacity bulk 조회 실패 - popup 상세는 DB값으로 계속 응답합니다: {}", e.getMessage());
		}
		return remainingBySchedule;
	}

	private Integer getRemainingCapacityFromRedis(UUID scheduleId) {
		try {
			Object value = redisTemplate.opsForValue().get(String.format(POPUP_SCHEDULE_REMAINING_KEY_FORMAT, scheduleId));
			return parseRemainingCapacity(value);
		} catch (Exception e) {
			log.warn("⚠️ Redis remainingCapacity 단건 조회 실패 - scheduleId={}: {}", scheduleId, e.getMessage());
			return null;
		}
	}

	private Integer parseRemainingCapacity(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String stringValue) {
			try {
				return Integer.parseInt(stringValue);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private Map<UUID, Integer> loadRemainingCapacityFromDb(UUID popupId) {
		Map<UUID, Integer> remainingBySchedule = new HashMap<>();
		if (popupId == null) {
			return remainingBySchedule;
		}

		List<PopupScheduleView> views = popupScheduleQueryRepository.findProductSessions(popupId, null, null);
		for (PopupScheduleView view : views) {
			if (view == null || view.getScheduleId() == null) {
				continue;
			}
			try {
				UUID scheduleId = UUID.fromString(view.getScheduleId());
				remainingBySchedule.put(scheduleId, view.getRemainingCapacity());
			} catch (IllegalArgumentException ignored) {
				// skip invalid UUID
			}
		}
		return remainingBySchedule;
	}

	/**
	 * 세션 가격 조회
	 * Order 서비스의 가격 조회 요청을 처리합니다.
	 */
	@Transactional(readOnly = true)
	public SessionPriceResponse getSessionPrice(UUID sessionId) {
		// ⚡ 최적화: 세션 정보 조회
		PopupScheduleView schedule = popupScheduleQueryRepository.findByScheduleId(sessionId);
		if (schedule == null) {
			throw PopupException.popupNotFound();
		}

		// ⚡ 최적화: 가격 조회용으로 간소화된 응답 (팝업 상세 조회 제거)
		// 팝업 제목은 캐시 미스 시 성능 저하를 일으킬 수 있으므로 기본값 사용
		String sessionName = "팝업 세션"; // 기본값으로 성능 최적화

		// 캐시에서 팝업 정보 조회 시도 (캐시 히트 시에만 제목 사용)
		try {
			PopupDetailQuery popupQuery = PopupDetailQuery.of(schedule.getPopupId());
			PopupDetailResponse popupDetail = popupDetailCacheService.getPopupDetailCached(popupQuery);
			if (popupDetail != null && popupDetail.getTitle() != null) {
				sessionName = popupDetail.getTitle() + " - 세션";
			}
		} catch (Exception e) {
			// 캐시 조회 실패 시 기본값 유지 (성능 우선)
			log.debug("⚠️ 팝업 상세 정보 조회 실패, 기본 세션명 사용: {}", e.getMessage());
		}

		// 사용 가능한 좌석 계산
		Integer availableSeats = schedule.getRemainingCapacity();
		Integer totalSeats = schedule.getCapacity();

		// 응답 생성
		return SessionPriceResponse.builder()
				.sessionId(sessionId)
				.popupId(schedule.getPopupId())
				.sessionName(sessionName)
				.price(schedule.getPrice())
				.originalPrice(schedule.getPrice()) // 할인 기능이 없으므로 동일
				.discountRate(0) // 기본 할인율 0%
				.availableSeats(availableSeats)
				.totalSeats(totalSeats)
				.status(calculateSessionStatus(availableSeats, schedule.getStartAt()))
				.sessionStartTime(schedule.getStartAt())
				.sessionEndTime(schedule.getEndAt())
				.currency("KRW")
				.build();
	}

	/**
	 * 세션 상태 계산 헬퍼 메서드
	 */
	private String calculateSessionStatus(Integer availableSeats, java.time.LocalDateTime startAt) {
		if (availableSeats == null || availableSeats <= 0) {
			return "SOLD_OUT";
		}
		if (startAt != null && java.time.LocalDateTime.now().isAfter(startAt)) {
			return "EXPIRED";
		}
		return "AVAILABLE";
	}

}
