package com.popcorn.payment.service

import com.popcorn.payment.dto.PaymentConfirmRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class PaymentApprovalAsyncService(
    private val tossPaymentService: TossPaymentCoroutineService
) {
    private val log = LoggerFactory.getLogger(PaymentApprovalAsyncService::class.java)

    @Async("asyncTaskExecutor")
    fun confirmAsync(request: PaymentConfirmRequest): java.util.concurrent.CompletableFuture<Void> {
        return java.util.concurrent.CompletableFuture.runAsync {
            try {
                // 코루틴을 새로운 스레드에서 실행하여 블로킹 방지
                kotlinx.coroutines.runBlocking {
                    tossPaymentService.confirmPayment(
                        paymentKey = request.paymentKey,
                        orderId = request.orderId,
                        amount = request.amount
                    )
                }
                log.info("✅ 결제 승인(비동기) 완료: paymentKey={}, orderId={}",
                    request.paymentKey, request.orderId)
            } catch (e: Exception) {
                log.error("❌ 결제 승인(비동기) 실패: paymentKey={}, orderId={}, error={}",
                    request.paymentKey, request.orderId, e.message, e)
                throw e
            }
        }
    }
}
