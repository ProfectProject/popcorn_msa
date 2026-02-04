package com.popcorn.order.exception;

import lombok.Getter;

/**
 * 외부 서비스 연동 중 오류가 발생했을 때 던지는 예외
 *
 * 다른 마이크로서비스나 외부 API 호출 실패,
 * 네트워크 타임아웃, 서비스 다운 등의 상황에서 발생합니다.
 */
@Getter
public class ExternalServiceException extends RuntimeException {

    private final String serviceName;
    private final String operation;
    private final String errorCode;

    public ExternalServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
        this.operation = null;
        this.errorCode = null;
    }

    public ExternalServiceException(String serviceName, String operation, String message) {
        super(message);
        this.serviceName = serviceName;
        this.operation = operation;
        this.errorCode = null;
    }

    public ExternalServiceException(String serviceName, String operation, String errorCode, String message) {
        super(message);
        this.serviceName = serviceName;
        this.operation = operation;
        this.errorCode = errorCode;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.operation = null;
        this.errorCode = null;
    }

    /**
     * 결제 서비스 연동 실패시 사용하는 팩토리 메서드
     */
    public static ExternalServiceException paymentServiceError(String operation, String message) {
        return new ExternalServiceException("payment-service", operation, message);
    }

    /**
     * 사용자 서비스 연동 실패시 사용하는 팩토리 메서드
     */
    public static ExternalServiceException userServiceError(String operation, String message) {
        return new ExternalServiceException("user-service", operation, message);
    }

    /**
     * 재고 서비스 연동 실패시 사용하는 팩토리 메서드
     */
    public static ExternalServiceException inventoryServiceError(String operation, String message) {
        return new ExternalServiceException("inventory-service", operation, message);
    }

    /**
     * 스토어 서비스 연동 실패시 사용하는 팩토리 메서드
     */
    public static ExternalServiceException storeServiceError(String operation, String message) {
        return new ExternalServiceException("store-service", operation, message);
    }

    /**
     * 알림 서비스 연동 실패시 사용하는 팩토리 메서드
     */
    public static ExternalServiceException notificationServiceError(String operation, String message) {
        return new ExternalServiceException("notification-service", operation, message);
    }

    /**
     * 서비스 일시적 사용불가 (503) 팩토리 메서드
     */
    public static ExternalServiceException serviceUnavailable(String serviceName, String operation) {
        return new ExternalServiceException(serviceName, operation, "SERVICE_UNAVAILABLE",
                                          serviceName + " 서비스가 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * 타임아웃 팩토리 메서드
     */
    public static ExternalServiceException timeout(String serviceName, String operation) {
        return new ExternalServiceException(serviceName, operation, "TIMEOUT",
                                          serviceName + " 서비스 응답 시간이 초과되었습니다.");
    }

}