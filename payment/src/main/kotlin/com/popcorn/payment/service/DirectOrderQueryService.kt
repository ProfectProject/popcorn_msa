package com.popcorn.payment.service

import com.popcorn.payment.entity.OrderQueryEntity
import com.popcorn.payment.entity.OrderStatus
import com.popcorn.payment.repository.OrderQueryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Order 정보를 직접 DB 조회하는 서비스
 *
 * 기존 PaymentOrderInfoService의 이벤트 기반 통신을 대체하여
 * 직접 데이터베이스에서 Order 정보를 조회합니다.
 *
 * 장점:
 * - 타임아웃 문제 해결
 * - 빠른 응답 시간
 * - 네트워크 의존성 제거
 */
@Service
class DirectOrderQueryService(
    private val orderQueryRepository: OrderQueryRepository
) {
    private val log = LoggerFactory.getLogger(DirectOrderQueryService::class.java)

    /**
     * 주문 ID로 주문 정보 조회 (결제 검증용)
     */
    fun getOrderForPayment(orderId: UUID): OrderQueryEntity? {
        return try {
            log.debug("🔍 [DIRECT] 주문 정보 직접 조회 시작 - orderId: {}", orderId)

            val order = orderQueryRepository.findPayableOrderById(orderId)

            if (order.isPresent) {
                val orderEntity = order.get()
                log.info("✅ [DIRECT] 주문 정보 조회 성공 - orderId: {}, orderNo: {}, status: {}, totalAmount: {}",
                    orderId, orderEntity.orderNo, orderEntity.status, orderEntity.totalAmount)
                orderEntity
            } else {
                log.warn("❌ [DIRECT] 주문 정보 없음 또는 결제 불가 상태 - orderId: {}", orderId)
                null
            }
        } catch (e: Exception) {
            log.error("❌ [DIRECT] 주문 정보 조회 실패 - orderId: {}, error: {}", orderId, e.message, e)
            null
        }
    }

    /**
     * 주문 상태 검증 (결제 가능 여부 확인)
     */
    fun isPayableOrder(orderId: UUID): Boolean {
        return try {
            val order = getOrderForPayment(orderId)
            val isPayable = order != null && isPayableStatus(order.status)

            log.debug("🔍 [DIRECT] 주문 결제 가능 여부 - orderId: {}, payable: {}, status: {}",
                orderId, isPayable, order?.status)

            isPayable
        } catch (e: Exception) {
            log.error("❌ [DIRECT] 주문 상태 검증 실패 - orderId: {}, error: {}", orderId, e.message, e)
            false
        }
    }

    /**
     * 주문 기본 정보 조회 (상태 무관)
     */
    fun getOrderBasicInfo(orderId: UUID): OrderQueryEntity? {
        return try {
            log.debug("🔍 [DIRECT] 주문 기본 정보 조회 - orderId: {}", orderId)

            val order = orderQueryRepository.findById(orderId)

            if (order.isPresent) {
                log.debug("✅ [DIRECT] 주문 기본 정보 조회 성공 - orderId: {}", orderId)
                order.get()
            } else {
                log.warn("❌ [DIRECT] 주문 기본 정보 없음 - orderId: {}", orderId)
                null
            }
        } catch (e: Exception) {
            log.error("❌ [DIRECT] 주문 기본 정보 조회 실패 - orderId: {}, error: {}", orderId, e.message, e)
            null
        }
    }

    /**
     * 결제 가능한 상태인지 확인
     */
    private fun isPayableStatus(status: OrderStatus): Boolean {
        return status in listOf(
            OrderStatus.REQUESTED,
            OrderStatus.RESERVED,
            OrderStatus.PAYMENT_PENDING
        )
    }
}