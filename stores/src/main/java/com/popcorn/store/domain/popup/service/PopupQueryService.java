package com.popcorn.store.domain.popup.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;
import com.popcorn.store.domain.popup.repository.PopupQueryRepository;
import com.popcorn.store.domain.popup.repository.PopupScheduleQueryRepository;
import com.popcorn.store.domain.popup.repository.view.PopupListView;
import com.popcorn.store.domain.popup.repository.view.PopupScheduleView;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PopupQueryService {

	private final PopupQueryRepository popupQueryRepository;
	private final PopupScheduleQueryRepository popupScheduleQueryRepository;
	private final PopupListCacheService popupListCacheService;

	private static final int DEFAULT_PAGE = 1;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public PopupListResponse getPopups(PopupListQuery query) {
		Long regionId = query.getRegionId();
		PopupCategory category = query.getCategory();
		String keyword = query.getKeyword();
		UUID storeId = query.getStoreId();
		Integer page = query.getPage();
		Integer size = query.getSize();
		boolean withTotal = query.getWithTotal() == null || query.getWithTotal();

		int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
		int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		long offset = (long) (normalizedPage - 1) * normalizedSize;

		String categoryValue = category == null ? null : category.name();

		// ⚡ 캐시 우선 조회 (인기 목록 또는 일반 캐시)
		String cacheKey = popupListCacheService.generateCacheKey(
				regionId, categoryValue, keyword, storeId, page, size);

		// 필터링이 없는 첫 페이지는 인기 목록 캐시 사용
		if (isPopularListQuery(regionId, categoryValue, keyword, storeId, normalizedPage)) {
			PopupListResponse cachedPopular = popupListCacheService.getPopularPopupList();
			if (cachedPopular != null) {
				return cachedPopular;
			}
		}

		// 일반 캐시 조회
		PopupListResponse cached = popupListCacheService.getCachedPopupList(cacheKey);
		if (cached != null) {
			return cached;
		}

		// ⚡ 캐시 미스 시 데이터베이스 조회
		long total = withTotal
				? popupQueryRepository.countPopups(regionId, categoryValue, keyword, storeId)
				: -1L;
		List<PopupListView> views = popupQueryRepository.findPopups(
				regionId, categoryValue, keyword, storeId, normalizedSize, offset);

		List<PopupListResponse.ItemDto> items = views.stream()
				.map(view -> PopupListResponse.ItemDto.builder()
						.id(toUuid(view.getId()))
						.storeId(toUuid(view.getStoreId()))
						.title(view.getTitle())
						.category(toCategory(view.getCategory()))
						.status(toStatus(view.getStatus()))
						.reservationOpenAt(view.getReservationOpenAt())
						.addressRoad(view.getAddressRoad())
						.addressDetail(view.getAddressDetail())
						.eventStartAt(view.getEventStartAt())
						.eventEndAt(view.getEventEndAt())
						.build())
				.toList();

		PopupListResponse response = PopupListResponse.builder()
				.items(items)
				.page(normalizedPage)
				.size(normalizedSize)
				.total(total)
				.build();

		// ⚡ 결과를 캐시에 저장
		if (isPopularListQuery(regionId, categoryValue, keyword, storeId, normalizedPage)) {
			popupListCacheService.cachePopularPopupList(response);
		} else {
			popupListCacheService.cachePopupList(cacheKey, response);
		}

		return response;
	}

	/**
	 * 인기 목록 쿼리인지 판단 (필터링이 없는 첫 페이지)
	 */
	private boolean isPopularListQuery(Long regionId, String category, String keyword,
										UUID storeId, int page) {
		return regionId == null && category == null && keyword == null &&
			   storeId == null && page == 1;
	}

	public PopupDetailResponse getPopupDetail(PopupDetailQuery query) {
		PopupListView view = popupQueryRepository.findPopupDetail(query.getPopupId())
				.orElseThrow(com.popcorn.store.domain.popup.exception.PopupException::popupNotFound);

		return PopupDetailResponse.builder()
				.id(toUuid(view.getId()))
				.storeId(toUuid(view.getStoreId()))
				.title(view.getTitle())
				.description(view.getDescription())
				.category(toCategory(view.getCategory()))
				.status(toStatus(view.getStatus()))
				.reservationOpenAt(view.getReservationOpenAt())
				.addressRoad(view.getAddressRoad())
				.addressDetail(view.getAddressDetail())
				.eventStartAt(view.getEventStartAt())
				.eventEndAt(view.getEventEndAt())
				.schedules(fetchSchedules(query.getPopupId()))
				.build();
	}

	private UUID toUuid(String value) {
		return value == null ? null : UUID.fromString(value);
	}

	private PopupCategory toCategory(String value) {
		return value == null ? null : PopupCategory.valueOf(value);
	}

	private PopupStatus toStatus(String value) {
		return value == null ? null : PopupStatus.valueOf(value);
	}

	private List<PopupScheduleListResponse.ItemDto> fetchSchedules(UUID popupId) {
		List<PopupScheduleView> views = popupScheduleQueryRepository.findProductSessions(popupId, null, null);
		return views.stream()
				.map(view -> {
					UUID scheduleId = toUuid(view.getScheduleId());
					if (scheduleId == null) {
						return null;
					}
					return PopupScheduleListResponse.ItemDto.builder()
							.id(scheduleId)
							.startAt(view.getStartAt())
							.endAt(view.getEndAt())
							.price(view.getPrice())
							.capacity(view.getCapacity())
							.remainingCapacity(view.getRemainingCapacity())
							.isActive(view.getIsActive())
							.build();
				})
				.filter(Objects::nonNull)
				.toList();
	}
}
