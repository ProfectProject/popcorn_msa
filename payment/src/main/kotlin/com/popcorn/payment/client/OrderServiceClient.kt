package com.popcorn.payment.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.popcorn.payment.dto.OrderInfoResponse
import com.popcorn.payment.util.SystemPassportGenerator
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
    private val webClient: WebClient,
    private val systemPassportGenerator: SystemPassportGenerator
) {
    private val log = LoggerFactory.getLogger(OrderServiceClient::class.java)

    @Value("\${order.service.base-url:http://popcorn-gateway:8080}")
    private lateinit var orderServiceBaseUrl: String

    /**
     * 주문 정보 조회 (HTTP API 호출)
     */
    suspend fun getOrderInfo(orderId: UUID): OrderInfoResponse? {
        return try {
            log.debug("🔍 [HTTP] Order 서비스 API 호출 시작 - orderId: {}", orderId)

            val systemPassport = systemPassportGenerator.generateSystemPassport()

            val response = webClient
                .get()
                .uri("$orderServiceBaseUrl/api/orders/v1/{orderId}", orderId)
                .header("X-Passport", systemPassport)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .bodyToMono(OrderApiResponse::class.java)
                .awaitSingle()

            if (isSuccessResponse(response) && response.data != null) {
                log.info("✅ [HTTP] Order 정보 조회 성공 - orderId: {}, orderNo: {}",
                    orderId, response.data.orderNo)

                val hasGoods = hasGoodsItems(response.data)
                log.info("🔍 [HTTP] 굿즈 포함 여부 확인 - orderId: {}, hasGoods: {}", orderId, hasGoods)

                OrderInfoResponse(
                    orderId = response.data.id,
                    orderNo = response.data.orderNo,
                    customerId = response.data.customerId,
                    status = response.data.status,
                    totalAmount = response.data.totalAmount,
                    success = true,
                    hasGoods = hasGoods
                )
            } else {
                log.warn("❌ [HTTP] Order 정보 조회 실패 - orderId: {}, code: {}, message: {}",
                    orderId, response.code, response.message)
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

    /**
     * 주문에 굿즈가 포함되어 있는지 확인
     * 굿즈가 있으면 배송 주소 검증이 필요함
     */
    fun hasGoodsItems(orderData: OrderDetailData): Boolean {
        return orderData.items?.any { item ->
            item.goodsId != null || item.orderItemType == "GOODS"
        } ?: false
    }

    /**
     * Order 서비스 응답이 성공인지 확인
     * 2xxx 코드는 성공 응답
     */
    private fun isSuccessResponse(response: OrderApiResponse): Boolean {
        return response.code in 2000..2999
    }
}

/**
 * Order API 응답 DTO (Order 서비스의 BaseResponse 구조에 맞춤)
 */
data class OrderApiResponse(
    val code: Int,
    val message: String,
    val data: OrderDetailData?
)

data class OrderDetailData(
    @JsonProperty("orderId")
    val id: UUID,
    val orderNo: String,
    val customerId: Long,
    val status: String,
    val totalAmount: Int,
    val orderType: String? = null,
    val items: List<OrderItemData>? = null
)

data class OrderItemData(
    val itemId: UUID? = null,
    val orderItemType: String? = null,
    val qty: Int? = null,
    val unitPrice: Int? = null,
    val lineAmount: Int? = null,
    val sessionOptionId: UUID? = null,
    val goodsId: UUID? = null
)