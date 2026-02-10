package com.popcorn.payment.service

import com.popcorn.payment.client.OrderServiceClient
import com.popcorn.payment.config.ReadOnlyOperation
import com.popcorn.payment.exception.PaymentException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 조회/상태 변경 서비스 (HTTP 기반)
 */
@Service
class OrderQueryCoroutineService(
    private val orderServiceClient: OrderServiceClient,
    @Value("\${payment.order-query.timeout-ms:5000}")
    private val queryTimeoutMs: Long
) {

    private val log = LoggerFactory.getLogger(OrderQueryCoroutineService::class.java)

    @ReadOnlyOperation
    suspend fun getOrder(orderId: UUID): OrderInfo {
        return try {
            val orderInfo = withTimeout(queryTimeoutMs) {
                orderServiceClient.getOrderInfo(orderId)
            } ?: throw PaymentException.externalApiError("주문 조회 실패: $orderId")

            OrderInfo(
                id = orderInfo.orderId,
                orderNo = orderInfo.orderNo,
                customerId = orderInfo.customerId,
                totalAmount = orderInfo.totalAmount,
                status = orderInfo.status,
                orderType = if (orderInfo.hasGoods) "GOODS" else "RESERVATION",
                createdAt = LocalDateTime.now()
            )
        } catch (e: TimeoutCancellationException) {
            log.error("⏱️ 주문 조회 타임아웃: orderId={}", orderId)
            throw PaymentException.externalApiError("주문 조회 타임아웃: $orderId")
        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            log.error("❌ 주문 조회 실패: orderId={}, error={}", orderId, e.message, e)
            throw PaymentException.externalApiError("주문 조회 실패: ${e.message}")
        }
    }

    suspend fun updateOrderStatus(orderId: UUID, status: String, reason: String): OrderInfo {
        return try {
            val updated = withTimeout(queryTimeoutMs) {
                orderServiceClient.updateOrderStatus(orderId, status, reason)
            }
            if (!updated) {
                throw PaymentException.externalApiError("주문 상태 업데이트 실패: $orderId")
            }
            getOrder(orderId)
        } catch (e: TimeoutCancellationException) {
            log.error("⏱️ 주문 상태 업데이트 타임아웃: orderId={}, status={}", orderId, status)
            throw PaymentException.externalApiError("주문 상태 업데이트 타임아웃: $orderId")
        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            log.error("❌ 주문 상태 업데이트 실패: orderId={}, status={}, error={}", orderId, status, e.message, e)
            throw PaymentException.externalApiError("주문 상태 업데이트 실패: ${e.message}")
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
