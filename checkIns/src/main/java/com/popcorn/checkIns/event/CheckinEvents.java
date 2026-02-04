package com.popcorn.checkIns.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CheckIn 도메인 이벤트들 (BaseEvent 기반)
 *
 * common-lib의 BaseEvent를 활용하여 일관된 이벤트 구조를 제공합니다.
 * - 메타데이터, 상관관계 추적, 버전 관리 등 BaseEvent의 모든 기능 활용
 * - CheckIn 도메인에 특화된 이벤트 페이로드
 */
public class CheckinEvents {

    // ================ QR 코드 생성 요청 이벤트 (Payment → CheckIn) ================

    /**
     * QR 코드 생성 요청 이벤트 (checkin-requests 스트림)
     * Payment 서비스에서 QR 코드 생성을 요청할 때 발행
     */
    public static class QrGenerationRequestedEvent extends BaseCheckinEvent {

        private final UUID orderId;
        private final UUID paymentId;
        private final String orderNo;
        private final UUID storeId;
        private final UUID popupId;
        private final LocalDateTime requestedAt;

        public QrGenerationRequestedEvent(UUID qrId, UUID orderId, UUID paymentId, String orderNo,
                                        UUID storeId, UUID popupId, Long userId) {
            super(qrId, "qr-generation-requested", userId);
            this.orderId = orderId;
            this.paymentId = paymentId;
            this.orderNo = orderNo;
            this.storeId = storeId;
            this.popupId = popupId;
            this.requestedAt = LocalDateTime.now();
        }

        @Override
        public Map<String, Object> getEventPayload() {
            return Map.of(
                    "qrId", getCheckinId(),
                    "orderId", orderId,
                    "paymentId", paymentId,
                    "orderNo", orderNo,
                    "storeId", storeId,
                    "popupId", popupId,
                    "requestedAt", requestedAt.toString()
            );
        }

        // Getters
        public UUID getOrderId() { return orderId; }
        public UUID getPaymentId() { return paymentId; }
        public String getOrderNo() { return orderNo; }
        public UUID getStoreId() { return storeId; }
        public UUID getPopupId() { return popupId; }
        public LocalDateTime getRequestedAt() { return requestedAt; }

        public static QrGenerationRequestedEvent create(UUID orderId, UUID paymentId, String orderNo,
                                                       UUID storeId, UUID popupId, Long userId) {
            return new QrGenerationRequestedEvent(UUID.randomUUID(), orderId, paymentId, orderNo,
                                                storeId, popupId, userId);
        }
    }

    // ================ QR 코드 생성 완료 이벤트 (CheckIn → Order, User) ================

    /**
     * QR 코드 생성 완료 이벤트 (checkin-events 스트림)
     * QR 코드가 성공적으로 생성되었을 때 발행
     */
    public static class QrGeneratedEvent extends BaseCheckinEvent {

        private final UUID orderId;
        private final String orderNo;
        private final String qrToken;
        private final String qrUrl;
        private final LocalDateTime expiresAt;
        private final LocalDateTime generatedAt;
        private final Long ttlSeconds;

        public QrGeneratedEvent(UUID qrId, UUID orderId, String orderNo, String qrToken, String qrUrl,
                               LocalDateTime expiresAt, Long ttlSeconds, Long userId) {
            super(qrId, "qr-generated", userId);
            this.orderId = orderId;
            this.orderNo = orderNo;
            this.qrToken = qrToken;
            this.qrUrl = qrUrl;
            this.expiresAt = expiresAt;
            this.ttlSeconds = ttlSeconds;
            this.generatedAt = LocalDateTime.now();
        }

        @Override
        public Map<String, Object> getEventPayload() {
            return Map.of(
                    "qrId", getCheckinId(),
                    "orderId", orderId,
                    "orderNo", orderNo,
                    "qrToken", qrToken != null ? qrToken : "null",
                    "qrUrl", qrUrl != null ? qrUrl : "null",
                    "expiresAt", expiresAt.toString(),
                    "generatedAt", generatedAt.toString(),
                    "ttlSeconds", ttlSeconds.toString()
            );
        }

        // Getters
        public UUID getQrId() { return getCheckinId(); }
        public UUID getOrderId() { return orderId; }
        public String getOrderNo() { return orderNo; }
        public String getQrToken() { return qrToken; }
        public String getQrUrl() { return qrUrl; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public Long getTtlSeconds() { return ttlSeconds; }

        public static QrGeneratedEvent create(UUID orderId, String orderNo, String qrToken, String qrUrl,
                                            LocalDateTime expiresAt, Long ttlSeconds, Long userId) {
            return new QrGeneratedEvent(UUID.randomUUID(), orderId, orderNo, qrToken, qrUrl,
                                      expiresAt, ttlSeconds, userId);
        }
    }

    // ================ 체크인 생성 이벤트 (CheckIn → Order, Query) ================

    /**
     * 체크인 생성 이벤트 (checkin-events 스트림)
     * 사용자가 QR 코드를 통해 체크인을 완료했을 때 발행
     */
    public static class CheckinCreatedEvent extends BaseCheckinEvent {

        private final UUID orderId;
        private final UUID orderGoodsId;
        private final UUID popupId;
        private final UUID storeId;
        private final UUID qrId;
        private final String qrToken;
        private final LocalDateTime checkinAt;
        private final String checkinLocation;

        public CheckinCreatedEvent(UUID checkinId, UUID orderId, UUID orderGoodsId, UUID popupId, UUID storeId,
                                  UUID qrId, String qrToken, String checkinLocation, Long userId) {
            super(checkinId, "checkin-created", userId);
            this.orderId = orderId;
            this.orderGoodsId = orderGoodsId;
            this.popupId = popupId;
            this.storeId = storeId;
            this.qrId = qrId;
            this.qrToken = qrToken;
            this.checkinLocation = checkinLocation;
            this.checkinAt = LocalDateTime.now();
        }

        @Override
        public Map<String, Object> getEventPayload() {
            return Map.of(
                    "checkinId", getCheckinId(),
                    "orderId", orderId,
                    "orderGoodsId", orderGoodsId,
                    "popupId", popupId,
                    "storeId", storeId,
                    "qrId", qrId,
                    "qrToken", qrToken != null ? qrToken : "null",
                    "checkinAt", checkinAt.toString(),
                    "checkinLocation", checkinLocation != null ? checkinLocation : "null"
            );
        }

        // Getters
        public UUID getCheckinIdValue() { return getCheckinId(); }
        public UUID getOrderId() { return orderId; }
        public UUID getOrderGoodsId() { return orderGoodsId; }
        public UUID getPopupId() { return popupId; }
        public UUID getStoreId() { return storeId; }
        public UUID getQrId() { return qrId; }
        public String getQrToken() { return qrToken; }
        public LocalDateTime getCheckinAt() { return checkinAt; }
        public String getCheckinLocation() { return checkinLocation; }

        public static CheckinCreatedEvent create(UUID orderId, UUID orderGoodsId, UUID popupId, UUID storeId,
                                               UUID qrId, String qrToken, String checkinLocation, Long userId) {
            return new CheckinCreatedEvent(UUID.randomUUID(), orderId, orderGoodsId, popupId, storeId,
                                         qrId, qrToken, checkinLocation, userId);
        }
    }

    // ================ 이벤트 Publisher 인터페이스 ================

    /**
     * CheckIn 이벤트 발행을 위한 Publisher 인터페이스
     */
    public interface CheckinEventPublisher {
        void publish(BaseCheckinEvent event);
        void publishQrGenerationRequested(QrGenerationRequestedEvent event);
        void publishQrGenerated(QrGeneratedEvent event);
        void publishCheckinCreated(CheckinCreatedEvent event);
    }

    /**
     * CheckIn 이벤트 핸들러 기본 인터페이스
     */
    public interface CheckinEventHandler<T extends BaseCheckinEvent> {
        void handle(T event);
        Class<T> getEventType();
    }
}