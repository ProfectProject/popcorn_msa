package com.popcorn.checkIns.event.listener;

import com.popcorn.checkIns.constants.EventConstants;
import com.popcorn.checkIns.event.domain.CheckinEvents.*;
import com.popcorn.checkIns.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * CheckIns 서비스 Redis Stream 이벤트 리스너
 * - 표준 CheckIns 이벤트를 Stream으로 수신
 * - Consumer Group 기반 메시지 처리
 * - QR 코드 생성 및 체크인 이벤트 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInRedisStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final ApplicationEventPublisher eventPublisher;
    private final QrCodeService qrCodeService;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            String streamName = record.getStream();
            String recordId = record.getId() != null ? record.getId().getValue() : "unknown";
            Map<String, String> values = record.getValue();

            log.info("[CHECKINS] Stream 메시지 수신 - stream: {}, recordId: {}, eventType: {}",
                    streamName, recordId, values.get(EventConstants.MetadataKeys.EVENT_TYPE));

            String eventType = (String) values.get(EventConstants.MetadataKeys.EVENT_TYPE);
            // eventType에서 따옴표 제거
            if (eventType != null) {
                eventType = eventType.trim().replaceAll("^\"|\"$", "");
            }
            handleStreamEvent(eventType, values);

            log.debug("[CHECKINS] 메시지 처리 완료 - stream: {}, recordId: {}", streamName, recordId);

        } catch (Exception e) {
            log.error("[CHECKINS] Stream 메시지 처리 실패 - record: {}, error: {}",
                    record, e.getMessage(), e);
        }
    }

    /**
     * Stream 이벤트 타입별 처리 - 실제 비즈니스 로직이 있는 이벤트만 처리
     */
    private void handleStreamEvent(String eventType, Map<String, String> values) {
        try {
            switch (eventType) {
                // === Standard 이벤트들 (기존) ===

                // 표준 QR 생성 이벤트
                case "QR_GENERATED":
                    log.info("[CHECKINS] QR 생성 이벤트 수신 (Standard) - orderId: {}, qrCode: {}",
                            values.get(EventConstants.MetadataKeys.ORDER_ID),
                            values.get(EventConstants.MetadataKeys.QR_CODE));
                    handleQrGeneratedEvent(values);
                    break;

                // 표준 체크인 생성 이벤트
                case "CHECKIN_CREATED":
                    log.info("[CHECKINS] 체크인 생성 이벤트 수신 (Standard) - checkinId: {}, orderId: {}",
                            values.get(EventConstants.MetadataKeys.CHECKIN_ID),
                            values.get(EventConstants.MetadataKeys.ORDER_ID));
                    handleCheckinCreatedEvent(values);
                    break;

                // === BaseEvent 기반 이벤트들 (신규) ===

                // QR 생성 요청 이벤트 (Payment → CheckIn)
                case "qr-generation-requested":
                    log.info("[CHECKINS] QR 생성 요청 수신 (BaseEvent) - orderId: {}, paymentId: {}",
                            values.get(EventConstants.MetadataKeys.ORDER_ID),
                            values.get(EventConstants.MetadataKeys.PAYMENT_ID));
                    handleQrGenerationRequested(values);
                    break;

                // QR 생성 완료 이벤트 (BaseEvent 기반)
                case "qr-generated":
                    log.info("[CHECKINS] QR 생성 완료 (BaseEvent) - orderId: {}, qrId: {}",
                            values.get(EventConstants.MetadataKeys.ORDER_ID),
                            values.get(EventConstants.MetadataKeys.QR_ID));
                    handleBaseQrGeneratedEvent(values);
                    break;

                // 체크인 생성 이벤트 (BaseEvent 기반)
                case "checkin-created":
                    log.info("[CHECKINS] 체크인 생성 완료 (BaseEvent) - orderId: {}, checkinId: {}",
                            values.get(EventConstants.MetadataKeys.ORDER_ID),
                            values.get(EventConstants.MetadataKeys.CHECKIN_ID));
                    handleBaseCheckinCreatedEvent(values);
                    break;

                // === 외부 도메인 이벤트들 ===

                // 결제 승인 시 QR 코드 생성 준비
                case "payment-approved":
                    log.info("[CHECKINS] 결제 승인 이벤트 수신 - paymentId: {}, orderId: {}",
                            values.get(EventConstants.MetadataKeys.PAYMENT_ID),
                            values.get(EventConstants.MetadataKeys.ORDER_ID));
                    handlePaymentApproved(values);
                    break;

                // 처리하지 않는 이벤트들은 조용히 무시
                default:
                    log.debug("[CHECKINS] 처리하지 않는 이벤트 타입: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("[CHECKINS] 이벤트 처리 실패 - eventType: {}, error: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * QR 생성 이벤트 처리
     */
    private void handleQrGeneratedEvent(Map<String, String> values) {
        try {
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            String qrCode = (String) values.get(EventConstants.MetadataKeys.QR_CODE);
            String qrId = (String) values.get(EventConstants.MetadataKeys.QR_ID);

            log.info("[CHECKINS] QR 생성 이벤트 처리 완료 - orderId: {}, qrId: {}", orderId, qrId);

            // 추가적인 QR 코드 후처리가 필요한 경우 여기서 수행
            // 예: 외부 알림 시스템 연동, 로깅, 모니터링 등

        } catch (Exception e) {
            log.error("[CHECKINS] QR 생성 이벤트 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 체크인 생성 이벤트 처리
     */
    private void handleCheckinCreatedEvent(Map<String, String> values) {
        try {
            String checkinId = (String) values.get(EventConstants.MetadataKeys.CHECKIN_ID);
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            String qrCode = (String) values.get(EventConstants.MetadataKeys.QR_CODE);

            log.info("[CHECKINS] 체크인 생성 이벤트 처리 완료 - checkinId: {}, orderId: {}", checkinId, orderId);

            // 추가적인 체크인 후처리가 필요한 경우 여기서 수행
            // 예: 사용자 알림, 포인트 적립, 외부 시스템 연동 등

        } catch (Exception e) {
            log.error("[CHECKINS] 체크인 생성 이벤트 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 결제 승인 이벤트 처리 (향후 확장용)
     */
    private void handlePaymentApproved(Map<String, String> values) {
        try {
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            if (orderId != null) {
                log.info("[CHECKINS] 결제 승인으로 인한 QR 코드 생성 준비 - orderId: {}", orderId);
                // 향후 자동 QR 코드 생성 로직 추가 가능
            }
        } catch (Exception e) {
            log.error("[CHECKINS] 결제 승인 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    // ================ 새로운 BaseEvent 기반 핸들러들 ================

    /**
     * QR 생성 요청 이벤트 처리 (Payment → CheckIn)
     */
    private void handleQrGenerationRequested(Map<String, String> values) {
        try {
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            String paymentId = (String) values.get(EventConstants.MetadataKeys.PAYMENT_ID);
            String orderNo = (String) values.get("orderNo");
            String storeId = (String) values.get("storeId");
            String popupId = (String) values.get("popupId");

            log.info("[CHECKINS] QR 생성 요청 처리 시작 - orderId: {}, paymentId: {}", orderId, paymentId);

            // 1. QR 코드 생성 - QrCodeService를 통해 실제 QR 코드 발급
            try {
                UUID orderUuid = UUID.fromString(orderId);
                var qrCodeResponse = qrCodeService.issue(orderUuid);

                log.info("[CHECKINS] QR 코드 생성 완료 - orderId: {}, qrCode: {}, expiresAt: {}",
                        orderId, qrCodeResponse.getQrCode(), qrCodeResponse.getExpiresAt());

                // 2. QrGeneratedEvent 발행 - BaseEvent 기반 이벤트 발행
                QrGeneratedEvent qrGeneratedEvent = QrGeneratedEvent.create(
                        orderUuid,           // orderId
                        orderNo,            // orderNo (가능한 경우)
                        qrCodeResponse.getQrCode(), // qrToken으로 사용
                        null,               // qrUrl (현재는 생성하지 않음)
                        qrCodeResponse.getExpiresAt(), // expiresAt
                        600L,              // ttlSeconds (10분)
                        null               // userId (현재 조회 불가)
                );

                // 3. CheckIn 도메인 비즈니스 로직 수행 - 이벤트 발행을 통한 후처리
                eventPublisher.publishEvent(qrGeneratedEvent);

                log.info("[CHECKINS] QR 생성 이벤트 발행 완료 - orderId: {}, eventId: {}",
                        orderId, qrGeneratedEvent.getEventId());

            } catch (IllegalArgumentException e) {
                log.error("[CHECKINS] 잘못된 주문 ID 형식 - orderId: {}, error: {}", orderId, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("[CHECKINS] QR 코드 생성 중 오류 발생 - orderId: {}, error: {}", orderId, e.getMessage(), e);
                throw e;
            }

            log.info("[CHECKINS] QR 생성 요청 처리 완료 - orderId: {}", orderId);

        } catch (Exception e) {
            log.error("[CHECKINS] QR 생성 요청 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * QR 생성 완료 이벤트 처리 (BaseEvent 기반)
     */
    private void handleBaseQrGeneratedEvent(Map<String, String> values) {
        try {
            String qrId = (String) values.get(EventConstants.MetadataKeys.QR_ID);
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            String qrToken = (String) values.get("qrToken");
            String qrUrl = (String) values.get("qrUrl");
            String expiresAt = (String) values.get("expiresAt");

            log.info("[CHECKINS] BaseEvent QR 생성 완료 처리 - orderId: {}, qrId: {}", orderId, qrId);

            // BaseEvent 메타데이터 활용
            String eventId = (String) values.get(EventConstants.MetadataKeys.EVENT_ID);
            String correlationId = (String) values.get("correlationId");
            String timestamp = (String) values.get("timestamp");

            log.debug("[CHECKINS] BaseEvent 메타데이터 - eventId: {}, correlationId: {}, timestamp: {}",
                    eventId, correlationId, timestamp);

            // QR 코드 후처리 로직
            // 1. 외부 시스템 알림
            // 2. 사용자 알림
            // 3. 로깅 및 모니터링

        } catch (Exception e) {
            log.error("[CHECKINS] BaseEvent QR 생성 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }

    /**
     * 체크인 생성 이벤트 처리 (BaseEvent 기반)
     */
    private void handleBaseCheckinCreatedEvent(Map<String, String> values) {
        try {
            String checkinId = (String) values.get(EventConstants.MetadataKeys.CHECKIN_ID);
            String orderId = (String) values.get(EventConstants.MetadataKeys.ORDER_ID);
            String orderGoodsId = (String) values.get("orderGoodsId");
            String popupId = (String) values.get("popupId");
            String storeId = (String) values.get("storeId");
            String checkinAt = (String) values.get("checkinAt");

            log.info("[CHECKINS] BaseEvent 체크인 생성 완료 처리 - checkinId: {}, orderId: {}", checkinId, orderId);

            // BaseEvent 메타데이터 활용
            String eventId = (String) values.get(EventConstants.MetadataKeys.EVENT_ID);
            String correlationId = (String) values.get("correlationId");
            String userId = (String) values.get("userId");

            log.debug("[CHECKINS] BaseEvent 메타데이터 - eventId: {}, correlationId: {}, userId: {}",
                    eventId, correlationId, userId);

            // 체크인 후처리 로직
            // 1. 포인트 적립
            // 2. 사용자 알림
            // 3. 통계 데이터 업데이트
            // 4. 외부 시스템 연동

        } catch (Exception e) {
            log.error("[CHECKINS] BaseEvent 체크인 생성 처리 실패 - values: {}, error: {}", values, e.getMessage(), e);
        }
    }
}
