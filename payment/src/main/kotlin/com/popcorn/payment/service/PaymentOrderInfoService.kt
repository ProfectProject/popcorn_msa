package com.popcorn.payment.service

import com.popcorn.payment.event.domain.payment.OrderInfoRequestPaymentEvent
import com.popcorn.payment.event.domain.payment.OrderInfoResponseEvent
import com.popcorn.payment.event.base.BasePaymentEventPublisher
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
    private val paymentEventPublisher: BasePaymentEventPublisher,
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(PaymentOrderInfoService::class.java)

    // 진행 중인 요청들을 추적
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<OrderInfoResponseEvent>>()

    /**
     * Order 정보 요청 (이벤트 기반 - Redis Stream) - 🚀 타이밍 최적화 (15초 → 25초)
     */
    suspend fun requestOrderInfo(orderId: UUID, timeoutMs: Long = 25000): CompletableFuture<OrderInfoResponseEvent?> {
        val request = OrderInfoRequestPaymentEvent.create(orderId)
        val requestId = request.requestId
        val future = CompletableFuture<OrderInfoResponseEvent>()

        // 🚀 requestId로 추적하도록 수정 (paymentId 대신)
        pendingRequests[requestId] = future

        try {
            log.info("🔄 [EVENT] Order 정보 요청 이벤트 발행 - orderId: {}, requestId: {}", orderId, requestId)

            // Redis Stream으로 요청 이벤트 발행
            publishOrderInfoRequest(request)

            return future.completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete { result, throwable ->
                    pendingRequests.remove(requestId)
                    when {
                        result == null && throwable == null -> {
                            log.warn("⏰ [EVENT] Order 정보 요청 타임아웃 - orderId: {}, requestId: {}, timeout: {}ms",
                                orderId, requestId, timeoutMs)
                        }
                        throwable != null -> {
                            log.error("❌ [EVENT] Order 정보 요청 중 예외 발생 - orderId: {}, requestId: {}, error: {}",
                                orderId, requestId, throwable.message)
                        }
                        else -> {
                            log.debug("✅ [EVENT] Order 정보 요청 완료 - orderId: {}, requestId: {}, success: {}",
                                orderId, requestId, result?.success)
                        }
                    }
                }

        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            log.error("❌ [EVENT] Order 정보 요청 발행 실패 - orderId: {}, requestId: {}, error: {}",
                     orderId, requestId, e.message, e)
            return CompletableFuture.completedFuture(null)
        }
    }

    /**
     * Order 정보 응답 수신 처리 - 🚀 매칭 로직 개선
     */
    fun handleOrderInfoResponse(response: OrderInfoResponseEvent) {
        val requestId = response.requestId
        if (requestId.isNullOrBlank()) {
            log.warn("⚠️ Order 정보 응답에 requestId가 없음 - response: {}", response)
            return
        }

        // 🚀 requestId로 먼저 매칭 시도
        var future = pendingRequests.remove(requestId)

        // requestId로 매칭되지 않으면 다른 ID 패턴으로 재시도
        if (future == null) {
            val alternativeKeys = pendingRequests.keys.filter { key ->
                key.contains(requestId) || requestId.contains(key)
            }

            if (alternativeKeys.isNotEmpty()) {
                val matchedKey = alternativeKeys.first()
                future = pendingRequests.remove(matchedKey)
                log.debug("🔍 대체 키로 매칭 성공 - requestId: {}, matchedKey: {}", requestId, matchedKey)
            }
        }

        if (future != null) {
            // 🔍 Order 정보 응답 상세 로깅
            log.info("✅ Order 정보 응답 수신 완료 - requestId: {}, success: {}", requestId, response.success)
            log.info("🔍 [DEBUG] Order 응답 상세:")
            log.info("  - errorMessage: {}", response.errorMessage)
            log.info("  - actualLines count: {}", response.actualLines?.size ?: 0)
            log.info("  - customerId: {}", response.customerId)
            log.info("  - orderStatus: {}", response.orderStatus)
            log.info("  - totalAmount: {}", response.totalAmount)
            log.info("  - actualOrderNo: {}", response.actualOrderNo)
            log.info("  - actualUserId: {}", response.actualUserId)
            log.info("  - actualPopupId: {}", response.actualPopupId)
            log.info("  - actualStoreId: {}", response.actualStoreId)
            log.info("  - actualHasReservation: {}", response.actualHasReservation)
            log.info("  - actualHasGoods: {}", response.actualHasGoods)

            if (response.actualLines != null && response.actualLines.isNotEmpty()) {
                response.actualLines.forEachIndexed { index, line ->
                    log.info("  - actualLine[{}]: itemType={}, qty={}, unitPrice={}, linePrice={}, scheduleId={}, goodsId={}",
                        index, line.itemType, line.qty, line.unitPrice, line.linePrice, line.scheduleId, line.goodsId)
                }
            } else {
                log.warn("⚠️ actualLines가 비어있음 - count: {}", response.actualLines?.size ?: 0)
            }

            future.complete(response)
        } else {
            log.warn("🔍 매칭되는 요청이 없음 - requestId: {}, pendingRequests: {}",
                requestId, pendingRequests.keys.take(5))
        }
    }

    /**
     * Order 정보 요청 이벤트 발행 (Redis Stream) - 🚀 최적화
     */
    private suspend fun publishOrderInfoRequest(request: OrderInfoRequestPaymentEvent) {
        try {
            paymentEventPublisher.publish(request)
            log.info("📤 Order 정보 요청 이벤트 발행 완료 - requestId: {}", request.requestId)

        } catch (e: Exception) {
            log.error("❌ Order 정보 요청 이벤트 발행 실패 - requestId: {}, error: {}",
                request.requestId, e.message, e)
            throw e
        }
    }

    /**
     * 🚀 진행 중인 요청 수 모니터링 (성능 최적화용)
     */
    fun getPendingRequestCount(): Int = pendingRequests.size

    /**
     * 🚀 특정 요청의 상태 확인
     */
    fun isPendingRequest(requestId: String): Boolean = pendingRequests.containsKey(requestId)
}
