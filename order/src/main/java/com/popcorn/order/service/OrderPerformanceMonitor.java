package com.popcorn.order.service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 처리 성능 모니터링 서비스
 *
 * 🎯 목표 성능 추적:
 * - 전체 주문 처리 시간 < 1초
 * - Redis 캐시 응답 시간 < 50ms
 * - DB 트랜잭션 시간 < 200ms
 * - 이벤트 발행 시간 < 100ms
 */
@Service
@Slf4j
public class OrderPerformanceMonitor {

    private final AtomicLong totalOrders = new AtomicLong(0);
    private final AtomicLong fastOrders = new AtomicLong(0); // < 1초
    private final AtomicLong slowOrders = new AtomicLong(0); // >= 1초
    private final AtomicReference<Long> bestTime = new AtomicReference<>(Long.MAX_VALUE);
    private final AtomicReference<Long> worstTime = new AtomicReference<>(0L);

    /**
     * 주문 처리 성능 측정 시작
     */
    public PerformanceTracker startOrderTracking(String orderNo) {
        return new PerformanceTracker(orderNo);
    }

    /**
     * 성능 통계 업데이트
     */
    public void recordOrderPerformance(long processingTimeMs) {
        totalOrders.incrementAndGet();

        if (processingTimeMs < 1000) {
            fastOrders.incrementAndGet();
            log.info("🚀 ULTRA-FAST 주문 처리 성공 - {}ms", processingTimeMs);
        } else {
            slowOrders.incrementAndGet();
            log.warn("🐌 SLOW 주문 처리 - {}ms (목표 1초 초과)", processingTimeMs);
        }

        // 최고/최악 기록 업데이트
        bestTime.updateAndGet(current -> Math.min(current, processingTimeMs));
        worstTime.updateAndGet(current -> Math.max(current, processingTimeMs));
    }

    /**
     * 현재 성능 통계 로깅
     */
    public void logPerformanceStats() {
        long total = totalOrders.get();
        long fast = fastOrders.get();
        long slow = slowOrders.get();

        if (total > 0) {
            double fastPercent = (fast * 100.0) / total;
            log.info("📊 주문 처리 성능 통계 - 총 {}건, 고속 {}건({}%), 저속 {}건, 최고 {}ms, 최악 {}ms",
                    total, fast, String.format("%.1f", fastPercent), slow,
                    bestTime.get() == Long.MAX_VALUE ? "N/A" : bestTime.get(), worstTime.get());
        }
    }

    /**
     * 개별 주문 성능 추적기
     */
    public class PerformanceTracker {
        private final String orderNo;
        private final long startTime;
        private long dbStartTime;
        private long dbEndTime;
        private long cacheStartTime;
        private long cacheEndTime;
        private long eventStartTime;
        private long eventEndTime;

        public PerformanceTracker(String orderNo) {
            this.orderNo = orderNo;
            this.startTime = System.currentTimeMillis();
            log.debug("⏱️ 주문 성능 추적 시작 - {}", orderNo);
        }

        public void markDbStart() {
            this.dbStartTime = System.currentTimeMillis();
        }

        public void markDbEnd() {
            this.dbEndTime = System.currentTimeMillis();
            long dbTime = dbEndTime - dbStartTime;
            if (dbTime > 200) {
                log.warn("🗄️ DB 처리 시간 초과 - {}ms (목표 200ms)", dbTime);
            } else {
                log.debug("🗄️ DB 처리 완료 - {}ms", dbTime);
            }
        }

        public void markCacheStart() {
            this.cacheStartTime = System.currentTimeMillis();
        }

        public void markCacheEnd() {
            this.cacheEndTime = System.currentTimeMillis();
            long cacheTime = cacheEndTime - cacheStartTime;
            if (cacheTime > 50) {
                log.warn("💾 캐시 처리 시간 초과 - {}ms (목표 50ms)", cacheTime);
            } else {
                log.debug("💾 캐시 처리 완료 - {}ms", cacheTime);
            }
        }

        public void markEventStart() {
            this.eventStartTime = System.currentTimeMillis();
        }

        public void markEventEnd() {
            this.eventEndTime = System.currentTimeMillis();
            long eventTime = eventEndTime - eventStartTime;
            if (eventTime > 100) {
                log.warn("📢 이벤트 처리 시간 초과 - {}ms (목표 100ms)", eventTime);
            } else {
                log.debug("📢 이벤트 처리 완료 - {}ms", eventTime);
            }
        }

        public void finish() {
            long totalTime = System.currentTimeMillis() - startTime;
            recordOrderPerformance(totalTime);

            // 세부 성능 분석
            if (dbEndTime > 0 && cacheEndTime > 0 && eventEndTime > 0) {
                long dbTime = dbEndTime - dbStartTime;
                long cacheTime = cacheEndTime - cacheStartTime;
                long eventTime = eventEndTime - eventStartTime;
                long otherTime = totalTime - dbTime - cacheTime - eventTime;

                log.info("📈 주문 {} 성능 분석 - 총 {}ms (DB: {}ms, 캐시: {}ms, 이벤트: {}ms, 기타: {}ms)",
                        orderNo, totalTime, dbTime, cacheTime, eventTime, otherTime);
            }
        }
    }
}