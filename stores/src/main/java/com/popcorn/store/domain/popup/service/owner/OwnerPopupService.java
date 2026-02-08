package com.popcorn.store.domain.popup.service.owner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.popcorn.store.domain.popup.entity.OutboxEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.popcorn.store.domain.popup.dto.PopupResponseCode;
import com.popcorn.store.domain.popup.dto.owner.request.CreatePopupRequest;
import com.popcorn.store.domain.popup.dto.owner.request.CreatePopupScheduleRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupScheduleRequest;
import com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupStatusRequest;
import com.popcorn.store.domain.popup.dto.owner.response.PopupDetailDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupCreatedDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupDeletedDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupListDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupScheduleDetailDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupStatusUpdatedDto;
import com.popcorn.store.domain.popup.dto.owner.response.PopupUpdatedDto;
import com.popcorn.store.domain.popup.entity.Popup;
import com.popcorn.store.domain.popup.entity.enums.PopupStatus;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.exception.owner.OwnerPopupException;
import com.popcorn.store.domain.popup.event.PopupCreatedEvent;
import com.popcorn.store.domain.popup.event.PopupDeletedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleCreatedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleDeletedEvent;
import com.popcorn.store.domain.popup.event.PopupScheduleUpdatedEvent;
import com.popcorn.store.domain.popup.event.PopupStatusUpdatedEvent;
import com.popcorn.store.domain.popup.event.PopupUpdatedEvent;
import com.popcorn.store.domain.popup.cache.PopupDetailCacheManager;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupRepository;
import com.popcorn.store.domain.popup.repository.owner.OwnerPopupScheduleRepository;
import com.popcorn.store.domain.popup.repository.owner.outbox.OutboxEventRepository;
import com.popcorn.store.domain.popup.repository.owner.view.OwnerPopupScheduleView;
import com.popcorn.store.domain.store.exception.StoreException;
import com.popcorn.store.event.standard.StandardPopupCreatedEvent;
import com.popcorn.store.event.standard.StandardPopupStatusUpdatedEvent;
import com.popcorn.store.event.standard.StandardEventType;
import com.popcorn.store.constants.EventConstants;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerPopupService {

    private final OwnerPopupRepository ownerPopupRepository;
    private final OwnerPopupScheduleRepository ownerPopupScheduleRepository;
    private final OwnerPopupValidationService validationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PopupDetailCacheManager popupDetailCacheManager;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public PopupCreatedDto createPopup(Long userId, CreatePopupRequest request) {
        log.info("[POPUP_CREATE] ownerId={}, storeId={}, title={}", userId, request.getStoreId(), request.getTitle());

        validateOwner(userId);
        String trimmedTitle = validationService.validateCreateRequest(request);

        if (!ownerPopupRepository.existsOwnedStore(request.getStoreId(), userId)) {
            throw StoreException.storeNotFound(request.getStoreId());
        }

        validateDuplicateTitle(request.getStoreId(), trimmedTitle);

        Popup savedPopup = ownerPopupRepository.save(createPopupEntity(userId, request, trimmedTitle));
        createPopupSchedules(savedPopup.getId(), request.getSchedules(), userId);

        queuePopupCreatedOutboxEntry(savedPopup);

        log.info("[POPUP_CREATED] popupId={}, storeId={}", savedPopup.getId(), savedPopup.getStoreId());
        return mapToDto(savedPopup);

    }

    private void queuePopupCreatedOutboxEntry(Popup popup) {
        try {
            StandardPopupCreatedEvent event = StandardPopupCreatedEvent.builder()
                    .topic("store-events")
                    .eventType(StandardEventType.POPUP_CREATED)
                    .producer("store-service")
                    .storeId(popup.getStoreId())
                    .popupId(popup.getId())
                    .title(popup.getTitle())
                    .status(popup.getStatus() != null ? popup.getStatus().name() : null)
                    .reservationOpenAt(popup.getReservationOpenAt())
                    .addressRoad(popup.getAddressRoad())
                    .addressDetail(popup.getAddressDetail())
                    .createdAt(popup.getCreatedAt())
                    .build();

            event.setDefaults();
            persistPopupCreatedOutboxEntry(popup, event);

            log.info("📦 [OUTBOX] 팝업 생성 표준 이벤트 저장 완료 - popupId: {}, storeId: {}",
                    popup.getId(), popup.getStoreId());
        } catch (Exception e) {
            log.error("❌ [OUTBOX] 팝업 생성 표준 이벤트 저장 실패 - popupId: {}, error: {}",
                    popup.getId(), e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<PopupListDto> getPopupByStoreId(Long ownerId, UUID storeId, int page, int size, String category) {
        log.info("[POPUP_LIST] ownerId={}, storeId={}, page={}, size={}, category={}", ownerId, storeId, page, size, category);

        validateOwner(ownerId);
        validateStoreId(storeId);
        validatePaginationParams(page, size);

        List<PopupListDto> result = ownerPopupRepository.findOwnedPopupsByStoreWithPagination(storeId, ownerId, page, size, category).stream()
                .map(this::mapToListDto)
                .toList();

        log.info("[POPUP_LIST_FOUND] storeId={}, page={}, size={}, category={}, count={}", storeId, page, size, category, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<PopupListDto> getPopupByStoreId(Long ownerId, UUID storeId) {
        return getPopupByStoreId(ownerId, storeId, 1, 10, null);
    }

    @Transactional(readOnly = true)
    public PopupDetailDto getPopupDetail(Long ownerId, UUID popupId) {
        log.info("[POPUP_DETAIL] ownerId={}, popupId={}", ownerId, popupId);

        validateOwner(ownerId);
        validatePopupId(popupId);

        Popup popup = ownerPopupRepository.findOwnedPopup(popupId, ownerId)
                .orElseThrow(PopupException::popupNotFound);

        List<PopupScheduleDetailDto> schedules = ownerPopupScheduleRepository.findSchedulesByPopup(popup.getId())
                .stream()
                .map(this::mapToScheduleDetailDto)
                .toList();
        PopupDetailDto detail = mapToDetailDto(popup, schedules);
        log.info("[POPUP_DETAIL_FOUND] popupId={}, storeId={}", popup.getId(), popup.getStoreId());
        return detail;
    }

    @Transactional
    public PopupUpdatedDto updatePopup(Long ownerId, UUID popupId, UpdatePopupRequest request) {
        log.info("[POPUP_UPDATE] ownerId={}, popupId={}", ownerId, popupId);

        validateOwner(ownerId);
        validatePopupId(popupId);

        String trimmedTitle = validationService.validateUpdateRequest(request);

        Popup popup = ownerPopupRepository.findOwnedPopup(popupId, ownerId)
                .orElseThrow(PopupException::popupNotFound);

        if (trimmedTitle != null) {
            validateDuplicateTitle(popup.getStoreId(), popup.getId(), trimmedTitle);
            popup.setTitle(trimmedTitle);
        }
        if (request.getDescription() != null) {
            popup.setDescription(request.getDescription());
        }
        if (request.getPopupCategory() != null) {
            popup.setCategory(request.getPopupCategory());
        }
        if (request.getReservationOpenAt() != null) {
            popup.setReservationOpenAt(request.getReservationOpenAt());
        }
        if (request.getAddressRoad() != null) {
            popup.setAddressRoad(request.getAddressRoad());
        }
        if (request.getAddressDetail() != null) {
            popup.setAddressDetail(request.getAddressDetail());
        }
        popup.setUpdatedBy(ownerId);

        Popup updatedPopup = ownerPopupRepository.save(popup);
        applyScheduleChanges(updatedPopup.getId(), request, ownerId);

        eventPublisher.publishEvent(new PopupUpdatedEvent(ownerId, updatedPopup));
        // 팝업/스케줄 변경 결과가 상세 응답에 반영되도록 캐시 삭제
        popupDetailCacheManager.evictDetail(updatedPopup.getId());

        log.info("[POPUP_UPDATED] popupId={}, storeId={}", updatedPopup.getId(), updatedPopup.getStoreId());
        return mapToUpdatedDto(updatedPopup);
    }

    @Transactional
    public PopupStatusUpdatedDto updatePopupStatus(Long ownerId, UUID popupId, UpdatePopupStatusRequest request) {
        log.info("[POPUP_STATUS_UPDATE] ownerId={}, popupId={}, status={}", ownerId, popupId, request.getStatus());

        validateOwner(ownerId);
        validatePopupId(popupId);
        validateStatusRequest(request);

        Popup popup = ownerPopupRepository.findOwnedPopup(popupId, ownerId)
                .orElseThrow(PopupException::popupNotFound);

        // 이전 상태 저장
        PopupStatus fromStatus = popup.getStatus();

        popup.setStatus(request.getStatus());
        popup.setUpdatedBy(ownerId);

        Popup updatedPopup = ownerPopupRepository.save(popup);
        if (isInactiveStatus(request.getStatus())) {
            ownerPopupScheduleRepository.deactivateActiveSchedulesByPopup(updatedPopup.getId(), LocalDateTime.now(),
                    ownerId);
        }

        eventPublisher.publishEvent(new PopupStatusUpdatedEvent(ownerId, updatedPopup));
        queuePopupStatusUpdatedOutboxEntry(updatedPopup, fromStatus, request.getStatus());

        // 상태 변경 후 상세 캐시 무효화
        popupDetailCacheManager.evictDetail(updatedPopup.getId());

        log.info("[POPUP_STATUS_UPDATED] popupId={}, status={}", updatedPopup.getId(), updatedPopup.getStatus());
        return mapToStatusUpdatedDto(updatedPopup);
    }

    @Transactional
    public PopupDeletedDto deletePopup(Long ownerId, UUID popupId) {
        log.info("[POPUP_DELETE] ownerId={}, popupId={}", ownerId, popupId);

        validateOwner(ownerId);
        validatePopupId(popupId);

        Popup popup = ownerPopupRepository.findOwnedPopup(popupId, ownerId)
                .orElseThrow(PopupException::popupNotFound);

        popup.setDeletedAt(LocalDateTime.now());
        popup.setDeletedBy(ownerId);
        popup.setUpdatedBy(ownerId);

        Popup deletedPopup = ownerPopupRepository.save(popup);
        ownerPopupScheduleRepository.softDeleteSchedulesByPopup(deletedPopup.getId(), LocalDateTime.now(), ownerId);

        eventPublisher.publishEvent(new PopupDeletedEvent(ownerId, deletedPopup));
        // 삭제 후 상세 캐시 무효화
        popupDetailCacheManager.evictDetail(deletedPopup.getId());

        log.info("[POPUP_DELETED] popupId={}, storeId={}", deletedPopup.getId(), deletedPopup.getStoreId());
        return mapToDeletedDto(deletedPopup);
    }

    private PopupCreatedDto mapToDto(Popup popup) {
        return PopupCreatedDto.builder()
                .popupId(popup.getId())
                .storeId(popup.getStoreId())
                .title(popup.getTitle())
                .description(popup.getDescription())
                .popupCategory(popup.getCategory())
                .status(popup.getStatus())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .createdAt(popup.getCreatedAt())
                .createdBy(popup.getCreatedBy())
                .build();
    }

    private PopupListDto mapToListDto(Popup popup) {
        return PopupListDto.builder()
                .popupId(popup.getId())
                .title(popup.getTitle())
                .popupCategory(popup.getCategory())
                .status(popup.getStatus())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .createdAt(popup.getCreatedAt())
                .build();
    }

    private PopupDetailDto mapToDetailDto(Popup popup, List<PopupScheduleDetailDto> schedules) {
        return PopupDetailDto.builder()
                .popupId(popup.getId())
                .storeId(popup.getStoreId())
                .title(popup.getTitle())
                .description(popup.getDescription())
                .popupCategory(popup.getCategory())
                .status(popup.getStatus())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .createdAt(popup.getCreatedAt())
                .updatedAt(popup.getUpdatedAt())
                .schedules(schedules)
                .build();
    }

    private PopupScheduleDetailDto mapToScheduleDetailDto(OwnerPopupScheduleView view) {
        return PopupScheduleDetailDto.builder()
                .scheduleId(view.getScheduleId())
                .startAt(view.getStartAt())
                .endAt(view.getEndAt())
                .price(view.getPrice())
                .capacity(view.getCapacity())
                .remainingCapacity(view.getRemainingCapacity())
                .active(view.isActive())
                .build();
    }

    private PopupUpdatedDto mapToUpdatedDto(Popup popup) {
        return PopupUpdatedDto.builder()
                .popupId(popup.getId())
                .title(popup.getTitle())
                .description(popup.getDescription())
                .status(popup.getStatus())
                .popupCategory(popup.getCategory())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .updatedBy(popup.getUpdatedBy())
                .updatedAt(popup.getUpdatedAt())
                .build();
    }

    private PopupStatusUpdatedDto mapToStatusUpdatedDto(Popup popup) {
        return PopupStatusUpdatedDto.builder()
                .popupId(popup.getId())
                .title(popup.getTitle())
                .status(popup.getStatus())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .updatedBy(popup.getUpdatedBy())
                .updatedAt(popup.getUpdatedAt())
                .build();
    }

    private PopupDeletedDto mapToDeletedDto(Popup popup) {
        return PopupDeletedDto.builder()
                .popupId(popup.getId())
                .title(popup.getTitle())
                .deletedAt(popup.getDeletedAt())
                .deletedBy(popup.getDeletedBy())
                .reservationOpenAt(popup.getReservationOpenAt())
                .addressRoad(popup.getAddressRoad())
                .addressDetail(popup.getAddressDetail())
                .build();
    }


    private Popup createPopupEntity(Long userId, CreatePopupRequest request, String title) {
        return Popup.builder()
                .storeId(request.getStoreId())
                .title(title)
                .description(request.getDescription())
                .status(PopupStatus.REQUEST)
                .category(request.getCategory())
                .reservationOpenAt(request.getReservationOpenAt())
                .addressRoad(request.getAddressRoad())
                .addressDetail(request.getAddressDetail())
                .createdBy(userId)
                .build();
    }

    private void validateOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw StoreException.ownerNotFound();
        }
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private void validatePopupId(UUID popupId) {
        if (popupId == null) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private void validatePaginationParams(int page, int size) {
        if (page <= 0) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
        if (size <= 0 || size > 100) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private void validateDuplicateTitle(UUID storeId, String title) {
        List<Popup> popups = ownerPopupRepository.findAllByStoreIdAndDeletedAtIsNull(storeId);
        boolean exists = popups.stream()
                .filter(popup -> popup.getTitle() != null)
                .anyMatch(popup -> popup.getTitle().equals(title));
        if (exists) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private void validateDuplicateTitle(UUID storeId, UUID popupId, String title) {
        List<Popup> popups = ownerPopupRepository.findAllByStoreIdAndDeletedAtIsNull(storeId);
        boolean exists = popups.stream()
                .filter(popup -> popup.getTitle() != null)
                .filter(popup -> !popup.getId().equals(popupId))
                .anyMatch(popup -> popup.getTitle().equals(title));
        if (exists) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private void validateStatusRequest(UpdatePopupStatusRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
        if (request.getStatus() == PopupStatus.REQUEST) {
            throw new PopupException(PopupResponseCode.INVALID_REQUEST);
        }
    }

    private boolean isInactiveStatus(PopupStatus status) {
        return status == PopupStatus.DRAFT || status == PopupStatus.CLOSED || status == PopupStatus.CANCELLED;
    }

    private void createPopupSchedules(UUID popupId, List<CreatePopupScheduleRequest> schedules,
            Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        for (com.popcorn.store.domain.popup.dto.owner.request.CreatePopupScheduleRequest schedule : schedules) {
            UUID scheduleId = UUID.randomUUID();
            ownerPopupScheduleRepository.insertSchedule(
                    scheduleId,
                    popupId,
                    schedule.getStartAt(),
                    schedule.getEndAt(),
                    schedule.getPrice(),
                    schedule.getCapacity(),
                    schedule.getCapacity(),
                    false,
                    now,
                    ownerId,
                    ownerId
            );
            eventPublisher.publishEvent(new PopupScheduleCreatedEvent(
                    ownerId,
                    popupId,
                    scheduleId,
                    schedule.getStartAt(),
                    schedule.getEndAt(),
                    schedule.getPrice(),
                    schedule.getCapacity()
            ));
        }
    }

    private void applyScheduleChanges(UUID popupId, UpdatePopupRequest request, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();

        List<CreatePopupScheduleRequest> createSchedules = request.getCreateSchedules();
        if (createSchedules != null && !createSchedules.isEmpty()) {
            for (com.popcorn.store.domain.popup.dto.owner.request.CreatePopupScheduleRequest schedule : createSchedules) {
                UUID scheduleId = UUID.randomUUID();
                ownerPopupScheduleRepository.insertSchedule(
                        scheduleId,
                        popupId,
                        schedule.getStartAt(),
                        schedule.getEndAt(),
                        schedule.getPrice(),
                        schedule.getCapacity(),
                        schedule.getCapacity(),
                        false,
                        now,
                        ownerId,
                        ownerId
                );
                eventPublisher.publishEvent(new PopupScheduleCreatedEvent(
                        ownerId,
                        popupId,
                        scheduleId,
                        schedule.getStartAt(),
                        schedule.getEndAt(),
                        schedule.getPrice(),
                        schedule.getCapacity()
                ));
            }
        }

        List<UpdatePopupScheduleRequest> updateSchedules = request.getUpdateSchedules();
        if (updateSchedules != null && !updateSchedules.isEmpty()) {
            for (com.popcorn.store.domain.popup.dto.owner.request.UpdatePopupScheduleRequest schedule : updateSchedules) {
                int updated = ownerPopupScheduleRepository.updateSchedule(
                        schedule.getScheduleId(),
                        popupId,
                        schedule.getStartAt(),
                        schedule.getEndAt(),
                        schedule.getPrice(),
                        schedule.getCapacity(),
                        schedule.getActive(),
                        now,
                        ownerId
                );
                if (updated == 0) {
                    throw OwnerPopupException.of(
                            com.popcorn.store.domain.popup.dto.owner.OwnerPopupResponseCode.SCHEDULE_UPDATE_NOT_FOUND);
                }
                eventPublisher.publishEvent(new PopupScheduleUpdatedEvent(
                        ownerId,
                        popupId,
                        schedule.getScheduleId(),
                        schedule.getStartAt(),
                        schedule.getEndAt(),
                        schedule.getPrice(),
                        schedule.getCapacity(),
                        schedule.getActive()
                ));
            }
        }

        List<UUID> deleteScheduleIds = request.getDeleteScheduleIds();
        if (deleteScheduleIds != null && !deleteScheduleIds.isEmpty()) {
            for (UUID scheduleId : deleteScheduleIds) {
                int deleted = ownerPopupScheduleRepository.softDeleteSchedule(scheduleId, popupId, now, ownerId);
                if (deleted == 0) {
                    throw OwnerPopupException.of(
                            com.popcorn.store.domain.popup.dto.owner.OwnerPopupResponseCode.SCHEDULE_DELETE_NOT_FOUND);
                }
                eventPublisher.publishEvent(new PopupScheduleDeletedEvent(
                        ownerId,
                        popupId,
                        scheduleId
                ));
            }
        }
    }


    /**
     * 표준 팝업 생성 이벤트 발행
     */
    private void persistPopupCreatedOutboxEntry(Popup popup, StandardPopupCreatedEvent event) {
        try {
            Map<String, Object> payload = event.toOutboxMap();
            Map<String, Object> headers = Map.of("producer", event.getProducer());
            OutboxEvent outbox = OutboxEvent.of(
                    event.getTopic(),
                    EventConstants.AggregateTypes.POPUP,
                    popup.getId().toString(),
                    event.getEventType(),
                    payload,
                    headers
            );
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("❌ [OUTBOX] 팝업 생성 표준 이벤트 저장 실패 - popupId: {}, error: {}",
                    popup.getId(), e.getMessage(), e);
        }
    }

    private void queuePopupStatusUpdatedOutboxEntry(Popup popup, PopupStatus fromStatus, PopupStatus toStatus) {
        try {
            StandardPopupStatusUpdatedEvent event = StandardPopupStatusUpdatedEvent.builder()
                    .eventType(StandardEventType.POPUP_STATUS_UPDATED)
                    .producer("store-service")
                    .storeId(popup.getStoreId())
                    .popupId(popup.getId())
                    .fromStatus(fromStatus.name())
                    .toStatus(toStatus.name())
                    .build();

            event.setDefaults();
            persistPopupStatusUpdatedOutboxEntry(popup, event);

            log.info("📦 [OUTBOX] 팝업 상태 변경 표준 이벤트 저장 완료 - popupId: {}, {} -> {}",
                    popup.getId(), fromStatus.name(), toStatus.name());
        } catch (Exception e) {
            log.error("❌ [OUTBOX] 팝업 상태 변경 표준 이벤트 저장 실패 - popupId: {}, error: {}",
                    popup.getId(), e.getMessage(), e);
        }
    }

    private void persistPopupStatusUpdatedOutboxEntry(Popup popup, StandardPopupStatusUpdatedEvent event) {
        try {
            Map<String, Object> payload = event.toOutboxMap();
            Map<String, Object> headers = Map.of("producer", event.getProducer());
            OutboxEvent outbox = OutboxEvent.of(
                    event.getTopic(),
                    EventConstants.AggregateTypes.POPUP,
                    popup.getId().toString(),
                    event.getEventType(),
                    payload,
                    headers
            );
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("❌ [OUTBOX] 팝업 상태 변경 표준 이벤트 저장 실패 - popupId: {}, error: {}",
                    popup.getId(), e.getMessage(), e);
        }
    }

}
