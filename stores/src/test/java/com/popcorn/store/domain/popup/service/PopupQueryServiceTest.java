package com.popcorn.store.domain.popup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.popcorn.store.domain.popup.dto.PopupResponseCode;
import com.popcorn.store.domain.popup.dto.query.PopupDetailQuery;
import com.popcorn.store.domain.popup.dto.query.PopupListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupDetailResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupListResponse;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.repository.PopupQueryRepository;
import com.popcorn.store.domain.popup.repository.PopupScheduleQueryRepository;
import com.popcorn.store.domain.popup.repository.view.PopupListView;
import com.popcorn.store.domain.popup.repository.view.PopupScheduleView;

class PopupQueryServiceTest {

	@Mock
	private PopupQueryRepository popupQueryRepository;

	@Mock
	private PopupScheduleQueryRepository popupScheduleQueryRepository;

	private PopupQueryService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new PopupQueryService(popupQueryRepository, popupScheduleQueryRepository);
	}

	@Test
	@DisplayName("팝업 목록 - 기본 페이지/사이즈 적용 및 매핑")
	void getPopupsAppliesDefaults() {
		UUID storeId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		PopupListQuery query = PopupListQuery.builder()
				.storeId(storeId)
				.build();

		PopupListView view = new TestPopupListView(
				"00000000-0000-0000-0000-000000000010",
				"00000000-0000-0000-0000-000000000001",
				"테스트 팝업",
				"설명",
				"FOOD",
				"OPEN",
				LocalDateTime.of(2025, 1, 1, 10, 0),
				"서울",
				"상세",
				LocalDateTime.of(2025, 1, 2, 10, 0),
				LocalDateTime.of(2025, 1, 3, 10, 0)
		);

		when(popupQueryRepository.countPopups(eq(null), eq(null), eq(null), eq(storeId)))
				.thenReturn(5L);
		when(popupQueryRepository.findPopups(eq(null), eq(null), eq(null), eq(storeId), eq(20), eq(0L)))
				.thenReturn(List.of(view));

		PopupListResponse response = service.getPopups(query);

		assertThat(response.getPage()).isEqualTo(1);
		assertThat(response.getSize()).isEqualTo(20);
		assertThat(response.getTotal()).isEqualTo(5);
		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getItems().get(0).getCategory()).isEqualTo(PopupCategory.FOOD);
		assertThat(response.getItems().get(0).getStatus()).isEqualTo(PopupStatus.OPEN);
	}

	@Test
	@DisplayName("팝업 목록 - total 미요청이면 count 생략")
	void getPopupsSkipsCountWhenWithTotalFalse() {
		PopupListQuery query = PopupListQuery.builder()
				.page(2)
				.size(10)
				.withTotal(false)
				.build();

		when(popupQueryRepository.findPopups(eq(null), eq(null), eq(null), eq(null), eq(10), eq(10L)))
				.thenReturn(List.of());

		PopupListResponse response = service.getPopups(query);

		assertThat(response.getTotal()).isEqualTo(-1L);
		verify(popupQueryRepository, never()).countPopups(any(), any(), any(), any());
	}

	@Test
	@DisplayName("팝업 상세 - 존재하지 않으면 예외")
	void getPopupDetailThrowsWhenMissing() {
		UUID popupId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		when(popupQueryRepository.findPopupDetail(popupId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPopupDetail(PopupDetailQuery.of(popupId)))
				.isInstanceOf(PopupException.class)
				.extracting("responseCode")
				.isEqualTo(PopupResponseCode.POPUP_NOT_FOUND);
	}

	@Test
	@DisplayName("팝업 상세 - 스케줄 null id는 제외")
	void getPopupDetailFiltersNullSchedule() {
		UUID popupId = UUID.fromString("00000000-0000-0000-0000-000000000100");
		PopupListView popupView = new TestPopupListView(
				popupId.toString(),
				"00000000-0000-0000-0000-000000000001",
				"테스트",
				"설명",
				"FOOD",
				"OPEN",
				LocalDateTime.of(2025, 1, 1, 10, 0),
				"서울",
				"상세",
				LocalDateTime.of(2025, 1, 2, 10, 0),
				LocalDateTime.of(2025, 1, 3, 10, 0)
		);
		when(popupQueryRepository.findPopupDetail(popupId)).thenReturn(Optional.of(popupView));

		PopupScheduleView valid = new TestPopupScheduleView(
				popupId,
				"00000000-0000-0000-0000-000000000201",
				LocalDateTime.of(2025, 1, 2, 10, 0),
				LocalDateTime.of(2025, 1, 2, 12, 0),
				10000,
				20,
				15,
				true
		);
		PopupScheduleView invalid = new TestPopupScheduleView(
				popupId,
				null,
				LocalDateTime.of(2025, 1, 3, 10, 0),
				LocalDateTime.of(2025, 1, 3, 12, 0),
				10000,
				20,
				15,
				true
		);
		when(popupScheduleQueryRepository.findProductSessions(popupId, null, null))
				.thenReturn(List.of(valid, invalid));

		PopupDetailResponse response = service.getPopupDetail(PopupDetailQuery.of(popupId));
		List<PopupScheduleListResponse.ItemDto> schedules = response.getSchedules();

		assertThat(schedules).hasSize(1);
		assertThat(schedules.get(0).getId())
				.isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000201"));
	}

	private record TestPopupListView(
			String id,
			String storeId,
			String title,
			String description,
			String category,
			String status,
			LocalDateTime reservationOpenAt,
			String addressRoad,
			String addressDetail,
			LocalDateTime eventStartAt,
			LocalDateTime eventEndAt) implements PopupListView {
		@Override public String getId() { return id; }
		@Override public String getStoreId() { return storeId; }
		@Override public String getTitle() { return title; }
		@Override public String getDescription() { return description; }
		@Override public String getCategory() { return category; }
		@Override public String getStatus() { return status; }
		@Override public LocalDateTime getReservationOpenAt() { return reservationOpenAt; }
		@Override public String getAddressRoad() { return addressRoad; }
		@Override public String getAddressDetail() { return addressDetail; }
		@Override public LocalDateTime getEventStartAt() { return eventStartAt; }
		@Override public LocalDateTime getEventEndAt() { return eventEndAt; }
	}

	private record TestPopupScheduleView(
			UUID popupId,
			String scheduleId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			Integer price,
			Integer capacity,
			Integer remainingCapacity,
			Boolean isActive) implements PopupScheduleView {
		@Override public String getScheduleId() { return scheduleId; }
		@Override public UUID getPopupId() { return popupId; }
		@Override public LocalDateTime getStartAt() { return startAt; }
		@Override public LocalDateTime getEndAt() { return endAt; }
		@Override public Integer getPrice() { return price; }
		@Override public Integer getCapacity() { return capacity; }
		@Override public Integer getRemainingCapacity() { return remainingCapacity; }
		@Override public Integer getReservationCapacity() { return 0; }
		@Override public Boolean getIsActive() { return isActive; }
	}
}
