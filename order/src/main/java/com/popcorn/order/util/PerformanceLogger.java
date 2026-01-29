package com.popcorn.order.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 호출 시간 측정 및 로깅 유틸리티
 *
 * 사용법:
 * - PerformanceLogger.logTime("주문 생성", () -> orderService.createOrder(...));
 * - PerformanceLogger.logTimeWithReturn("주문 조회", () -> orderService.getOrder(...));
 */
public class PerformanceLogger {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLogger.class);

    /**
     * 실행 시간을 측정하고 로그로 출력 (반환값이 없는 경우)
     *
     * @param operation 작업 설명
     * @param action 실행할 작업
     */
    public static void logTime(String operation, Runnable action) {
        long startTime = System.currentTimeMillis();
        try {
            action.run();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration > 1000) {
                log.warn("⏰ [SLOW] {} - 실행시간: {}ms", operation, duration);
            } else if (duration > 500) {
                log.info("⏰ [NORMAL] {} - 실행시간: {}ms", operation, duration);
            } else {
                log.debug("⏰ [FAST] {} - 실행시간: {}ms", operation, duration);
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("❌ [ERROR] {} - 실행시간: {}ms, 오류: {}", operation, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 실행 시간을 측정하고 로그로 출력 (반환값이 있는 경우)
     *
     * @param operation 작업 설명
     * @param supplier 실행할 작업
     * @return 작업 결과
     */
    public static <T> T logTimeWithReturn(String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        try {
            T result = supplier.get();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration > 1000) {
                log.warn("⏰ [SLOW] {} - 실행시간: {}ms", operation, duration);
            } else if (duration > 500) {
                log.info("⏰ [NORMAL] {} - 실행시간: {}ms", operation, duration);
            } else {
                log.debug("⏰ [FAST] {} - 실행시간: {}ms", operation, duration);
            }

            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("❌ [ERROR] {} - 실행시간: {}ms, 오류: {}", operation, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 상세 정보와 함께 실행 시간 측정
     */
    public static <T> T logDetailedTime(String operation, String details, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        try {
            T result = supplier.get();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration > 1000) {
                log.warn("⏰ [SLOW] {} ({}) - 실행시간: {}ms", operation, details, duration);
            } else if (duration > 500) {
                log.info("⏰ [NORMAL] {} ({}) - 실행시간: {}ms", operation, details, duration);
            } else {
                log.debug("⏰ [FAST] {} ({}) - 실행시간: {}ms", operation, details, duration);
            }

            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("❌ [ERROR] {} ({}) - 실행시간: {}ms, 오류: {}", operation, details, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 시작 시간 기록
     */
    public static long startTimer() {
        return System.currentTimeMillis();
    }

    /**
     * 종료 시간 기록 및 로깅
     */
    public static void endTimer(String operation, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        if (duration > 1000) {
            log.warn("⏰ [SLOW] {} - 실행시간: {}ms", operation, duration);
        } else if (duration > 500) {
            log.info("⏰ [NORMAL] {} - 실행시간: {}ms", operation, duration);
        } else {
            log.debug("⏰ [FAST] {} - 실행시간: {}ms", operation, duration);
        }
    }
}