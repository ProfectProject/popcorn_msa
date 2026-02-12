package com.popcorn.payment.service

import com.popcorn.payment.client.OrderServiceClient
import com.popcorn.payment.dto.OrderInfoResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Order 정보를 HTTP API로 조회하는 서비스
 *
 * 기존 DirectOrderQueryService의 DB 직접 조회를 대체하여
 * Order 서비스의 REST API를 호출합니다.
 *
 * 장점:
 * - Order 서비스의 비즈니스 로직을 그대로 활용
 * - 서비스 경계 명확히 유지
 * - 빠른 HTTP 통신
 */
@Service
class HttpOrderQueryService(
    private val orderServiceClient: OrderServiceClient
) {
    private val log = LoggerFactory.getLogger(HttpOrderQueryService::class.java)

    /**
     * 주문 ID로 주문 정보 조회 (결제 검증용)
     */
    suspend fun getOrderForPayment(orderId: UUID): OrderInfoResponse? {
        return try {
            log.debug("🔍 [HTTP] 주문 정보 HTTP 조회 시작 - orderId: {}", orderId)

            val orderInfo = orderServiceClient.getOrderInfo(orderId)

            if (orderInfo?.success == true) {
                log.info("✅ [HTTP] 주문 정보 조회 성공 - orderId: {}, orderNo: {}, status: {}, totalAmount: {}",
                    orderId, orderInfo.orderNo, orderInfo.status, orderInfo.totalAmount)
                orderInfo
            } else {
                log.warn("❌ [HTTP] 주문 정보 조회 실패 - orderId: {}, error: {}",
                    orderId, orderInfo?.errorMessage)
                null
            }
        } catch (e: Exception) {
            log.error("❌ [HTTP] 주문 정보 조회 실패 - orderId: {}, error: {}", orderId, e.message, e)
            null
        }
    }

    /**
     * 주문 상태 검증 (결제 가능 여부 확인)
     */
    suspend fun isPayableOrder(orderId: UUID): Boolean {
        return try {
            val isPayable = orderServiceClient.isPayableOrder(orderId)

            log.debug("🔍 [HTTP] 주문 결제 가능 여부 - orderId: {}, payable: {}",
                orderId, isPayable)

            isPayable
        } catch (e: Exception) {
            log.error("❌ [HTTP] 주문 상태 검증 실패 - orderId: {}, error: {}", orderId, e.message, e)
            false
        }
    }

    /**
     * 주문 기본 정보 조회 (상태 무관)
     */
    suspend fun getOrderBasicInfo(orderId: UUID): OrderInfoResponse? {
        return try {
            log.debug("🔍 [HTTP] 주문 기본 정보 조회 - orderId: {}", orderId)

            val orderInfo = orderServiceClient.getOrderInfo(orderId)

            if (orderInfo != null) {
                log.debug("✅ [HTTP] 주문 기본 정보 조회 성공 - orderId: {}", orderId)
                orderInfo
            } else {
                log.warn("❌ [HTTP] 주문 기본 정보 없음 - orderId: {}", orderId)
                null
            }
        } catch (e: Exception) {
            log.error("❌ [HTTP] 주문 기본 정보 조회 실패 - orderId: {}, error: {}", orderId, e.message, e)
            null
        }
    }
}