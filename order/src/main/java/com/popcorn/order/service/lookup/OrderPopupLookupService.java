package com.popcorn.order.service.lookup;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.popcorn.order.dto.store.PopupInfoResponse;
import com.popcorn.order.client.StoreServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Order 서비스의 Popup 정보 조회 서비스 (HTTP 동기식)
 * Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPopupLookupService {

    private final StoreServiceClient storeServiceClient;

    /**
     * 팝업 정보 조회 (HTTP 동기식)
     * 이전 Redis Stream 기반 비동기 방식에서 HTTP 동기 방식으로 변경
     */
    public Optional<PopupInfoResponse> getPopupInfo(UUID popupId) {
        try {
            log.info("🏪 [동기] 팝업 정보 조회 시작 - popupId: {}", popupId);

            PopupInfoResponse popupInfo = storeServiceClient.getPopupInfo(popupId);

            if (popupInfo != null) {
                log.info("✅ [동기] 팝업 정보 조회 성공 - popupId: {}, title: {}",
                        popupId, popupInfo.getTitle());
                return Optional.of(popupInfo);
            } else {
                log.warn("⚠️ [동기] 팝업 정보 조회 결과 없음 - popupId: {}", popupId);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("🚨 [동기] 팝업 정보 조회 실패 - popupId: {}, error: {}", popupId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}