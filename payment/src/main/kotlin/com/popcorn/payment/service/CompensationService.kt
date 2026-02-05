package com.popcorn.payment.service

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.base.BasePaymentEventPublisher
import com.popcorn.payment.event.domain.compensation.CompensationRequestedEvent
import com.popcorn.payment.event.domain.compensation.PaymentValidationFailedEvent
import com.popcorn.payment.event.integration.request.OrderCompensationRequestedEvent
import com.popcorn.payment.exception.PaymentException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 보상 트랜잭션 서비스
 *
 * 결제 과정에서 발생하는 실패 상황에 대한 보상 처리를 담당합니다.
 * - 검증 실패 시 보상
 * - 결제 실패 시 보상
 * - 부분 성공 시나리오 처리
 */
@Service
class CompensationService(
    private val paymentEventPublisher: BasePaymentEventPublisher,
    private val orderQueryService: OrderQueryCoroutineService,
    private val stringRedisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(CompensationService::class.java)

    /**
     * 결제 검증 실패에 대한 보상 처리
     *
     * @param orderId 주문 ID
     * @param validationType 검증 타입 (ADDRESS, PRICE, ORDER_STATUS)
     * @param failureReason 실패 사유
     * @param amount 결제 금액
     * @param paymentId 결제 ID (생성된 경우)
     * @param userId 사용자 ID
     */
    suspend fun handleValidationFailureCompensation(
        orderId: UUID,
        validationType: String,
        failureReason: String,
        amount: Int,
        paymentId: UUID? = null,
        userId: Long? = null
    ) = coroutineScope {
        log.info("🔄 결제 검증 실패 보상 처리 시작 - orderId: {}, validationType: {}, reason: {}",
                 orderId, validationType, failureReason)

        try {
            // 보상 처리 중복 실행 방지
            val lockKey = "compensation:validation:$orderId"
            if (!tryAcquireCompensationLock(lockKey)) {
                log.warn("🚫 보상 처리 중복 실행 방지 - orderId: {}", orderId)
                return@coroutineScope
            }

            try {
                // 1. 주문 정보 조회
                val order = orderQueryService.getOrder(orderId)
                log.debug("주문 정보 조회 완료 - orderId: {}, orderNo: {}, status: {}",
                         orderId, order.orderNo, order.status)

                // 2. 결제 검증 실패 이벤트 발행 (내부 이벤트)
                val validationFailedEvent = if (paymentId != null) {
                    when (validationType) {
                        "ADDRESS" -> PaymentValidationFailedEvent.addressValidationFailed(
                            paymentId, orderId, order.orderNo, order.customerId, failureReason, amount, userId)
                        "PRICE" -> PaymentValidationFailedEvent.priceValidationFailed(
                            paymentId, orderId, order.orderNo, order.customerId, failureReason, amount, userId)
                        "ORDER_STATUS" -> PaymentValidationFailedEvent.orderStatusValidationFailed(
                            paymentId, orderId, order.orderNo, order.customerId, failureReason, amount, userId)
                        else -> PaymentValidationFailedEvent(
                            validationPaymentId = paymentId, orderId = orderId, orderNo = order.orderNo, customerId = order.customerId, validationType = validationType, failureReason = failureReason, amount = amount, eventUserId = userId)
                    }
                } else {
                    // paymentId가 없는 경우 임시 ID 생성
                    val tempPaymentId = UUID.randomUUID()
                    PaymentValidationFailedEvent(
                        validationPaymentId = tempPaymentId, orderId = orderId, orderNo = order.orderNo, customerId = order.customerId, validationType = validationType, failureReason = failureReason, amount = amount, eventUserId = userId)
                }

                // 3. Order 서비스에 보상 요청
                val orderCompensationEvent = OrderCompensationRequestedEvent.forValidationFailure(
                    orderId = orderId,
                    orderNo = order.orderNo,
                    customerId = order.customerId,
                    validationFailureReason = failureReason,
                    originalPaymentId = paymentId,
                    eventUserId = userId
                )

                // 4. 이벤트 발행 (병렬 처리)
                val validationEventJob = async {
                    paymentEventPublisher.publish(validationFailedEvent)
                    log.info("✅ 결제 검증 실패 이벤트 발행 완료 - orderId: {}", orderId)
                }

                val compensationEventJob = async {
                    paymentEventPublisher.publish(orderCompensationEvent)
                    log.info("✅ Order 보상 요청 이벤트 발행 완료 - orderId: {}", orderId)
                }

                // 모든 이벤트 발행 완료 대기
                validationEventJob.await()
                compensationEventJob.await()

                // 5. 보상 처리 상태 기록
                recordCompensationAttempt(orderId, "VALIDATION_FAILURE", validationType, failureReason)

                log.info("✅ 결제 검증 실패 보상 처리 완료 - orderId: {}, validationType: {}", orderId, validationType)

            } finally {
                releaseCompensationLock(lockKey)
            }

        } catch (e: Exception) {
            log.error("❌ 결제 검증 실패 보상 처리 실패 - orderId: {}, validationType: {}, error: {}",
                     orderId, validationType, e.message, e)

            // 보상 실패 알림
            handleCompensationFailure(orderId, "VALIDATION_FAILURE", e)
            throw PaymentException.compensationFailed("결제 검증 실패 보상 처리에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 결제 처리 실패에 대한 보상 처리
     *
     * @param paymentId 결제 ID
     * @param orderId 주문 ID
     * @param failureReason 실패 사유
     * @param failureStage 실패 단계 (APPROVAL, CONFIRMATION 등)
     * @param amount 결제 금액
     * @param userId 사용자 ID
     */
    suspend fun handlePaymentFailureCompensation(
        paymentId: UUID,
        orderId: UUID,
        failureReason: String,
        failureStage: String,
        amount: Int,
        userId: Long? = null
    ) = coroutineScope {
        log.info("🔄 결제 처리 실패 보상 처리 시작 - paymentId: {}, orderId: {}, stage: {}, reason: {}",
                 paymentId, orderId, failureStage, failureReason)

        try {
            val lockKey = "compensation:payment:$paymentId"
            if (!tryAcquireCompensationLock(lockKey)) {
                log.warn("🚫 결제 실패 보상 처리 중복 실행 방지 - paymentId: {}", paymentId)
                return@coroutineScope
            }

            try {
                // 1. 주문 정보 조회
                val order = orderQueryService.getOrder(orderId)

                // 2. Order 서비스에 보상 요청
                val orderCompensationEvent = OrderCompensationRequestedEvent.forPaymentFailure(
                    originalPaymentId = paymentId,
                    orderId = orderId,
                    orderNo = order.orderNo,
                    customerId = order.customerId,
                    paymentFailureReason = failureReason,
                    eventUserId = userId
                )

                // 3. 이벤트 발행
                paymentEventPublisher.publish(orderCompensationEvent)

                // 4. 보상 처리 상태 기록
                recordCompensationAttempt(orderId, "PAYMENT_FAILURE", failureStage, failureReason)

                log.info("✅ 결제 처리 실패 보상 처리 완료 - paymentId: {}, orderId: {}", paymentId, orderId)

            } finally {
                releaseCompensationLock(lockKey)
            }

        } catch (e: Exception) {
            log.error("❌ 결제 처리 실패 보상 처리 실패 - paymentId: {}, orderId: {}, error: {}",
                     paymentId, orderId, e.message, e)

            handleCompensationFailure(orderId, "PAYMENT_FAILURE", e)
            throw PaymentException.compensationFailed("결제 처리 실패 보상 처리에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 부분 성공 시나리오에 대한 보상 처리
     *
     * 예: 결제는 성공했지만 주문 상태 업데이트 실패, 재고 차감 실패 등
     */
    suspend fun handlePartialSuccessCompensation(
        paymentId: UUID,
        orderId: UUID,
        successfulSteps: List<String>,
        failedStep: String,
        failureReason: String,
        amount: Int,
        userId: Long? = null
    ) {
        log.info("🔄 부분 성공 보상 처리 시작 - paymentId: {}, orderId: {}, failedStep: {}",
                 paymentId, orderId, failedStep)

        try {
            val order = orderQueryService.getOrder(orderId)

            // 성공한 단계에 따라 보상 액션 결정
            val compensationActions = determineCompensationActions(successfulSteps, failedStep)

            val orderCompensationEvent = OrderCompensationRequestedEvent.forCustomCompensation(
                orderId = orderId,
                orderNo = order.orderNo,
                customerId = order.customerId,
                compensationReason = "Partial success compensation - failed at: $failedStep, reason: $failureReason",
                compensationType = "PARTIAL_FAILURE",
                actions = compensationActions,
                originalPaymentId = paymentId,
                priority = "HIGH",
                eventUserId = userId
            )

            paymentEventPublisher.publish(orderCompensationEvent)

            recordCompensationAttempt(orderId, "PARTIAL_FAILURE", failedStep, failureReason)

            log.info("✅ 부분 성공 보상 처리 완료 - paymentId: {}, orderId: {}", paymentId, orderId)

        } catch (e: Exception) {
            log.error("❌ 부분 성공 보상 처리 실패 - paymentId: {}, orderId: {}, error: {}",
                     paymentId, orderId, e.message, e)
            throw PaymentException.compensationFailed("부분 성공 보상 처리에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 보상 처리 락 획득 시도
     */
    private fun tryAcquireCompensationLock(lockKey: String): Boolean {
        return try {
            stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofMinutes(10)) == true
        } catch (e: Exception) {
            log.warn("보상 처리 락 획득 실패 - key: {}, error: {}", lockKey, e.message)
            true // Redis 문제 시 보상 흐름은 진행
        }
    }

    /**
     * 보상 처리 락 해제
     */
    private fun releaseCompensationLock(lockKey: String) {
        try {
            stringRedisTemplate.delete(lockKey)
        } catch (e: Exception) {
            log.warn("보상 처리 락 해제 실패 - key: {}, error: {}", lockKey, e.message)
        }
    }

    /**
     * 보상 시도 기록
     */
    private fun recordCompensationAttempt(
        orderId: UUID,
        compensationType: String,
        failureType: String,
        failureReason: String
    ) {
        try {
            val recordKey = "compensation:record:$orderId"
            val compensationRecord = mapOf(
                "orderId" to orderId.toString(),
                "compensationType" to compensationType,
                "failureType" to failureType,
                "failureReason" to failureReason,
                "attemptedAt" to LocalDateTime.now().toString(),
                "status" to "REQUESTED"
            )

            stringRedisTemplate.opsForHash<String, String>()
                .putAll(recordKey, compensationRecord.mapValues { it.value.toString() })
            stringRedisTemplate.expire(recordKey, Duration.ofDays(7))

            log.debug("보상 처리 기록 저장 완료 - orderId: {}, type: {}", orderId, compensationType)

        } catch (e: Exception) {
            log.warn("보상 처리 기록 저장 실패 - orderId: {}, error: {}", orderId, e.message)
        }
    }

    /**
     * 보상 처리 실패 처리
     */
    private suspend fun handleCompensationFailure(orderId: UUID, compensationType: String, error: Exception) {
        try {
            log.error("🚨 보상 처리 실패 감지 - orderId: {}, type: {}, error: {}", orderId, compensationType, error.message)

            // 보상 실패 기록
            val failureKey = "compensation:failure:$orderId"
            val failureRecord = mapOf(
                "orderId" to orderId.toString(),
                "compensationType" to compensationType,
                "errorMessage" to error.message,
                "failedAt" to LocalDateTime.now().toString(),
                "requiresManualIntervention" to "true"
            )

            stringRedisTemplate.opsForHash<String, String>()
                .putAll(failureKey, failureRecord.mapValues { it.value.toString() })
            stringRedisTemplate.expire(failureKey, Duration.ofDays(30))

            // TODO: 관리자 알림, 모니터링 시스템 연동
            // sendCriticalAlert(orderId, compensationType, error)

            log.error("🚨 보상 처리 실패 기록 완료 - 수동 개입 필요: orderId: {}", orderId)

        } catch (recordError: Exception) {
            log.error("💥 보상 실패 기록도 실패 - orderId: {}, error: {}", orderId, recordError.message)
        }
    }

    /**
     * 성공한 단계에 따른 보상 액션 결정
     */
    private fun determineCompensationActions(
        successfulSteps: List<String>,
        failedStep: String
    ): List<OrderCompensationRequestedEvent.CompensationAction> {
        val actions = mutableListOf<OrderCompensationRequestedEvent.CompensationAction>()

        // 성공한 단계에 대한 롤백 액션 추가
        if (successfulSteps.contains("PAYMENT_APPROVED")) {
            actions.add(
                OrderCompensationRequestedEvent.CompensationAction(
                    actionType = "REFUND_PAYMENT",
                    targetResource = "payment",
                    parameters = mapOf("reason" to "partial_failure_compensation")
                )
            )
        }

        if (successfulSteps.contains("STOCK_RESERVED")) {
            actions.add(
                OrderCompensationRequestedEvent.CompensationAction(
                    actionType = "RELEASE_STOCK",
                    targetResource = "stock",
                    parameters = mapOf("reason" to "partial_failure_compensation")
                )
            )
        }

        if (successfulSteps.contains("RESERVATION_CREATED")) {
            actions.add(
                OrderCompensationRequestedEvent.CompensationAction(
                    actionType = "CANCEL_RESERVATION",
                    targetResource = "reservation",
                    parameters = mapOf("reason" to "partial_failure_compensation")
                )
            )
        }

        // 주문 상태 업데이트는 항상 포함
        actions.add(
            OrderCompensationRequestedEvent.CompensationAction(
                actionType = "UPDATE_ORDER_STATUS",
                targetResource = "order",
                parameters = mapOf(
                    "newStatus" to EventConstants.EventStatus.FAILED,
                    "reason" to "partial_failure_at_$failedStep"
                )
            )
        )

        return actions
    }
}
