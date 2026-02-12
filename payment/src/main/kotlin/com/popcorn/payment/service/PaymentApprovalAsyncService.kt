package com.popcorn.payment.service

import com.popcorn.payment.dto.PaymentConfirmRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

@Service
class PaymentApprovalAsyncService(
    private val tossPaymentService: TossPaymentCoroutineService
) {
    private val log = LoggerFactory.getLogger(PaymentApprovalAsyncService::class.java)

    // 전용 코루틴 디스패처 생성 (최적화된 스레드 풀)
    private val paymentDispatcher: CoroutineContext =
        Dispatchers.IO.limitedParallelism(20) + SupervisorJob()

    /**
     * ⚡ 최적화된 비동기 결제 승인 - 코루틴 네이티브 방식
     */
    @Async("asyncTaskExecutor")
    fun confirmAsync(request: PaymentConfirmRequest): CompletableFuture<Void> {
        val scope = CoroutineScope(paymentDispatcher)

        return scope.async {
            try {
                val startTime = System.currentTimeMillis()

                // 순수 코루틴 방식으로 처리 (runBlocking 제거)
                tossPaymentService.confirmPayment(
                    paymentKey = request.paymentKey,
                    orderId = request.orderId,
                    amount = request.amount
                )

                val duration = System.currentTimeMillis() - startTime
                log.info("⚡ 결제 승인(비동기) 완료: paymentKey={}, orderId={}, 처리시간={}ms",
                    request.paymentKey, request.orderId, duration)
            } catch (e: Exception) {
                log.error("❌ 결제 승인(비동기) 실패: paymentKey={}, orderId={}, error={}",
                    request.paymentKey, request.orderId, e.message, e)
                throw e
            }
        }.let { deferred ->
            // CompletableFuture로 변환
            GlobalScope.future {
                deferred.await()
                Unit
            }.thenApply { null as Void? }
        }
    }

    /**
     * 🔥 고속 병렬 결제 승인 - 여러 결제 동시 처리
     */
    @Async("asyncTaskExecutor")
    fun confirmBatchAsync(requests: List<PaymentConfirmRequest>): CompletableFuture<List<String>> {
        val scope = CoroutineScope(paymentDispatcher)

        return scope.async {
            val startTime = System.currentTimeMillis()

            // 병렬 처리로 성능 극대화
            val results = requests.map { request ->
                async {
                    try {
                        tossPaymentService.confirmPayment(
                            paymentKey = request.paymentKey,
                            orderId = request.orderId,
                            amount = request.amount
                        )
                        "✅ ${request.paymentKey}"
                    } catch (e: Exception) {
                        "❌ ${request.paymentKey}: ${e.message}"
                    }
                }
            }.awaitAll()

            val duration = System.currentTimeMillis() - startTime
            log.info("🚀 배치 결제 승인 완료: count={}, 처리시간={}ms", requests.size, duration)

            results
        }.let { deferred ->
            GlobalScope.future { deferred.await() }
        }
    }
}
