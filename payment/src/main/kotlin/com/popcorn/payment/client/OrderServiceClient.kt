package com.popcorn.payment.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.popcorn.payment.dto.OrderInfoResponse
import com.popcorn.payment.dto.OrderLineItem
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

                // 🔍 디버깅: Order 데이터 상세 확인
                log.info("🔍 [DEBUG] Order 데이터 확인 - orderId: {}, orderType: {}, items: {}",
                    orderId, response.data.orderType, response.data.items?.size ?: 0)

                response.data.items?.forEach { item ->
                    log.info("🔍 [DEBUG] 아이템 확인 - itemId: {}, type: {}, goodsId: {}, sessionOptionId: {}, qty: {}, price: {}",
                        item.itemId, item.orderItemType, item.goodsId, item.sessionOptionId, item.qty, item.unitPrice)
                }

                val hasGoods = hasGoodsItems(response.data)
                val lineItems = mapLineItems(response.data)
                log.info("🔍 [HTTP] 주문 정보 매핑 완료 - orderId: {}, hasGoods: {}, lineItems: {}개",
                    orderId, hasGoods, lineItems.size)

                OrderInfoResponse(
                    orderId = response.data.orderId,
                    orderNo = response.data.orderNo,
                    customerId = response.data.customerId,
                    status = response.data.status,
                    totalAmount = response.data.totalAmount,
                    success = true,
                    hasGoods = hasGoods,
                    lineItems = lineItems
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

    suspend fun updateOrderStatus(orderId: UUID, status: String, reason: String): Boolean {
        return try {
            val systemPassport = systemPassportGenerator.generateSystemPassport()
            webClient
                .patch()
                .uri { builder ->
                    builder.path("$orderServiceBaseUrl/api/orders/v1/{orderId}/status")
                        .queryParam("status", status)
                        .queryParam("reason", reason)
                        .build(orderId)
                }
                .header("X-Passport", systemPassport)
                .header("X-Internal-Call", "true")
                .header("X-Internal-Service", "payment-service")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            true
        } catch (ex: WebClientResponseException) {
            log.error("❌ [HTTP] Order 상태 변경 실패 - orderId: {}, status: {}, httpStatus: {}, error: {}",
                orderId, status, ex.statusCode, ex.message)
            false
        } catch (e: Exception) {
            log.error("❌ [HTTP] Order 상태 변경 실패 - orderId: {}, status: {}, error: {}",
                orderId, status, e.message, e)
            false
        }
    }

    private fun isPayableStatus(status: String): Boolean {
        return status in listOf("REQUESTED", "RESERVED", "PAYMENT_PENDING")
    }

    /**
     * 주문에 굿즈가 포함되어 있는지 확인
     * 굿즈가 있으면 배송 주소 검증이 필요함
     */
    fun hasGoodsItems(orderData: OrderDetailData): Boolean {
        val items = orderData.items
        log.info("🔍 [DEBUG] hasGoodsItems 검사 - 총 아이템 수: {}", items?.size ?: 0)

        if (items == null) {
            log.warn("⚠️ [DEBUG] items가 null임 - hasGoods: false")
            return false
        }

        val hasGoods = items.any { item ->
            val hasGoodsId = item.goodsId != null
            val isGoodsType = item.orderItemType == "GOODS"
            log.debug("🔍 [DEBUG] 아이템 검사 - goodsId: {}, type: {}, hasGoodsId: {}, isGoodsType: {}",
                item.goodsId, item.orderItemType, hasGoodsId, isGoodsType)
            hasGoodsId || isGoodsType
        }

        log.info("🔍 [DEBUG] hasGoodsItems 결과: {}", hasGoods)
        return hasGoods
    }

    /**
     * OrderItemDetailData를 OrderLineItem으로 변환
     * Store API 가격 검증에 사용
     */
    private fun mapLineItems(orderData: OrderDetailData): List<OrderLineItem> {
        return orderData.items?.mapNotNull { item ->
            val itemId: UUID?
            val itemType: String

            when {
                item.goodsId != null -> {
                    itemId = item.goodsId
                    itemType = "GOODS"
                }
                item.sessionOptionId != null -> {
                    itemId = item.sessionOptionId
                    itemType = "SESSION"
                }
                else -> {
                    log.debug("🚨 알 수 없는 아이템 타입 스킵 - itemDetailData: {}", item)
                    return@mapNotNull null
                }
            }

            OrderLineItem(
                itemId = itemId,
                itemType = itemType,
                quantity = item.qty ?: 1,
                unitPrice = item.unitPrice ?: 0,
                lineAmount = item.lineAmount ?: 0,
                sessionOptionId = item.sessionOptionId,
                goodsId = item.goodsId
            )
        } ?: emptyList()
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

/**
 * Order 서비스의 OrderDetailResponse 구조와 정확히 일치하도록 수정
 */
data class OrderDetailData(
    val orderId: UUID,  // ✅ @JsonProperty 제거, Order API에서 orderId로 반환
    val orderNo: String,
    val customerId: Long,
    val status: String,
    val totalAmount: Int,
    val orderType: String? = null,
    val items: List<OrderItemDetailData>? = null  // ✅ 올바른 이름으로 수정
)

/**
 * Order 서비스의 OrderDetailResponse.OrderItemDetailResponse와 매핑
 */
data class OrderItemDetailData(
    val itemId: UUID,
    val orderItemType: String,
    val qty: Int,
    val unitPrice: Int,
    val lineAmount: Int,
    val sessionOptionId: UUID? = null,
    val goodsId: UUID? = null
)
