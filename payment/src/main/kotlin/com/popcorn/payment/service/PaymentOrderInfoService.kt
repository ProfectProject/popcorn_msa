package com.popcorn.payment.service

import com.popcorn.payment.client.OrderServiceClient
import com.popcorn.payment.client.UserServiceClient
import com.popcorn.payment.dto.OrderInfoResponse
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Payment 서비스에서 Order 정보를 HTTP로 직접 조회하는 서비스
 * 코루틴 기반 병렬 처리로 성능 최적화
 */
@Service
class PaymentOrderInfoService(
    private val orderServiceClient: OrderServiceClient,
    private val userServiceClient: UserServiceClient,
    private val hybridValidationService: HybridValidationService
) {
    private val log = LoggerFactory.getLogger(PaymentOrderInfoService::class.java)

    /**
     * Order 정보 조회 (HTTP API - 코루틴 기반)
     */
    suspend fun getOrderInfo(orderId: UUID): OrderInfoResponse? {
        return try {
            log.info("🔍 [HTTP] Order 정보 조회 시작 - orderId: {}", orderId)

            val orderInfo = orderServiceClient.getOrderInfo(orderId)

            if (orderInfo != null) {
                log.info("✅ [HTTP] Order 정보 조회 성공 - orderId: {}, orderNo: {}, totalAmount: {}, hasGoods: {}",
                    orderId, orderInfo.orderNo, orderInfo.totalAmount, orderInfo.hasGoods)
            } else {
                log.warn("❌ [HTTP] Order 정보 조회 실패 - orderId: {}", orderId)
            }

            orderInfo
        } catch (e: Exception) {
            log.error("❌ [HTTP] Order 정보 조회 중 예외 발생 - orderId: {}, error: {}",
                orderId, e.message, e)
            null
        }
    }

    /**
     * Order 정보 조회 및 병렬 검증 (코루틴 기반)
     * - Order 정보 조회
     * - 가격 검증
     * - 주소 검증 (굿즈 포함시에만)
     */
    suspend fun getOrderInfoWithValidation(orderId: UUID, paymentAmount: Int, customerId: Long): OrderValidationResult {
        return try {
            log.info("🚀 [HTTP+COROUTINE] Order 정보 조회 및 병렬 검증 시작 - orderId: {}, paymentAmount: {}, customerId: {}",
                orderId, paymentAmount, customerId)

            // 1. Order 정보 조회 (필수)
            val orderInfo = getOrderInfo(orderId)
                ?: return OrderValidationResult(false, "Order 정보를 조회할 수 없습니다", null)

            // 2. 병렬 검증 작업들
            coroutineScope {
                val priceValidationDeferred = async {
                    validatePrice(orderInfo, paymentAmount)
                }

                val addressValidationDeferred = async {
                    if (orderInfo.hasGoods) {
                        log.info("🏠 [HTTP+COROUTINE] 굿즈 포함 주문 - 주소 검증 실행 - orderId: {}", orderId)
                        userServiceClient.hasDefaultAddress(customerId)
                    } else {
                        log.info("🎫 [HTTP+COROUTINE] 예약만 포함 주문 - 주소 검증 스킵 - orderId: {}", orderId)
                        true // 굿즈가 없으면 주소 검증 불필요
                    }
                }

                // 병렬 작업 결과 수집
                val isPriceValid = priceValidationDeferred.await()
                val isAddressValid = addressValidationDeferred.await()

                log.info("🔍 [HTTP+COROUTINE] 병렬 검증 완료 - orderId: {}, priceValid: {}, addressValid: {}",
                    orderId, isPriceValid, isAddressValid)

                when {
                    !isPriceValid -> OrderValidationResult(false, "결제 금액이 주문 금액과 일치하지 않습니다", orderInfo)
                    !isAddressValid -> OrderValidationResult(false, "유효한 배송 주소가 없습니다", orderInfo)
                    else -> OrderValidationResult(true, "검증 성공", orderInfo)
                }
            }

        } catch (e: Exception) {
            log.error("❌ [HTTP+COROUTINE] Order 검증 중 예외 발생 - orderId: {}, error: {}",
                orderId, e.message, e)
            OrderValidationResult(false, "Order 검증 중 오류가 발생했습니다: ${e.message}", null)
        }
    }

    /**
     * 결제 가능한 주문인지 확인 (병렬 처리)
     */
    suspend fun isPayableOrder(orderId: UUID): Boolean {
        return try {
            log.info("💳 [HTTP+COROUTINE] 결제 가능 여부 확인 - orderId: {}", orderId)

            val isPayable = orderServiceClient.isPayableOrder(orderId)

            log.info("💳 [HTTP+COROUTINE] 결제 가능 여부 - orderId: {}, payable: {}", orderId, isPayable)
            isPayable
        } catch (e: Exception) {
            log.error("❌ [HTTP+COROUTINE] 결제 가능 여부 확인 실패 - orderId: {}, error: {}",
                orderId, e.message, e)
            false
        }
    }

    /**
     * 가격 검증
     */
    private fun validatePrice(orderInfo: OrderInfoResponse, paymentAmount: Int): Boolean {
        val isValid = orderInfo.totalAmount == paymentAmount
        log.info("💰 [VALIDATION] 가격 검증 - orderId: {}, orderAmount: {}, paymentAmount: {}, valid: {}",
            orderInfo.orderId, orderInfo.totalAmount, paymentAmount, isValid)
        return isValid
    }
}

/**
 * Order 검증 결과
 */
data class OrderValidationResult(
    val isValid: Boolean,
    val message: String,
    val orderInfo: OrderInfoResponse?
)
