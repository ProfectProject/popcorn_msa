package com.example.orderquery.domain.summary.event;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.orderquery.domain.summary.entity.EventType;
import com.example.orderquery.domain.summary.service.PopupSummaryUpsertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopupSummaryEventListener {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "PopupCreatedEvent",
            "PopupUpdatedEvent",
            "PopupStatusUpdatedEvent",
            "PopupDeletedEvent"
    );

    private final PopupSummaryUpsertService upsertService;

    @EventListener
    public void handlePopupEvent(Object event) {
        String eventType = event == null ? null : event.getClass().getName();

        // 모든 이벤트 수신 로그
        log.info("🔔 [ORDERQUERY] 이벤트 수신 - type: {}", eventType);

        if (eventType == null || !SUPPORTED_EVENTS.contains(eventType)) {
            log.debug("🔍 [ORDERQUERY] 지원하지 않는 이벤트 타입 - type: {}", eventType);
            return;
        }

        log.info("📋 [ORDERQUERY] Popup 이벤트 처리 시작 - type: {}", eventType);

        Object popup = invoke(event, "getPopup");
        if (popup == null) {
            log.warn("Popup event ignored: missing popup payload. eventType={}", event.getClass().getName());
            return;
        }

        UUID popupId = (UUID) invoke(popup, "getId");
        UUID storeId = (UUID) invoke(popup, "getStoreId");
        if (popupId == null || storeId == null) {
            log.warn("Popup event ignored: missing ids. popupId={}, storeId={}", popupId, storeId);
            return;
        }
        Long ownerId = (Long) invoke(event, "getOwnerId");
        if (ownerId == null) {
            log.warn("Popup event ignored: missing ownerId. popupId={}, storeId={}", popupId, storeId);
            return;
        }

        String title = (String) invoke(popup, "getTitle");
        String addressRoad = (String) invoke(popup, "getAddressRoad");
        String addressDetail = (String) invoke(popup, "getAddressDetail");
        LocalDateTime reservationOpenAt = (LocalDateTime) invoke(popup, "getReservationOpenAt");
        Object statusObj = invoke(popup, "getStatus");
        String status = statusObj != null ? statusObj.toString() : null;

        if ("PopupDeletedEvent".equals(eventType)) {
            upsertService.deleteSummary(storeId, popupId);
            return;
        }

        if ("PopupCreatedEvent".equals(eventType)) {
            upsertService.createFromPopup(
                    null,
                    EventType.POPUP_CREATED,
                    popupId,
                    storeId,
                    ownerId,
                    title,
                    status,
                    addressRoad,
                    addressDetail,
                    reservationOpenAt
            );
            return;
        }

        EventType popupEventType = "PopupStatusUpdatedEvent".equals(eventType)
                ? EventType.POPUP_STATUS_UPDATED
                : EventType.POPUP_INFO_UPDATED;
        upsertService.updateFromPopup(
                null,
                popupEventType,
                popupId,
                storeId,
                ownerId,
                title,
                status,
                addressRoad,
                addressDetail,
                reservationOpenAt
        );
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ex) {
            log.debug("Popup event reflection failed. target={}, method={}, error={}",
                    target.getClass().getName(), methodName, ex.getMessage());
            return null;
        }
    }
}
