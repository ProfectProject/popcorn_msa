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

@Service
@RequiredArgsConstructor
public class PopupService {

	private final PopupQueryService popupQueryService;
	private final PopupScheduleQueryService popupScheduleQueryService;
	private final PopupValidationService popupValidationService;
	private final PopupScheduleReservationRepository popupScheduleReservationRepository;
	private final PopupScheduleQueryRepository popupScheduleQueryRepository;
	private final RedisTemplate<String, Object> redisTemplate;
	private final PopupDetailCacheService popupDetailCacheService;

	private static final String POPUP_SCHEDULE_REMAINING_KEY_FORMAT = "popup:schedule:%s:remaining";

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

		// Redis 카운터에서 remainingCapacity를 우선 조회한다.
		Map<UUID, Integer> remainingBySchedule = new HashMap<>();
		for (PopupScheduleListResponse.ItemDto item : response.getSchedules()) {
			if (item == null || item.getId() == null) {
				continue;
			}
			Integer remaining = getRemainingCapacityFromRedis(item.getId());
			if (remaining != null) {
				remainingBySchedule.put(item.getId(), remaining);
			}
		}


		if (remainingBySchedule.size() != response.getSchedules().size()) {
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

	private Integer getRemainingCapacityFromRedis(UUID scheduleId) {

		String key = String.format(POPUP_SCHEDULE_REMAINING_KEY_FORMAT, scheduleId);
		Object value = redisTemplate.opsForValue().get(key);
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
		// 세션 정보 조회
		PopupScheduleView schedule = popupScheduleQueryRepository.findByScheduleId(sessionId);
		if (schedule == null) {
			throw PopupException.popupNotFound();
		}

		// 팝업 상세 정보 조회 (제목 등 추가 정보)
		PopupDetailQuery popupQuery = PopupDetailQuery.of(schedule.getPopupId());
		PopupDetailResponse popupDetail = popupDetailCacheService.getPopupDetailCached(popupQuery);

		// 사용 가능한 좌석 계산
		Integer availableSeats = schedule.getRemainingCapacity();
		Integer totalSeats = schedule.getCapacity();

		// 응답 생성
		return SessionPriceResponse.builder()
				.sessionId(sessionId)
				.popupId(schedule.getPopupId())
				.sessionName(popupDetail.getTitle() + " - 세션")
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
