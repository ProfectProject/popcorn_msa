package com.popcorn.payment.event.listener

import com.popcorn.payment.constants.EventConstants
import com.popcorn.payment.event.domain.compensation.CompensationCompletedEvent
import com.popcorn.payment.event.domain.compensation.CompensationFailedEvent
import com.popcorn.payment.service.PaymentCommandCoroutineService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 보상 트랜잭션 결과 이벤트 리스너
 *
 * Order 서비스에서 보상 처리가 완료되었을 때의 결과를 수신하여 처리합니다.
 */
@Component
class CompensationEventListener(
    private val paymentCommandService: PaymentCommandCoroutineService
) {

    private val log = LoggerFactory.getLogger(CompensationEventListener::class.java)

    /**
     * 보상 완료 이벤트 처리
     *
     * Order 서비스에서 보상 처리가 성공적으로 완료되었을 때 호출됩니다.
     */
    suspend fun handleCompensationCompleted(event: CompensationCompletedEvent) {
        try {
            log.info("🎉 보상 완료 이벤트 수신 - compensationId: {}, orderId: {}, result: {}",
                     event.compensationId, event.orderId, event.result)

            when {
                event.isSuccessful() -> handleSuccessfulCompensation(event)
                event.isPartialSuccess() -> handlePartialCompensation(event)
                event.isFailed() -> handleFailedCompensationResult(event)
            }

            // 보상 완료 기록
            recordCompensationResult(event.compensationId, event.result, event.completedActions, null)

            log.info("✅ 보상 완료 이벤트 처리 완료 - compensationId: {}, result: {}",
                     event.compensationId, event.result)

        } catch (e: Exception) {
            log.error("❌ 보상 완료 이벤트 처리 실패 - compensationId: {}, error: {}",
                     event.compensationId, e.message, e)
            throw e
        }
    }

    /**
     * 보상 실패 이벤트 처리
     *
     * Order 서비스에서 보상 처리가 실패했을 때 호출됩니다.
     */
    suspend fun handleCompensationFailed(event: CompensationFailedEvent) {
        try {
            log.error("💥 보상 실패 이벤트 수신 - compensationId: {}, orderId: {}, reason: {}",
                     event.compensationId, event.orderId, event.failureReason)

            // 결제 상태 업데이트 (실패 상태로)
            try {
                // 결제 정보에 보상 실패 상태 기록
                updatePaymentWithCompensationFailure(event)
            } catch (e: Exception) {
                log.error("결제 상태 업데이트 실패 - compensationId: {}, error: {}", event.compensationId, e.message)
            }

            // 재시도 가능한 경우 재시도 스케줄링
            if (event.isRetryable()) {
                scheduleCompensationRetry(event)
            }

            // 수동 개입이 필요한 경우 알림
            if (event.requiresManualIntervention()) {
                sendManualInterventionAlert(event)
            }

            // 보상 실패 기록
            recordCompensationResult(event.compensationId, EventConstants.EventStatus.FAILED, event.partiallyCompletedActions, event.failureReason)

            log.error("🚨 보상 실패 이벤트 처리 완료 - 심각도: {} - compensationId: {}",
                     event.getSeverityLevel(), event.compensationId)

        } catch (e: Exception) {
            log.error("❌ 보상 실패 이벤트 처리 실패 - compensationId: {}, error: {}",
                     event.compensationId, e.message, e)
        }
    }

    /**
     * 성공한 보상 처리
     */
    private suspend fun handleSuccessfulCompensation(event: CompensationCompletedEvent) {
        log.info("✅ 보상 성공 처리 - compensationId: {}, orderId: {}", event.compensationId, event.orderId)

        // 결제 정보 업데이트 (보상 완료 상태)
        try {
            updatePaymentWithCompensationSuccess(event)
        } catch (e: Exception) {
            log.error("결제 상태 업데이트 실패 - compensationId: {}, error: {}", event.compensationId, e.message)
        }

        // 성공 메트릭 기록
        recordCompensationMetrics(event.compensationType, EventConstants.EventStatus.SUCCESS)
    }

    /**
     * 부분 성공한 보상 처리
     */
    private suspend fun handlePartialCompensation(event: CompensationCompletedEvent) {
        log.warn("⚠️ 보상 부분 성공 처리 - compensationId: {}, orderId: {}, notes: {}",
                event.compensationId, event.orderId, event.notes)

        // 결제 정보 업데이트 (부분 보상 완료 상태)
        try {
            updatePaymentWithPartialCompensation(event)
        } catch (e: Exception) {
            log.error("결제 상태 업데이트 실패 - compensationId: {}, error: {}", event.compensationId, e.message)
        }

        // 부분 성공에 대한 추가 처리 필요할 수 있음
        handlePartialCompensationFollowUp(event)

        // 부분 성공 메트릭 기록
        recordCompensationMetrics(event.compensationType, "PARTIAL_SUCCESS")
    }

    /**
     * 실패한 보상 결과 처리
     */
    private suspend fun handleFailedCompensationResult(event: CompensationCompletedEvent) {
        log.error("💥 보상 실패 결과 처리 - compensationId: {}, orderId: {}, notes: {}",
                 event.compensationId, event.orderId, event.notes)

        // 실패 처리 로직
        try {
            updatePaymentWithCompensationFailure(event)
        } catch (e: Exception) {
            log.error("결제 상태 업데이트 실패 - compensationId: {}, error: {}", event.compensationId, e.message)
        }

        // 관리자 알림 발송
        sendCompensationFailureAlert(event)
    }

    /**
     * 결제 정보를 보상 성공 상태로 업데이트
     */
    private suspend fun updatePaymentWithCompensationSuccess(event: CompensationCompletedEvent) {
        // TODO: 결제 상태를 COMPENSATED 또는 CANCELLED로 업데이트
        log.info("결제 보상 성공 상태 업데이트 - paymentId: {}", event.paymentId)
    }

    /**
     * 결제 정보를 부분 보상 상태로 업데이트
     */
    private suspend fun updatePaymentWithPartialCompensation(event: CompensationCompletedEvent) {
        // TODO: 결제 상태를 PARTIAL_COMPENSATED로 업데이트
        log.info("결제 부분 보상 상태 업데이트 - paymentId: {}", event.paymentId)
    }

    /**
     * 결제 정보를 보상 실패 상태로 업데이트
     */
    private suspend fun updatePaymentWithCompensationFailure(event: CompensationFailedEvent) {
        // TODO: 결제 상태를 COMPENSATION_FAILED로 업데이트
        log.error("결제 보상 실패 상태 업데이트 - paymentId: {}", event.paymentId)
    }

    /**
     * 보상 완료 이벤트의 실패 케이스 처리
     */
    private suspend fun updatePaymentWithCompensationFailure(event: CompensationCompletedEvent) {
        // TODO: 결제 상태를 COMPENSATION_FAILED로 업데이트
        log.error("결제 보상 실패 상태 업데이트 - paymentId: {}", event.paymentId)
    }

    /**
     * 보상 재시도 스케줄링
     */
    private fun scheduleCompensationRetry(event: CompensationFailedEvent) {
        log.info("🔄 보상 재시도 스케줄링 - compensationId: {}, orderId: {}",
                event.compensationId, event.orderId)

        // TODO: 보상 재시도 로직 구현
        // - 지수 백오프 알고리즘 사용
        // - 최대 재시도 횟수 제한
        // - 재시도 큐에 추가
    }

    /**
     * 수동 개입 필요 알림 발송
     */
    private fun sendManualInterventionAlert(event: CompensationFailedEvent) {
        log.error("🚨 수동 개입 필요 알림 - compensationId: {}, orderId: {}, severity: {}",
                 event.compensationId, event.orderId, event.getSeverityLevel())

        // TODO: 관리자 알림 시스템 연동
        // - Slack, Teams, Email 알림
        // - 모니터링 대시보드 업데이트
        // - 긴급 알림 시스템 트리거
    }

    /**
     * 보상 실패 알림 발송
     */
    private fun sendCompensationFailureAlert(event: CompensationCompletedEvent) {
        log.error("🚨 보상 실패 알림 - compensationId: {}, orderId: {}",
                 event.compensationId, event.orderId)

        // TODO: 보상 실패 알림 발송
    }

    /**
     * 부분 성공 후속 처리
     */
    private suspend fun handlePartialCompensationFollowUp(event: CompensationCompletedEvent) {
        log.info("🔍 부분 성공 후속 처리 - compensationId: {}", event.compensationId)

        // TODO: 부분 성공한 경우의 추가 처리 로직
        // - 미완료된 액션들 재시도
        // - 우선순위에 따른 추가 보상 액션 실행
    }

    /**
     * 보상 결과 기록
     */
    private fun recordCompensationResult(
        compensationId: java.util.UUID,
        result: String,
        completedActions: List<String>,
        failureReason: String?
    ) {
        try {
        log.debug("보상 결과 기록 - compensationId: {}, result: {}, actions: {}, failureReason: {}",
                 compensationId, result, completedActions.joinToString(", "), failureReason ?: "N/A")

            // TODO: 데이터베이스나 캐시에 보상 결과 기록
            // - 보상 이력 테이블 업데이트
            // - 메트릭 시스템 업데이트
            // - 감사 로그 기록

        } catch (e: Exception) {
            log.warn("보상 결과 기록 실패 - compensationId: {}, error: {}", compensationId, e.message)
        }
    }

    /**
     * 보상 메트릭 기록
     */
    private fun recordCompensationMetrics(compensationType: String, result: String) {
        try {
            // TODO: 메트릭 시스템에 보상 결과 기록
            // - 성공/실패 카운터
            // - 보상 타입별 통계
            // - 응답 시간 측정
            log.debug("보상 메트릭 기록 - type: {}, result: {}", compensationType, result)

        } catch (e: Exception) {
            log.warn("보상 메트릭 기록 실패 - type: {}, result: {}, error: {}",
                    compensationType, result, e.message)
        }
    }
}
