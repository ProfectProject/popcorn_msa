package com.popcorn.store.domain.popup.service.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
import org.springframework.context.ApplicationEventPublisher;

import com.popcorn.store.domain.popup.dto.PopupResponseCode;
import com.popcorn.store.domain.popup.dto.owner.OwnerPopupResponseCode;
import com.popcorn.store.domain.popup.dto.owner.request.CreatePopupRequest;
import com.popcorn.store.domain.popup.dto.owner.request.CreatePopupScheduleRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupScheduleRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupStatusRequest;
import com.popcorn.store.domain.popup.dto.owner.response.PopupCreatedDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupStatusUpdatedDto;
import com.popcorn.store.domain.popup.entity.Popup;
import com.popcorn.store.domain.popup.entity.enums.PopupCategory;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.exception.owner.OwnerPopupException;
import com.popcorn.store.domain.popup.event.PopupCreatedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleCreatedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleDeletedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleUpdatedEvent;
import com.popcorn.store.domain.popup.event.PopupStatusUpdatedEvent;
import com.popcorn.store.domain.popup.event.PopupUpdatedEvent;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupRepository;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupScheduleRepository;
import com.popcorn.store.domain.popup.repository.owner.view.OwnerPopupScheduleView;
import com.popcorn.store.domain.popup.cache.PopupDetailCacheManager;
import com.popcorn.store.event.standard.StandardStoreEventPublisher;

class OwnerPopupServiceTest {

	@Mock
	private OwnerPopupRepository ownerPopupRepository;

	@Mock
	private OwnerPopupScheduleRepository ownerPopupScheduleRepository;

	@Mock
	private OwnerPopupValidationService validationService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private PopupDetailCacheManager popupDetailCacheManager;

	@Mock
	private StandardStoreEventPublisher standardStoreEventPublisher;

	private OwnerPopupService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new OwnerPopupService(ownerPopupRepository, ownerPopupScheduleRepository, validationService, eventPublisher,
				standardStoreEventPublisher, popupDetailCacheManager);
	}

	@Test
	@DisplayName("팝업 생성 - 정상 생성 및 이벤트 발행")
	void createPopupPublishesEvents() {
		Long ownerId = 10L;
		UUID storeId = UUID.randomUUID();
		CreatePopupScheduleRequest schedule = CreatePopupScheduleRequest.builder()
				.startAt(LocalDateTime.of(2025, 1, 1, 10, 0))
				.endAt(LocalDateTime.of(2025, 1, 1, 12, 0))
				.price(10000)
				.capacity(20)
				.build();
		CreatePopupRequest request = CreatePopupRequest.builder()
				.storeId(storeId)
				.title("테스트")
				.category(PopupCategory.FOOD)
				.schedules(List.of(schedule))
				.build();

		when(validationService.validateCreateRequest(request)).thenReturn("테스트");
		when(ownerPopupRepository.existsOwnedStore(storeId, ownerId)).thenReturn(true);
		when(ownerPopupRepository.findAllByStoreIdAndDeletedAtIsNull(storeId)).thenReturn(List.of());

		Popup savedPopup = Popup.builder()
				.storeId(storeId)
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.REQUEST)
				.createdBy(ownerId)
				.build();
		savedPopup.setId(UUID.randomUUID());
		when(ownerPopupRepository.save(any(Popup.class))).thenReturn(savedPopup);

		PopupCreatedDto response = service.createPopup(ownerId, request);

		assertThat(response.getPopupId()).isEqualTo(savedPopup.getId());
		assertThat(response.getStoreId()).isEqualTo(storeId);
		verify(ownerPopupScheduleRepository, times(1)).insertSchedule(any(), eq(savedPopup.getId()),
				eq(schedule.getStartAt()), eq(schedule.getEndAt()), eq(10000), eq(20), eq(20), eq(false),
				any(), eq(ownerId), eq(ownerId));
		verify(eventPublisher).publishEvent(any(PopupCreatedEvent.class));
		verify(eventPublisher).publishEvent(any(PopupScheduleCreatedEvent.class));
	}

	@Test
	@DisplayName("팝업 생성 - 중복 제목이면 실패")
	void createPopupRejectsDuplicateTitle() {
		Long ownerId = 10L;
		UUID storeId = UUID.randomUUID();
		CreatePopupScheduleRequest schedule = CreatePopupScheduleRequest.builder()
				.startAt(LocalDateTime.of(2025, 1, 1, 10, 0))
				.endAt(LocalDateTime.of(2025, 1, 1, 12, 0))
				.price(10000)
				.capacity(20)
				.build();
		CreatePopupRequest request = CreatePopupRequest.builder()
				.storeId(storeId)
				.title("중복")
				.category(PopupCategory.FOOD)
				.schedules(List.of(schedule))
				.build();

		when(validationService.validateCreateRequest(request)).thenReturn("중복");
		when(ownerPopupRepository.existsOwnedStore(storeId, ownerId)).thenReturn(true);
		Popup existing = Popup.builder()
				.storeId(storeId)
				.title("중복")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.REQUEST)
				.createdBy(ownerId)
				.build();
		existing.setId(UUID.randomUUID());
		when(ownerPopupRepository.findAllByStoreIdAndDeletedAtIsNull(storeId)).thenReturn(List.of(existing));

		assertThatThrownBy(() -> service.createPopup(ownerId, request))
				.isInstanceOf(PopupException.class)
				.extracting("responseCode")
				.isEqualTo(PopupResponseCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("팝업 상태 변경 - 비활성 상태면 스케줄 비활성화")
	void updatePopupStatusDeactivatesSchedules() {
		Long ownerId = 10L;
		UUID popupId = UUID.randomUUID();
		Popup popup = Popup.builder()
				.storeId(UUID.randomUUID())
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.of(popup));
		when(ownerPopupRepository.save(popup)).thenReturn(popup);
		when(ownerPopupScheduleRepository.deactivateActiveSchedulesByPopup(eq(popupId), any(), eq(ownerId)))
				.thenReturn(1);

		PopupStatusUpdatedDto response = service.updatePopupStatus(ownerId, popupId,
				UpdatePopupStatusRequest.builder().status(PopupStatus.CLOSED).build());

		assertThat(response.getStatus()).isEqualTo(PopupStatus.CLOSED);
		verify(ownerPopupScheduleRepository).deactivateActiveSchedulesByPopup(eq(popupId), any(), eq(ownerId));
		verify(eventPublisher).publishEvent(any(PopupStatusUpdatedEvent.class));
	}

	@Test
	@DisplayName("팝업 수정 - 스케줄 업데이트 실패 시 예외")
	void updatePopupFailsWhenScheduleMissing() {
		Long ownerId = 10L;
		UUID popupId = UUID.randomUUID();
		UpdatePopupScheduleRequest schedule = UpdatePopupScheduleRequest.builder()
				.scheduleId(UUID.randomUUID())
				.startAt(LocalDateTime.of(2025, 1, 1, 10, 0))
				.endAt(LocalDateTime.of(2025, 1, 1, 12, 0))
				.price(10000)
				.capacity(10)
				.active(true)
				.build();
		UpdatePopupRequest request = UpdatePopupRequest.builder()
				.updateSchedules(List.of(schedule))
				.build();

		Popup popup = Popup.builder()
				.storeId(UUID.randomUUID())
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);

		when(validationService.validateUpdateRequest(request)).thenReturn(null);
		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.of(popup));
		when(ownerPopupRepository.save(popup)).thenReturn(popup);
		when(ownerPopupScheduleRepository.updateSchedule(
				eq(schedule.getScheduleId()), eq(popupId), eq(schedule.getStartAt()), eq(schedule.getEndAt()),
				eq(schedule.getPrice()), eq(schedule.getCapacity()), eq(schedule.getActive()), any(), eq(ownerId)))
				.thenReturn(0);

		assertThatThrownBy(() -> service.updatePopup(ownerId, popupId, request))
				.isInstanceOf(OwnerPopupException.class)
				.extracting("responseCode")
				.isEqualTo(OwnerPopupResponseCode.SCHEDULE_UPDATE_NOT_FOUND);
	}

	@Test
	@DisplayName("팝업 목록 조회 - 정상 반환")
	void getPopupByStoreIdReturnsList() {
		Long ownerId = 10L;
		UUID storeId = UUID.randomUUID();
		Popup popup = Popup.builder()
				.storeId(storeId)
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(UUID.randomUUID());

		when(ownerPopupRepository.findOwnedPopupsByStoreWithPagination(storeId, ownerId, 1, 10, null))
				.thenReturn(List.of(popup));

		assertThat(service.getPopupByStoreId(ownerId, storeId)).hasSize(1);
	}

	@Test
	@DisplayName("팝업 목록 조회 - 페이지 파라미터가 유효하지 않으면 실패")
	void getPopupByStoreIdRejectsInvalidPage() {
		Long ownerId = 10L;
		UUID storeId = UUID.randomUUID();

		assertThatThrownBy(() -> service.getPopupByStoreId(ownerId, storeId, 0, 10, null))
				.isInstanceOf(PopupException.class)
				.extracting("responseCode")
				.isEqualTo(PopupResponseCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("팝업 상세 - 스케줄 매핑 포함")
	void getPopupDetailReturnsSchedules() {
		Long ownerId = 10L;
		UUID popupId = UUID.randomUUID();
		Popup popup = Popup.builder()
				.storeId(UUID.randomUUID())
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.of(popup));
		when(ownerPopupScheduleRepository.findSchedulesByPopup(popupId))
				.thenReturn(List.of(new TestOwnerScheduleView(popupId)));

		assertThat(service.getPopupDetail(ownerId, popupId).getSchedules()).hasSize(1);
	}

	@Test
	@DisplayName("팝업 수정 - 스케줄 생성/수정/삭제 성공")
	void updatePopupAppliesScheduleChanges() {
		Long ownerId = 10L;
		UUID popupId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		UUID updateScheduleId = UUID.randomUUID();
		UUID deleteScheduleId = UUID.randomUUID();
		CreatePopupScheduleRequest createSchedule = CreatePopupScheduleRequest.builder()
				.startAt(LocalDateTime.of(2025, 1, 1, 10, 0))
				.endAt(LocalDateTime.of(2025, 1, 1, 12, 0))
				.price(10000)
				.capacity(20)
				.build();
		UpdatePopupScheduleRequest updateSchedule = UpdatePopupScheduleRequest.builder()
				.scheduleId(updateScheduleId)
				.startAt(LocalDateTime.of(2025, 1, 2, 10, 0))
				.endAt(LocalDateTime.of(2025, 1, 2, 12, 0))
				.price(9000)
				.capacity(15)
				.active(true)
				.build();
		UpdatePopupRequest request = UpdatePopupRequest.builder()
				.title("  새 제목 ")
				.createSchedules(List.of(createSchedule))
				.updateSchedules(List.of(updateSchedule))
				.deleteScheduleIds(List.of(deleteScheduleId))
				.build();

		Popup popup = Popup.builder()
				.storeId(storeId)
				.title("기존")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);

		when(validationService.validateUpdateRequest(request)).thenReturn("새 제목");
		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.of(popup));
		when(ownerPopupRepository.findAllByStoreIdAndDeletedAtIsNull(storeId)).thenReturn(List.of());
		when(ownerPopupRepository.save(popup)).thenReturn(popup);
		when(ownerPopupScheduleRepository.updateSchedule(eq(updateScheduleId), eq(popupId),
				eq(updateSchedule.getStartAt()), eq(updateSchedule.getEndAt()),
				eq(updateSchedule.getPrice()), eq(updateSchedule.getCapacity()),
				eq(updateSchedule.getActive()), any(), eq(ownerId))).thenReturn(1);
		when(ownerPopupScheduleRepository.softDeleteSchedule(eq(deleteScheduleId), eq(popupId), any(), eq(ownerId)))
				.thenReturn(1);

		service.updatePopup(ownerId, popupId, request);

		verify(eventPublisher).publishEvent(any(PopupUpdatedEvent.class));
		verify(eventPublisher).publishEvent(any(PopupScheduleCreatedEvent.class));
		verify(eventPublisher).publishEvent(any(PopupScheduleUpdatedEvent.class));
		verify(eventPublisher).publishEvent(any(PopupScheduleDeletedEvent.class));
	}

	@Test
	@DisplayName("팝업 삭제 - 스케줄 삭제와 이벤트 발행")
	void deletePopupPublishesEvent() {
		Long ownerId = 10L;
		UUID popupId = UUID.randomUUID();
		Popup popup = Popup.builder()
				.storeId(UUID.randomUUID())
				.title("테스트")
				.category(PopupCategory.FOOD)
				.status(PopupStatus.OPEN)
				.createdBy(ownerId)
				.build();
		popup.setId(popupId);

		when(ownerPopupRepository.findOwnedPopup(popupId, ownerId)).thenReturn(Optional.of(popup));
		when(ownerPopupRepository.save(popup)).thenReturn(popup);
		when(ownerPopupScheduleRepository.softDeleteSchedulesByPopup(eq(popupId), any(), eq(ownerId)))
				.thenReturn(1);

		service.deletePopup(ownerId, popupId);

		verify(ownerPopupScheduleRepository).softDeleteSchedulesByPopup(eq(popupId), any(), eq(ownerId));
	}

	private static final class TestOwnerScheduleView implements OwnerPopupScheduleView {

		private final UUID scheduleId;

		private TestOwnerScheduleView(UUID popupId) {
			this.scheduleId = popupId;
		}

		@Override
		public UUID getScheduleId() {
			return scheduleId;
		}

		@Override
		public LocalDateTime getStartAt() {
			return LocalDateTime.of(2025, 1, 1, 10, 0);
		}

		@Override
		public LocalDateTime getEndAt() {
			return LocalDateTime.of(2025, 1, 1, 12, 0);
		}

		@Override
		public Integer getPrice() {
			return 10000;
		}

		@Override
		public Integer getCapacity() {
			return 20;
		}

		@Override
		public Integer getRemainingCapacity() {
			return 10;
		}

		@Override
		public boolean isActive() {
			return true;
		}
	}
}
