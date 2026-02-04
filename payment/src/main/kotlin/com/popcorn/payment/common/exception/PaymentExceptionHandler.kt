package com.popcorn.payment.common.exception

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Payment 도메인 전용 예외 처리 유틸리티
 *
 * 일관된 예외 처리 및 로깅 패턴을 제공하여
 * 코드 중복을 최소화하고 유지보수성을 향상시킵니다.
 */
object PaymentExceptionHandler {

    /**
     * Redis Stream 이벤트 처리 중 발생하는 예외를 처리합니다
     */
    fun handleStreamProcessingException(
        logger: Logger,
        context: String,
        eventType: String? = null,
        eventData: Map<String, Any?> = emptyMap(),
        exception: Exception
    ) {
        val errorMessage = buildString {
            append("[PAYMENT] ")
            append(context)
            append(" 처리 실패")
            if (eventType != null) {
                append(" - eventType: ").append(eventType)
            }
            if (eventData.isNotEmpty()) {
                append(", eventData: ").append(eventData.keys.joinToString(","))
            }
            append(", error: ").append(exception.message)
        }

        logger.error(errorMessage, exception)
    }

    /**
     * Redis Pub/Sub 이벤트 처리 중 발생하는 예외를 처리합니다
     */
    fun handlePubSubProcessingException(
        logger: Logger,
        channel: String,
        messageBody: String,
        exception: Exception
    ) {
        logger.error(
            "[PAYMENT] Redis 이벤트 처리 실패 - channel: {}, body: {}, error: {}",
            channel,
            maskSensitiveData(messageBody),
            exception.message,
            exception
        )
    }

    /**
     * 이벤트 발행 중 발생하는 예외를 처리합니다
     */
    fun handleEventPublishException(
        logger: Logger,
        eventType: String,
        eventClass: String,
        exception: Exception
    ) {
        logger.error(
            "[PAYMENT] 이벤트 발행 실패 - eventType: {}, eventClass: {}, error: {}",
            eventType,
            eventClass,
            exception.message,
            exception
        )
    }

    /**
     * 비즈니스 로직 처리 중 발생하는 예외를 처리합니다
     */
    fun handleBusinessLogicException(
        logger: Logger,
        operation: String,
        paymentId: String? = null,
        orderId: String? = null,
        exception: Exception
    ) {
        val contextInfo = buildString {
            if (paymentId != null) append("paymentId: $paymentId")
            if (orderId != null) {
                if (isNotEmpty()) append(", ")
                append("orderId: $orderId")
            }
        }

        logger.error(
            "[PAYMENT] {} 실패 - {}, error: {}",
            operation,
            contextInfo.ifEmpty { "no context" },
            exception.message,
            exception
        )
    }

    /**
     * 재시도 가능한 예외인지 확인합니다
     */
    fun isRetryableException(exception: Exception): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is org.springframework.dao.QueryTimeoutException,
            is org.springframework.dao.TransientDataAccessException -> true
            else -> false
        }
    }

    /**
     * 예외 타입별로 적절한 로그 레벨을 결정합니다
     */
    fun getLogLevel(exception: Exception): LogLevel {
        return when {
            isRetryableException(exception) -> LogLevel.WARN
            exception is IllegalArgumentException ||
            exception is IllegalStateException -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
    }

    /**
     * 민감한 데이터를 마스킹 처리합니다
     */
    private fun maskSensitiveData(data: String): String {
        return data.replace(Regex(""""(password|token|key|secret)"\s*:\s*"[^"]*""""),
                          """"$1":"***"""")
               .replace(Regex(""""cardNumber"\s*:\s*"[\d\s-]*""""),
                          """"cardNumber":"****"""")
    }

    /**
     * 로그 레벨 열거형
     */
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * 예외 상황에서 안전하게 로깅을 수행하는 확장 함수들
     */
    fun Logger.safeWarn(message: String, vararg args: Any?) {
        try {
            warn(message, *args)
        } catch (e: Exception) {
            // 로깅 실패 시에도 애플리케이션이 중단되지 않도록 함
            System.err.println("Logging failed: $message")
        }
    }

    fun Logger.safeError(message: String, throwable: Throwable? = null, vararg args: Any?) {
        try {
            if (throwable != null) {
                error(message, *args, throwable)
            } else {
                error(message, *args)
            }
        } catch (e: Exception) {
            System.err.println("Error logging failed: $message")
            throwable?.printStackTrace()
        }
    }

    fun Logger.safeInfo(message: String, vararg args: Any?) {
        try {
            info(message, *args)
        } catch (e: Exception) {
            System.err.println("Info logging failed: $message")
        }
    }
}