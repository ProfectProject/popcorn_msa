package com.popcorn.checkIns.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CheckIns 서비스 Redis Stream 이벤트 리스너
 * - 표준 CheckIns 이벤트를 Stream으로 수신
 * - Consumer Group 기반 메시지 처리
 * - QR 코드 생성 및 체크인 이벤트 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInRedisStreamListener implements StreamListener<String, MapRecord<String, String, Object>> {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onMessage(MapRecord<String, String, Object> record) {
        try {
            String streamName = record.getStream();
            String recordId = record.getId().getValue();
            Map<String, Object> values = record.getValue();

            log.info("🔔 [CHECKINS] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                    streamName, recordId, values.get("eventType"));

            String eventType = (String) values.get("eventType");
            // eventType에서 따옴표 제거
            if (eventType != null) {
                eventType = eventType.trim().replaceAll("^\"|\"$", "");
            }
            handleStreamEvent(eventType, values);

            log.debug("✅ [CHECKINS] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId);

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] Stream 메시지 처리 실패 - record: {}, error: {}",
                    record, e.getMessage(), e);
        }
    }

    /**
     * Stream 이벤트 타입별 처리 - 실제 비즈니스 로직이 있는 이벤트만 처리
     */
    private void handleStreamEvent(String eventType, Map<String, Object> values) {
        try {
            switch (eventType) {
                // === Standard 이벤트들 (기존) ===

                // 표준 QR 생성 이벤트
                case "QR_GENERATED":
                    log.info("🎯 [CHECKINS] QR 생성 이벤트 수신 (Standard) - orderId: {}, qrCode: {}",
                            values.get("orderId"), values.get("qrCode"));
                    handleQrGeneratedEvent(values);
                    break;

                // 표준 체크인 생성 이벤트
                case "CHECKIN_CREATED":
                    log.info("🎉 [CHECKINS] 체크인 생성 이벤트 수신 (Standard) - checkinId: {}, orderId: {}",
                            values.get("checkinId"), values.get("orderId"));
                    handleCheckinCreatedEvent(values);
                    break;

                // === BaseEvent 기반 이벤트들 (신규) ===

                // QR 생성 요청 이벤트 (Payment → CheckIn)
                case "qr-generation-requested":
                    log.info("🔔 [CHECKINS] QR 생성 요청 수신 (BaseEvent) - orderId: {}, paymentId: {}",
                            values.get("orderId"), values.get("paymentId"));
                    handleQrGenerationRequested(values);
                    break;

                // QR 생성 완료 이벤트 (BaseEvent 기반)
                case "qr-generated":
                    log.info("✅ [CHECKINS] QR 생성 완료 (BaseEvent) - orderId: {}, qrId: {}",
                            values.get("orderId"), values.get("qrId"));
                    handleBaseQrGeneratedEvent(values);
                    break;

                // 체크인 생성 이벤트 (BaseEvent 기반)
                case "checkin-created":
                    log.info("🎯 [CHECKINS] 체크인 생성 완료 (BaseEvent) - orderId: {}, checkinId: {}",
                            values.get("orderId"), values.get("checkinId"));
                    handleBaseCheckinCreatedEvent(values);
                    break;

                // === 외부 도메인 이벤트들 ===

                // 결제 승인 시 QR 코드 생성 준비
                case "payment-approved":
                    log.info("💳 [CHECKINS] 결제 승인 이벤트 수신 - paymentId: {}, orderId: {}",
                            values.get("paymentId"), values.get("orderId"));
                    handlePaymentApproved(values);
                    break;

                // 처리하지 않는 이벤트들은 조용히 무시
                default:
                    log.debug("📝 [CHECKINS] 처리하지 않는 이벤트 타입: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("🚨 [CHECKINS] 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * QR 생성 이벤트 처리
     */
    private void handleQrGeneratedEvent(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String qrCode = (String) values.get("qrCode");
            String qrId = (String) values.get("qrId");

            log.info("🎯 [CHECKINS] QR 생성 이벤트 처리 완료 - orderId: {}, qrId: {}", orderId, qrId);

            // 추가적인 QR 코드 후처리가 필요한 경우 여기서 수행
            // 예: 외부 알림 시스템 연동, 로깅, 모니터링 등

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] QR 생성 이벤트 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 체크인 생성 이벤트 처리
     */
    private void handleCheckinCreatedEvent(Map<String, Object> values) {
        try {
            String checkinId = (String) values.get("checkinId");
            String orderId = (String) values.get("orderId");
            String qrCode = (String) values.get("qrCode");

            log.info("🎉 [CHECKINS] 체크인 생성 이벤트 처리 완료 - checkinId: {}, orderId: {}", checkinId, orderId);

            // 추가적인 체크인 후처리가 필요한 경우 여기서 수행
            // 예: 사용자 알림, 포인트 적립, 외부 시스템 연동 등

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] 체크인 생성 이벤트 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 결제 승인 이벤트 처리 (향후 확장용)
     */
    private void handlePaymentApproved(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            if (orderId != null) {
                log.info("💳 [CHECKINS] 결제 승인으로 인한 QR 코드 생성 준비 - orderId: {}", orderId);
                // 향후 자동 QR 코드 생성 로직 추가 가능
            }
        } catch (Exception e) {
            log.error("🚨 [CHECKINS] 결제 승인 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    // ================ 새로운 BaseEvent 기반 핸들러들 ================

    /**
     * QR 생성 요청 이벤트 처리 (Payment → CheckIn)
     */
    private void handleQrGenerationRequested(Map<String, Object> values) {
        try {
            String orderId = (String) values.get("orderId");
            String paymentId = (String) values.get("paymentId");
            String orderNo = (String) values.get("orderNo");
            String storeId = (String) values.get("storeId");
            String popupId = (String) values.get("popupId");

            log.info("📱 [CHECKINS] QR 생성 요청 처리 시작 - orderId: {}, paymentId: {}", orderId, paymentId);

            // TODO: 실제 QR 코드 생성 로직 구현
            // 1. QR 코드 생성
            // 2. QrGeneratedEvent 발행
            // 3. CheckIn 도메인 비즈니스 로직 수행

            log.info("✅ [CHECKINS] QR 생성 요청 처리 완료 - orderId: {}", orderId);

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] QR 생성 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * QR 생성 완료 이벤트 처리 (BaseEvent 기반)
     */
    private void handleBaseQrGeneratedEvent(Map<String, Object> values) {
        try {
            String qrId = (String) values.get("qrId");
            String orderId = (String) values.get("orderId");
            String qrToken = (String) values.get("qrToken");
            String qrUrl = (String) values.get("qrUrl");
            String expiresAt = (String) values.get("expiresAt");

            log.info("🎯 [CHECKINS] BaseEvent QR 생성 완료 처리 - orderId: {}, qrId: {}", orderId, qrId);

            // BaseEvent 메타데이터 활용
            String eventId = (String) values.get("eventId");
            String correlationId = (String) values.get("correlationId");
            String timestamp = (String) values.get("timestamp");

            log.debug("📊 [CHECKINS] BaseEvent 메타데이터 - eventId: {}, correlationId: {}, timestamp: {}",
                    eventId, correlationId, timestamp);

            // QR 코드 후처리 로직
            // 1. 외부 시스템 알림
            // 2. 사용자 알림
            // 3. 로깅 및 모니터링

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] BaseEvent QR 생성 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 체크인 생성 이벤트 처리 (BaseEvent 기반)
     */
    private void handleBaseCheckinCreatedEvent(Map<String, Object> values) {
        try {
            String checkinId = (String) values.get("checkinId");
            String orderId = (String) values.get("orderId");
            String orderGoodsId = (String) values.get("orderGoodsId");
            String popupId = (String) values.get("popupId");
            String storeId = (String) values.get("storeId");
            String checkinAt = (String) values.get("checkinAt");

            log.info("🎉 [CHECKINS] BaseEvent 체크인 생성 완료 처리 - checkinId: {}, orderId: {}", checkinId, orderId);

            // BaseEvent 메타데이터 활용
            String eventId = (String) values.get("eventId");
            String correlationId = (String) values.get("correlationId");
            String userId = (String) values.get("userId");

            log.debug("📊 [CHECKINS] BaseEvent 메타데이터 - eventId: {}, correlationId: {}, userId: {}",
                    eventId, correlationId, userId);

            // 체크인 후처리 로직
            // 1. 포인트 적립
            // 2. 사용자 알림
            // 3. 통계 데이터 업데이트
            // 4. 외부 시스템 연동

        } catch (Exception e) {
            log.error("🚨 [CHECKINS] BaseEvent 체크인 생성 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }
}