package com.popcorn.payment.service

import com.popcorn.payment.event.standard.OrderInfoRequestEvent
import com.popcorn.payment.event.standard.OrderInfoResponseEvent
import com.popcorn.payment.event.standard.StandardPaymentEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Payment 서비스에서 Order 정보를 요청/응답 받는 서비스
 * Redis Stream을 통한 비동기 요청/응답 패턴
 */
@Service
class PaymentOrderInfoService(
    private val standardPaymentEventPublisher: StandardPaymentEventPublisher,
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(PaymentOrderInfoService::class.java)

    // 진행 중인 요청들을 추적
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<OrderInfoResponseEvent>>()

    /**
     * Order 정보 요청 (이벤트 기반 - Redis Stream)
     */
    fun requestOrderInfo(orderId: UUID, timeoutMs: Long = 15000): CompletableFuture<OrderInfoResponseEvent?> {
        val request = OrderInfoRequestEvent.create(orderId)
        val future = CompletableFuture<OrderInfoResponseEvent>()

        pendingRequests[request.requestId!!] = future

        try {
            log.info("🔄 [EVENT] Order 정보 요청 이벤트 발행 - orderId: {}, requestId: {}", orderId, request.requestId)

            // Redis Stream으로 요청 이벤트 발행
            publishOrderInfoRequest(request)

            return future.completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete { result, throwable ->
                    pendingRequests.remove(request.requestId)
                    if (result == null) {
                        log.warn("🔄 [EVENT] Order 정보 요청 타임아웃 - orderId: {}, requestId: {}, timeout: {}ms",
                            orderId, request.requestId, timeoutMs)
                    }
                    if (throwable != null) {
                        log.error("🔄 [EVENT] Order 정보 요청 중 예외 발생 - orderId: {}, requestId: {}, error: {}",
                            orderId, request.requestId, throwable.message)
                    }
                }

        } catch (e: Exception) {
            pendingRequests.remove(request.requestId)
            log.error("❌ [EVENT] Order 정보 요청 발행 실패 - orderId: {}, error: {}", orderId, e.message, e)
            return CompletableFuture.completedFuture(null)
        }
    }

    /**
     * Order 정보 응답 수신 처리
     */
    fun handleOrderInfoResponse(response: OrderInfoResponseEvent) {
        val requestId = response.requestId
        if (requestId == null) {
            log.warn("⚠️ Order 정보 응답에 requestId가 없음")
            return
        }

        val future = pendingRequests.remove(requestId)
        if (future != null) {
            future.complete(response)
            log.info("✅ Order 정보 응답 수신 완료 - requestId: {}, success: {}", requestId, response.success)
        } else {
            log.debug("🔍 매칭되는 요청이 없음 - requestId: {}", requestId)
        }
    }

    /**
     * Order 정보 요청 이벤트 발행 (Redis Stream)
     */
    private fun publishOrderInfoRequest(request: OrderInfoRequestEvent) {
        try {
            // 요청 전용 Stream에 발행
            val eventData = request.toStreamMap()
            val record = org.springframework.data.redis.connection.stream.StreamRecords
                .string(eventData)
                .withStreamKey("order-info-requests")

            val recordId = redisTemplate.opsForStream<String, Any>().add(record)?.value
            log.info("📤 Order 정보 요청 이벤트 발행 완료 - requestId: {}, recordId: {}",
                request.requestId, recordId)

        } catch (e: Exception) {
            log.error("❌ Order 정보 요청 이벤트 발행 실패 - requestId: {}, error: {}",
                request.requestId, e.message, e)
            throw e
        }
    }
}