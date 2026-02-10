package com.popcorn.store.domain.popup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;

import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.PopupScheduleListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.repository.PopupScheduleQueryRepository;
import com.popcorn.store.domain.popup.repository.PopupScheduleReservationRepository;

class PopupServiceTest {

	@Test
	@DisplayName("팝업 목록 조회 - 검증/정규화 후 조회 서비스 호출")
	void getPopups_callsQueryService() {
		PopupQueryService queryService = Mockito.mock(PopupQueryService.class);
		PopupScheduleQueryService scheduleQueryService = Mockito.mock(PopupScheduleQueryService.class);
		PopupValidationService validationService = Mockito.mock(PopupValidationService.class);
		PopupScheduleReservationRepository reservationRepository = Mockito.mock(PopupScheduleReservationRepository.class);
		PopupScheduleQueryRepository scheduleQueryRepository = Mockito.mock(PopupScheduleQueryRepository.class);
		RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
		PopupDetailCacheService popupDetailCacheService = Mockito.mock(PopupDetailCacheService.class);
		PopupService service = new PopupService(
				queryService,
				scheduleQueryService,
				validationService,
				reservationRepository,
				scheduleQueryRepository,
				redisTemplate,
				popupDetailCacheService);

		PopupListQuery request = PopupListQuery.builder()
				.category(PopupCategory.FOOD)
				.page(1)
				.size(20)
				.build();
		PopupListQuery normalized = PopupListQuery.builder()
				.category(PopupCategory.FOOD)
				.page(1)
				.size(20)
				.build();
		PopupListResponse response = PopupListResponse.builder()
				.page(1)
				.size(20)
				.total(3)
				.items(java.util.List.of())
				.build();

		when(validationService.normalizeListQuery(request)).thenReturn(normalized);
		when(queryService.getPopups(normalized)).thenReturn(response);

		PopupListResponse result = service.getPopups(request);

		assertEquals(3, result.getTotal());
		verify(queryService).getPopups(normalized);
	}

	@Test
	@DisplayName("팝업 상세 조회 - 조회 서비스 호출")
	void getPopupDetail_callsQueryService() {
		PopupQueryService queryService = Mockito.mock(PopupQueryService.class);
		PopupScheduleQueryService scheduleQueryService = Mockito.mock(PopupScheduleQueryService.class);
		PopupValidationService validationService = Mockito.mock(PopupValidationService.class);
		PopupScheduleReservationRepository reservationRepository = Mockito.mock(PopupScheduleReservationRepository.class);
		PopupScheduleQueryRepository scheduleQueryRepository = Mockito.mock(PopupScheduleQueryRepository.class);
		RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
		PopupDetailCacheService popupDetailCacheService = Mockito.mock(PopupDetailCacheService.class);
		PopupService service = new PopupService(
				queryService,
				scheduleQueryService,
				validationService,
				reservationRepository,
				scheduleQueryRepository,
				redisTemplate,
				popupDetailCacheService);

		UUID popupId = UUID.fromString("00000000-0000-0000-0000-000000000101");
		PopupDetailQuery query = PopupDetailQuery.of(popupId);
		PopupDetailResponse response = PopupDetailResponse.builder()
				.id(popupId)
				.storeId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
				.category(PopupCategory.FOOD)
				.schedules(java.util.List.of())
				.build();

		when(popupDetailCacheService.getPopupDetailCached(query)).thenReturn(response);

		PopupDetailResponse result = service.getPopupDetail(query);

		assertEquals(popupId, result.getId());
		verify(popupDetailCacheService).getPopupDetailCached(query);
	}

	@Test
	@DisplayName("회차 조회 - 검증 후 조회 서비스 호출")
	void getProductSessions_validatesQuery() {
		PopupQueryService queryService = Mockito.mock(PopupQueryService.class);
		PopupScheduleQueryService scheduleQueryService = Mockito.mock(PopupScheduleQueryService.class);
		PopupValidationService validationService = Mockito.mock(PopupValidationService.class);
		PopupScheduleReservationRepository reservationRepository = Mockito.mock(PopupScheduleReservationRepository.class);
		PopupScheduleQueryRepository scheduleQueryRepository = Mockito.mock(PopupScheduleQueryRepository.class);
		RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
		PopupDetailCacheService popupDetailCacheService = Mockito.mock(PopupDetailCacheService.class);
		PopupService service = new PopupService(
				queryService,
				scheduleQueryService,
				validationService,
				reservationRepository,
				scheduleQueryRepository,
				redisTemplate,
				popupDetailCacheService);

		PopupScheduleListQuery query = PopupScheduleListQuery.builder()
				.popupId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
				.build();
		PopupScheduleListResponse response = PopupScheduleListResponse.builder()
				.items(java.util.List.of())
				.build();

		when(validationService.normalizeSessionQuery(query)).thenReturn(query);
		when(scheduleQueryService.getProductSessions(query)).thenReturn(response);

		PopupScheduleListResponse result = service.getProductSessions(query);

		assertEquals(0, result.getItems().size());
		verify(validationService).normalizeSessionQuery(query);
		verify(scheduleQueryService).getProductSessions(query);
	}
}
