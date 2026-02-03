package com.popcorn.order.service.monitor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 처리 성능 모니터링 서비스
 *
 * 🎯 목표 성능 추적:
 * - 결제 토큰 생성 시간 < 100ms
 * - 결제 URL 생성 시간 < 200ms
 * - 결제 검증 시간 < 50ms
 * - 전체 결제 처리 시간 < 800ms
 */
@Service
@Slf4j
public class PaymentPerformanceMonitor {

    private final AtomicLong totalPayments = new AtomicLong(0);
    private final AtomicLong ultraFastPayments = new AtomicLong(0); // < 800ms
    private final AtomicLong slowPayments = new AtomicLong(0); // >= 800ms
    private final AtomicReference<Long> bestTime = new AtomicReference<>(Long.MAX_VALUE);
    private final AtomicReference<Long> worstTime = new AtomicReference<>(0L);

    // 세부 성능 메트릭
    private final AtomicLong tokenGenerationCount = new AtomicLong(0);
    private final AtomicLong tokenCacheHits = new AtomicLong(0);
    private final AtomicLong urlGenerationCount = new AtomicLong(0);
    private final AtomicLong urlCacheHits = new AtomicLong(0);

    /**
     * 결제 처리 성능 측정 시작
     */
    public PaymentPerformanceTracker startPaymentTracking(String orderId) {
        return new PaymentPerformanceTracker(orderId);
    }

    /**
     * 성능 통계 업데이트
     */
    public void recordPaymentPerformance(long processingTimeMs) {
        totalPayments.incrementAndGet();

        if (processingTimeMs < 800) {
            ultraFastPayments.incrementAndGet();
            log.info("🚀 ULTRA-FAST 결제 처리 성공 - {}ms", processingTimeMs);
        } else {
            slowPayments.incrementAndGet();
            log.warn("🐌 SLOW 결제 처리 - {}ms (목표 800ms 초과)", processingTimeMs);
        }

        // 최고/최악 기록 업데이트
        bestTime.updateAndGet(current -> Math.min(current, processingTimeMs));
        worstTime.updateAndGet(current -> Math.max(current, processingTimeMs));
    }

    /**
     * 토큰 생성 성능 기록
     */
    public void recordTokenGeneration(long generationTimeMs, boolean fromCache) {
        tokenGenerationCount.incrementAndGet();
        if (fromCache) {
            tokenCacheHits.incrementAndGet();
        }

        if (generationTimeMs > 100) {
            log.warn("🔐 토큰 생성 시간 초과 - {}ms (목표 100ms)", generationTimeMs);
        } else {
            log.debug("🔐 토큰 생성 완료 - {}ms", generationTimeMs);
        }
    }

    /**
     * URL 생성 성능 기록
     */
    public void recordUrlGeneration(long generationTimeMs, boolean fromCache) {
        urlGenerationCount.incrementAndGet();
        if (fromCache) {
            urlCacheHits.incrementAndGet();
        }

        if (generationTimeMs > 200) {
            log.warn("🔗 URL 생성 시간 초과 - {}ms (목표 200ms)", generationTimeMs);
        } else {
            log.debug("🔗 URL 생성 완료 - {}ms", generationTimeMs);
        }
    }

    /**
     * 현재 성능 통계 로깅
     */
    public void logPerformanceStats() {
        long total = totalPayments.get();
        long fast = ultraFastPayments.get();
        long slow = slowPayments.get();

        if (total > 0) {
            double fastPercent = (fast * 100.0) / total;
            double tokenCacheRate = tokenGenerationCount.get() > 0 ?
                (tokenCacheHits.get() * 100.0) / tokenGenerationCount.get() : 0.0;
            double urlCacheRate = urlGenerationCount.get() > 0 ?
                (urlCacheHits.get() * 100.0) / urlGenerationCount.get() : 0.0;

            log.info("📊 결제 처리 성능 통계 - 총 {}건, 고속 {}건({}%), 저속 {}건, 최고 {}ms, 최악 {}ms",
                    total, fast, String.format("%.1f", fastPercent), slow,
                    bestTime.get() == Long.MAX_VALUE ? "N/A" : bestTime.get(), worstTime.get());
            log.info("💾 결제 캐시 성능 - 토큰 캐시율 {}%, URL 캐시율 {}%",
                    String.format("%.1f", tokenCacheRate), String.format("%.1f", urlCacheRate));
        }
    }

    /**
     * 개별 결제 성능 추적기
     */
    public class PaymentPerformanceTracker {
        private final String orderId;
        private final long startTime;
        private long tokenStartTime;
        private long tokenEndTime;
        private long urlStartTime;
        private long urlEndTime;
        private long validationStartTime;
        private long validationEndTime;

        public PaymentPerformanceTracker(String orderId) {
            this.orderId = orderId;
            this.startTime = System.currentTimeMillis();
            log.debug("⏱️ 결제 성능 추적 시작 - 주문: {}", orderId);
        }

        public void markTokenGenerationStart() {
            this.tokenStartTime = System.currentTimeMillis();
        }

        public void markTokenGenerationEnd(boolean fromCache) {
            this.tokenEndTime = System.currentTimeMillis();
            long tokenTime = tokenEndTime - tokenStartTime;
            recordTokenGeneration(tokenTime, fromCache);
        }

        public void markUrlGenerationStart() {
            this.urlStartTime = System.currentTimeMillis();
        }

        public void markUrlGenerationEnd(boolean fromCache) {
            this.urlEndTime = System.currentTimeMillis();
            long urlTime = urlEndTime - urlStartTime;
            recordUrlGeneration(urlTime, fromCache);
        }

        public void markValidationStart() {
            this.validationStartTime = System.currentTimeMillis();
        }

        public void markValidationEnd() {
            this.validationEndTime = System.currentTimeMillis();
            long validationTime = validationEndTime - validationStartTime;
            if (validationTime > 50) {
                log.warn("✅ 결제 검증 시간 초과 - {}ms (목표 50ms)", validationTime);
            } else {
                log.debug("✅ 결제 검증 완료 - {}ms", validationTime);
            }
        }

        public void finish() {
            long totalTime = System.currentTimeMillis() - startTime;
            recordPaymentPerformance(totalTime);

            // 세부 성능 분석
            if (tokenEndTime > 0 && urlEndTime > 0 && validationEndTime > 0) {
                long tokenTime = tokenEndTime - tokenStartTime;
                long urlTime = urlEndTime - urlStartTime;
                long validationTime = validationEndTime - validationStartTime;
                long otherTime = totalTime - tokenTime - urlTime - validationTime;

                log.info("📈 결제 {} 성능 분석 - 총 {}ms (토큰: {}ms, URL: {}ms, 검증: {}ms, 기타: {}ms)",
                        orderId, totalTime, tokenTime, urlTime, validationTime, otherTime);

                // 성능 경고 체크
                if (totalTime > 800) {
                    log.warn("⚠️ 결제 처리 시간 초과 - 주문: {}, 시간: {}ms (목표: <800ms)", orderId, totalTime);
                }
            }
        }
    }

    /**
     * 성능 통계 조회 (모니터링 API용)
     */
    public PaymentPerformanceStats getPerformanceStats() {
        long total = totalPayments.get();
        long fast = ultraFastPayments.get();
        double fastPercent = total > 0 ? (fast * 100.0) / total : 0.0;
        double tokenCacheRate = tokenGenerationCount.get() > 0 ?
            (tokenCacheHits.get() * 100.0) / tokenGenerationCount.get() : 0.0;
        double urlCacheRate = urlGenerationCount.get() > 0 ?
            (urlCacheHits.get() * 100.0) / urlGenerationCount.get() : 0.0;

        return new PaymentPerformanceStats(
            total, fast, fastPercent,
            bestTime.get() == Long.MAX_VALUE ? 0 : bestTime.get(),
            worstTime.get(),
            tokenCacheRate, urlCacheRate
        );
    }

    /**
     * 성능 통계 DTO
     */
    public static class PaymentPerformanceStats {
        private final long totalPayments;
        private final long ultraFastPayments;
        private final double fastPercent;
        private final long bestTimeMs;
        private final long worstTimeMs;
        private final double tokenCacheRate;
        private final double urlCacheRate;

        public PaymentPerformanceStats(long totalPayments, long ultraFastPayments,
                                     double fastPercent, long bestTimeMs, long worstTimeMs,
                                     double tokenCacheRate, double urlCacheRate) {
            this.totalPayments = totalPayments;
            this.ultraFastPayments = ultraFastPayments;
            this.fastPercent = fastPercent;
            this.bestTimeMs = bestTimeMs;
            this.worstTimeMs = worstTimeMs;
            this.tokenCacheRate = tokenCacheRate;
            this.urlCacheRate = urlCacheRate;
        }

        // Getters
        public long getTotalPayments() { return totalPayments; }
        public long getUltraFastPayments() { return ultraFastPayments; }
        public double getFastPercent() { return fastPercent; }
        public long getBestTimeMs() { return bestTimeMs; }
        public long getWorstTimeMs() { return worstTimeMs; }
        public double getTokenCacheRate() { return tokenCacheRate; }
        public double getUrlCacheRate() { return urlCacheRate; }
    }
}