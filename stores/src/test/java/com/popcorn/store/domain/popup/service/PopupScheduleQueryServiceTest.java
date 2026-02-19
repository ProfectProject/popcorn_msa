package com.popcorn.store.domain.popup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.popcorn.store.domain.popup.dto.query.PopupScheduleListQuery;
import com.popcorn.store.domain.popup.dto.query.response.PopupScheduleListResponse;
import com.popcorn.store.domain.popup.repository.PopupQueryRepository;
import com.popcorn.store.domain.popup.repository.PopupScheduleQueryRepository;
import com.popcorn.store.domain.popup.repository.view.PopupListView;
import com.popcorn.store.domain.popup.repository.view.PopupScheduleView;

class PopupScheduleQueryServiceTest {

	@Test
	@DisplayName("회차 조회 - 필드 매핑")
	void getProductSessions_mapsFields() {
		PopupScheduleQueryRepository scheduleRepository = Mockito.mock(PopupScheduleQueryRepository.class);
		PopupQueryRepository popupRepository = Mockito.mock(PopupQueryRepository.class);
		PopupScheduleQueryService service = new PopupScheduleQueryService(scheduleRepository, popupRepository);

		PopupScheduleView view = new TestScheduleView(
				UUID.fromString("00000000-0000-0000-0000-000000000101"),
				"00000000-0000-0000-0000-000000000201",
				LocalDateTime.of(2025, 1, 1, 10, 0),
				LocalDateTime.of(2025, 1, 5, 18, 0),
				12000,
				50,
				50,
				0,
				true
		);

		when(popupRepository.findPopupDetail(eq(UUID.fromString("00000000-0000-0000-0000-000000000101"))))
				.thenReturn(Optional.of(Mockito.mock(PopupListView.class)));

		when(scheduleRepository.findProductSessions(
				eq(UUID.fromString("00000000-0000-0000-0000-000000000101")),
				eq(LocalDateTime.of(2025, 1, 1, 0, 0)),
				eq(LocalDateTime.of(2025, 1, 31, 23, 59))))
				.thenReturn(List.of(view));

		PopupScheduleListResponse response = service.getProductSessions(PopupScheduleListQuery.builder()
				.popupId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
				.from(LocalDateTime.of(2025, 1, 1, 0, 0))
				.to(LocalDateTime.of(2025, 1, 31, 23, 59))
				.build());

		assertEquals(1, response.getItems().size());
		assertEquals(12000, response.getItems().get(0).getPrice());
		assertEquals(50, response.getItems().get(0).getCapacity());
		assertEquals(50, response.getItems().get(0).getRemainingCapacity());
		assertEquals(true, response.getItems().get(0).getIsActive());
	}

	@Test
	@DisplayName("회차 조회 - 비활성 정보 매핑")
	void getProductSessions_mapsInactive() {
		PopupScheduleQueryRepository scheduleRepository = Mockito.mock(PopupScheduleQueryRepository.class);
		PopupQueryRepository popupRepository = Mockito.mock(PopupQueryRepository.class);
		PopupScheduleQueryService service = new PopupScheduleQueryService(scheduleRepository, popupRepository);

		PopupScheduleView view = new TestScheduleView(
				UUID.fromString("00000000-0000-0000-0000-000000000101"),
				"00000000-0000-0000-0000-000000000202",
				LocalDateTime.of(2025, 1, 10, 10, 0),
				LocalDateTime.of(2025, 1, 10, 18, 0),
				8000,
				30,
				0,
				0,
				false
		);

		when(popupRepository.findPopupDetail(eq(UUID.fromString("00000000-0000-0000-0000-000000000101"))))
				.thenReturn(Optional.of(Mockito.mock(PopupListView.class)));

		when(scheduleRepository.findProductSessions(
				eq(UUID.fromString("00000000-0000-0000-0000-000000000101")),
				eq(null),
				eq(null)))
				.thenReturn(List.of(view));

		PopupScheduleListResponse response = service.getProductSessions(PopupScheduleListQuery.builder()
				.popupId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
				.build());

		assertEquals(1, response.getItems().size());
		assertEquals(false, response.getItems().get(0).getIsActive());
	}

	private record TestScheduleView(UUID popupId, String scheduleId, LocalDateTime startAt, LocalDateTime endAt, Integer price,
									Integer capacity, Integer remainingCapacity, Integer reservationCapacity,
									Boolean isActive) implements PopupScheduleView {

		@Override
		public String getScheduleId() { return scheduleId; }
		@Override
		public UUID getPopupId() { return popupId; }

		@Override
		public LocalDateTime getStartAt() { return startAt; }

		@Override
		public LocalDateTime getEndAt() { return endAt; }

		@Override
		public Integer getPrice() { return price; }

		@Override
		public Integer getCapacity() { return capacity; }

		@Override
		public Integer getRemainingCapacity() { return remainingCapacity; }

		// reservationCapacity는 인터페이스에 없는 메서드이므로 제거

		@Override
		public Boolean getIsActive() { return isActive; }
	}
}
