package com.popcorn.payment.exception

/**
 * 결제 관련 예외 클래스
 */
sealed class PaymentException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * 결제 정보를 찾을 수 없음
     */
    class PaymentNotFound(message: String = "결제 정보를 찾을 수 없습니다.") : PaymentException(message) {
        companion object {
            fun create(): PaymentNotFound = PaymentNotFound()
        }
    }

    /**
     * 잘못된 요청
     */
    class InvalidRequest(message: String = "잘못된 요청입니다.") : PaymentException(message) {
        companion object {
            fun create(): InvalidRequest = InvalidRequest()
            fun create(message: String): InvalidRequest = InvalidRequest(message)
        }
    }

    /**
     * 중복 결제 시도
     */
    class DuplicatePaymentAttempt(
        message: String = "이미 처리 중인 결제가 있습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(): DuplicatePaymentAttempt = DuplicatePaymentAttempt()
        }
    }

    /**
     * 외부 API 호출 실패
     */
    class ExternalApiError(
        message: String,
        cause: Throwable? = null
    ) : PaymentException(message, cause) {
        companion object {
            fun create(message: String): ExternalApiError = ExternalApiError(message)
            fun create(message: String, cause: Throwable): ExternalApiError = ExternalApiError(message, cause)
        }
    }

    /**
     * 결제 상태 전환 오류
     */
    class InvalidStatusTransition(
        message: String = "유효하지 않은 결제 상태 전환입니다."
    ) : PaymentException(message) {
        companion object {
            fun create(): InvalidStatusTransition = InvalidStatusTransition()
            fun create(message: String): InvalidStatusTransition = InvalidStatusTransition(message)
        }
    }

    /**
     * 결제 금액 불일치
     */
    class AmountMismatch(
        message: String = "결제 금액이 일치하지 않습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(): AmountMismatch = AmountMismatch()
            fun create(message: String): AmountMismatch = AmountMismatch(message)
        }
    }

    /**
     * 결제 기한 초과
     */
    class PaymentExpired(
        message: String = "결제 가능 시간이 초과되었습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(): PaymentExpired = PaymentExpired()
        }
    }

    /**
     * 검증 실패 (주소, 가격 등)
     */
    class ValidationFailed(
        message: String = "검증에 실패했습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(message: String): ValidationFailed = ValidationFailed(message)
        }
    }

    /**
     * 외부 서비스 오류
     */
    class ExternalServiceError(
        message: String = "외부 서비스 연동 중 오류가 발생했습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(message: String): ExternalServiceError = ExternalServiceError(message)
        }
    }

    /**
     * 보상 트랜잭션 실패
     */
    class CompensationFailed(
        message: String = "보상 트랜잭션 처리에 실패했습니다."
    ) : PaymentException(message) {
        companion object {
            fun create(message: String): CompensationFailed = CompensationFailed(message)
        }
    }

    companion object {
        // 정적 팩토리 메서드들 (기존 Java 코드와의 호환성을 위해)
        fun paymentNotFound(): PaymentNotFound = PaymentNotFound.create()
        fun invalidRequest(): InvalidRequest = InvalidRequest.create()
        fun invalidRequest(message: String): InvalidRequest = InvalidRequest.create(message)
        fun duplicatePaymentAttempt(): DuplicatePaymentAttempt = DuplicatePaymentAttempt.create()
        fun externalApiError(message: String): ExternalApiError = ExternalApiError.create(message)
        fun externalApiError(message: String, cause: Throwable): ExternalApiError = ExternalApiError.create(message, cause)
        fun invalidStatusTransition(): InvalidStatusTransition = InvalidStatusTransition.create()
        fun invalidStatusTransition(message: String): InvalidStatusTransition = InvalidStatusTransition.create(message)
        fun amountMismatch(): AmountMismatch = AmountMismatch.create()
        fun amountMismatch(message: String): AmountMismatch = AmountMismatch.create(message)
        fun paymentExpired(): PaymentExpired = PaymentExpired.create()
        fun validationFailed(message: String): ValidationFailed = ValidationFailed.create(message)
        fun externalServiceError(message: String): ExternalServiceError = ExternalServiceError.create(message)
        fun compensationFailed(message: String): CompensationFailed = CompensationFailed.create(message)
    }
}