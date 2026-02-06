package com.popcorn.payment.client

import com.popcorn.payment.dto.OrderInfoResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.*

/**
 * Order 서비스와 HTTP 통신하는 클라이언트
 *
 * 기존 이벤트 기반 통신을 대체하여 직접 REST API 호출
 */
@Service
class OrderServiceClient(
    @Qualifier("defaultWebClient")
    private val webClient: WebClient
) {
    private val log = LoggerFactory.getLogger(OrderServiceClient::class.java)

    @Value("\${order.service.base-url:http://popcorn-order:8084}")
    private lateinit var orderServiceBaseUrl: String

    /**
     * 주문 정보 조회 (HTTP API 호출)
     */
    suspend fun getOrderInfo(orderId: UUID): OrderInfoResponse? {
        return try {
            log.debug("🔍 [HTTP] Order 서비스 API 호출 시작 - orderId: {}", orderId)

            val response = webClient
                .get()
                .uri("$orderServiceBaseUrl/api/order/v1/orders/{orderId}", orderId)
                .header("X-Internal-Service", "payment-service")
                .header("X-Internal-Call", "true")
                .retrieve()
                .bodyToMono(OrderApiResponse::class.java)
                .awaitSingle()

            if (response.success && response.data != null) {
                log.info("✅ [HTTP] Order 정보 조회 성공 - orderId: {}, orderNo: {}",
                    orderId, response.data.orderNo)

                OrderInfoResponse(
                    orderId = response.data.id,
                    orderNo = response.data.orderNo,
                    customerId = response.data.customerId,
                    status = response.data.status,
                    totalAmount = response.data.totalAmount,
                    success = true
                )
            } else {
                log.warn("❌ [HTTP] Order 정보 조회 실패 - orderId: {}, message: {}",
                    orderId, response.message)
                null
            }
        } catch (ex: WebClientResponseException) {
            log.error("❌ [HTTP] Order API 호출 실패 - orderId: {}, status: {}, error: {}",
                orderId, ex.statusCode, ex.message)
            null
        } catch (e: Exception) {
            log.error("❌ [HTTP] Order 서비스 통신 실패 - orderId: {}, error: {}",
                orderId, e.message, e)
            null
        }
    }

    /**
     * 결제 가능한 주문인지 확인
     */
    suspend fun isPayableOrder(orderId: UUID): Boolean {
        val orderInfo = getOrderInfo(orderId)
        return orderInfo?.success == true && isPayableStatus(orderInfo.status)
    }

    private fun isPayableStatus(status: String): Boolean {
        return status in listOf("REQUESTED", "RESERVED", "PAYMENT_PENDING")
    }
}

/**
 * Order API 응답 DTO
 */
data class OrderApiResponse(
    val success: Boolean,
    val data: OrderDetailData?,
    val message: String?
)

data class OrderDetailData(
    val id: UUID,
    val orderNo: String,
    val customerId: Long,
    val status: String,
    val totalAmount: Int
)