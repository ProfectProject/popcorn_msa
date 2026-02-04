package com.popcorn.store.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 🚀 성능 모니터링 설정
 *
 * Redis 홀드 작업과 주요 서비스 메서드의 성능을 모니터링하여
 * 최적화 효과를 추적합니다.
 */
@Aspect
@Component
@Slf4j
public class PerformanceMonitoringConfig {

    @Around("execution(* com.popcorn.store.inventory.redis.InventoryRedisHoldService.hold*(..))")
    public Object monitorRedisHoldOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > 100) {
                log.warn("🐌 Redis Hold 작업 지연 - 메서드: {}, 실행시간: {}ms", methodName, executionTime);
            } else {
                log.debug("⚡ Redis Hold 작업 완료 - 메서드: {}, 실행시간: {}ms", methodName, executionTime);
            }

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ Redis Hold 작업 실패 - 메서드: {}, 실행시간: {}ms, 오류: {}",
                     methodName, executionTime, e.getMessage());
            throw e;
        }
    }

    @Around("execution(* com.popcorn.store.event.StoreRedisStreamListener.handleScheduleReservationRequested(..))")
    public Object monitorScheduleReservationProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > 600) {
                log.warn("🎯 목표 초과 - 스케줄 예약 처리시간: {}ms (목표: 600ms 이하)", executionTime);
            } else {
                log.info("🎯 목표 달성 - 스케줄 예약 처리시간: {}ms ⚡", executionTime);
            }

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ 스케줄 예약 처리 실패 - 실행시간: {}ms, 오류: {}", executionTime, e.getMessage());
            throw e;
        }
    }

    @Around("execution(* com.popcorn.store.domain.*.service.*.*(..)) && " +
            "@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitorTransactionalMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > 200) {
                log.warn("🗄️ DB 트랜잭션 지연 - {}.{}: {}ms", className, methodName, executionTime);
            }

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ DB 트랜잭션 실패 - {}.{}: {}ms, 오류: {}",
                     className, methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}