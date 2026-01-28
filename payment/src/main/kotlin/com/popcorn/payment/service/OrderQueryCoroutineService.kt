package com.popcorn.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.popcorn.payment.config.ReadOnlyOperation
import com.popcorn.payment.exception.PaymentException
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * 주문 조회 서비스 (이벤트 기반 + 코루틴 버전)
 *
 * 🔍 주요 기능:
 * - Redis Stream을 통한 이벤트 기반 주문 정보 조회
 * - Redis Stream을 통한 주문 상태 업데이트
 * - 결제와 연동된 주문 데이터 제공
 *
 * 📊 이벤트 기반 최적화:
 * - HTTP 호출 제거로 네트워크 지연 시간 최소화
 * - Redis Stream 기반 비동기 통신
 * - 캐시 우선 조회로 성능 향상
 * - 이벤트 기반 상태 업데이트
 */
@Service
class OrderQueryCoroutineService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(OrderQueryCoroutineService::class.java)

    companion object {
        private const val ORDER_QUERY_STREAM = "order:query:stream"
        private const val ORDER_UPDATE_STREAM = "order:update:stream"
        private const val ORDER_CACHE_PREFIX = "order:cache:"
        private const val QUERY_TIMEOUT_MS = 5000L
    }

    /**
     * 주문 ID로 주문 정보 조회 (이벤트 기반)
     *
     * 🎯 이벤트 기반 최적화:
     * - Redis 캐시 우선 조회
     * - Redis Stream을 통한 이벤트 기반 조회
     * - 비동기 처리로 성능 향상
     *
     * @param orderId 주문 ID
     * @return 주문 정보
     */
    @ReadOnlyOperation
    suspend fun getOrder(orderId: UUID): OrderInfo {
        log.debug("🔍 [이벤트 기반] 주문 조회: orderId={}", orderId)

        // 1. 캐시에서 먼저 조회
        val cachedOrder = getCachedOrder(orderId)
        if (cachedOrder != null) {
            log.debug("✅ 캐시에서 주문 조회 성공: orderId={}", orderId)
            return cachedOrder
        }

        // 2. Redis Stream을 통한 주문 조회 이벤트 발행
        return try {
            val correlationId = UUID.randomUUID().toString()

            // 주문 조회 요청 이벤트 발행
            val queryEvent = mapOf(
                "eventType" to "ORDER_QUERY_REQUEST",
                "orderId" to orderId.toString(),
                "correlationId" to correlationId,
                "requestedBy" to "payment-service",
                "timestamp" to LocalDateTime.now().toString()
            )

            redisTemplate.opsForStream<String, String>()
                .add(ORDER_QUERY_STREAM, queryEvent)

            log.debug("📤 주문 조회 이벤트 발행: orderId={}, correlationId={}", orderId, correlationId)

            // 응답 대기 (폴링 방식)
            withTimeout(QUERY_TIMEOUT_MS) {
                waitForOrderQueryResponse(correlationId, orderId)
            }
        } catch (e: TimeoutCancellationException) {
            log.error("⏱️ 주문 조회 타임아웃: orderId={}", orderId)
            throw PaymentException.externalApiError("주문 조회 타임아웃: $orderId")
        } catch (e: Exception) {
            log.error("❌ 주문 조회 실패: orderId={}, error={}", orderId, e.message, e)
            throw PaymentException.externalApiError("주문 조회 실패: ${e.message}")
        }
    }

    /**
     * 주문 상태 업데이트 (이벤트 기반)
     *
     * @param orderId 주문 ID
     * @param status 새로운 상태
     * @param reason 상태 변경 사유
     * @return 업데이트된 주문 정보
     */
    suspend fun updateOrderStatus(
        orderId: UUID,
        status: String,
        reason: String
    ): OrderInfo {
        log.info("🔄 [이벤트 기반] 주문 상태 업데이트: orderId={}, status={}, reason={}", orderId, status, reason)

        return try {
            val correlationId = UUID.randomUUID().toString()

            // 주문 상태 업데이트 이벤트 발행
            val updateEvent = mapOf(
                "eventType" to "ORDER_STATUS_UPDATE_REQUEST",
                "orderId" to orderId.toString(),
                "newStatus" to status,
                "reason" to reason,
                "correlationId" to correlationId,
                "requestedBy" to "payment-service",
                "timestamp" to LocalDateTime.now().toString()
            )

            redisTemplate.opsForStream<String, String>()
                .add(ORDER_UPDATE_STREAM, updateEvent)

            log.debug("📤 주문 상태 업데이트 이벤트 발행: orderId={}, status={}, correlationId={}",
                orderId, status, correlationId)

            // 응답 대기 후 캐시 무효화 및 재조회
            withTimeout(QUERY_TIMEOUT_MS) {
                waitForOrderUpdateResponse(correlationId, orderId)
            }

            // 캐시 무효화 후 재조회
            invalidateOrderCache(orderId)
            getOrder(orderId)

        } catch (e: TimeoutCancellationException) {
            log.error("⏱️ 주문 상태 업데이트 타임아웃: orderId={}, status={}", orderId, status)
            throw PaymentException.externalApiError("주문 상태 업데이트 타임아웃: $orderId")
        } catch (e: Exception) {
            log.error("❌ 주문 상태 업데이트 실패: orderId={}, status={}, error={}", orderId, status, e.message, e)
            throw PaymentException.externalApiError("주문 상태 업데이트 실패: ${e.message}")
        }
    }

    /**
     * 캐시에서 주문 정보 조회
     */
    private fun getCachedOrder(orderId: UUID): OrderInfo? {
        return try {
            val cached = redisTemplate.opsForValue().get("$ORDER_CACHE_PREFIX$orderId")
            cached?.let {
                val normalized = if (it.length >= 2 && it.first() == '"' && it.last() == '"') {
                    objectMapper.readValue(it, String::class.java)
                } else {
                    it
                }
                objectMapper.readValue(normalized, OrderInfo::class.java)
            }
        } catch (e: Exception) {
            log.warn("⚠️ 캐시 조회 실패: orderId={}, error={}", orderId, e.message)
            null
        }
    }

    /**
     * 주문 조회 응답 대기
     */
    private suspend fun waitForOrderQueryResponse(correlationId: String, orderId: UUID): OrderInfo {
        repeat(50) { // 5초 동안 100ms 간격으로 폴링
            delay(100)

            val cached = getCachedOrder(orderId)
            if (cached != null) {
                log.debug("✅ 주문 조회 응답 수신: orderId={}, correlationId={}", orderId, correlationId)
                return cached
            }
        }

        throw PaymentException.externalApiError("주문 조회 응답 타임아웃: $correlationId")
    }

    /**
     * 주문 상태 업데이트 응답 대기
     */
    private suspend fun waitForOrderUpdateResponse(correlationId: String, orderId: UUID) {
        repeat(50) { // 5초 동안 100ms 간격으로 폴링
            delay(100)

            // 업데이트 완료 확인 (임시 방법)
            val responseKey = "order:update:response:$correlationId"
            val response = redisTemplate.opsForValue().get(responseKey)
            if (response != null) {
                log.debug("✅ 주문 상태 업데이트 응답 수신: orderId={}, correlationId={}", orderId, correlationId)
                redisTemplate.delete(responseKey) // 응답 확인 후 삭제
                return
            }
        }

        throw PaymentException.externalApiError("주문 상태 업데이트 응답 타임아웃: $correlationId")
    }

    /**
     * 주문 캐시 무효화
     */
    private fun invalidateOrderCache(orderId: UUID) {
        try {
            redisTemplate.delete("$ORDER_CACHE_PREFIX$orderId")
            log.debug("🗑️ 주문 캐시 무효화: orderId={}", orderId)
        } catch (e: Exception) {
            log.warn("⚠️ 캐시 무효화 실패: orderId={}, error={}", orderId, e.message)
        }
    }

}

/**
 * 주문 정보 DTO
 */
data class OrderInfo(
    val id: UUID,
    val orderNo: String,
    val customerId: Long,
    val totalAmount: Int,
    val status: String,
    val orderType: String,
    val createdAt: LocalDateTime
)

data class ApiResponse<T>(
    val code: Int? = null,
    val message: String? = null,
    val data: T? = null
)

data class PageResponse<T>(
    val content: List<T> = emptyList()
)

data class OrderDetailApiResponse(
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val orderType: String,
    val status: String,
    val totalAmount: Int,
    val createdAt: LocalDateTime? = null
)

data class OrderSummaryApiResponse(
    val orderId: UUID,
    val orderNo: String,
    val customerId: Long,
    val orderType: String,
    val status: String,
    val totalAmount: Int,
    val createdAt: LocalDateTime? = null
)
