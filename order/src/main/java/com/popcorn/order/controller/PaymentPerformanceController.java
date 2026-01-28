package com.popcorn.order.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.popcorn.order.service.PaymentCacheService;
import com.popcorn.order.service.PaymentPerformanceMonitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 성능 모니터링 API
 *
 * 🎯 결제 시스템 성능 추적 및 최적화 정보 제공
 */
@RestController
@RequestMapping("/api/internal/payment-performance")
@RequiredArgsConstructor
@Slf4j
public class PaymentPerformanceController {

    private final PaymentPerformanceMonitor paymentPerformanceMonitor;
    private final PaymentCacheService paymentCacheService;

    /**
     * 결제 성능 통계 조회
     *
     * @return 결제 처리 성능 통계
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getPaymentPerformanceStats() {
        try {
            PaymentPerformanceMonitor.PaymentPerformanceStats stats =
                paymentPerformanceMonitor.getPerformanceStats();

            PaymentPerformanceResponse response = PaymentPerformanceResponse.builder()
                    .totalPayments(stats.getTotalPayments())
                    .ultraFastPayments(stats.getUltraFastPayments())
                    .fastPercent(stats.getFastPercent())
                    .bestTimeMs(stats.getBestTimeMs())
                    .worstTimeMs(stats.getWorstTimeMs())
                    .tokenCacheRate(stats.getTokenCacheRate())
                    .urlCacheRate(stats.getUrlCacheRate())
                    .targetTimeMs(800) // 목표 시간
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 결제 성능 통계 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("결제 성능 통계 조회에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 결제 캐시 통계 조회
     *
     * @return 결제 캐시 통계
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<?> getPaymentCacheStats() {
        try {
            PaymentCacheService.PaymentCacheStats cacheStats =
                paymentCacheService.getPaymentCacheStats();

            PaymentCacheStatsResponse response = PaymentCacheStatsResponse.builder()
                    .tokenCacheCount(cacheStats.getTokenCacheCount())
                    .urlCacheCount(cacheStats.getUrlCacheCount())
                    .statusCacheCount(cacheStats.getStatusCacheCount())
                    .customerInfoCacheCount(cacheStats.getCustomerInfoCacheCount())
                    .totalCacheCount(cacheStats.getTotalCacheCount())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 결제 캐시 통계 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("결제 캐시 통계 조회에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 결제 성능 현황 요약 (대시보드용)
     *
     * @return 결제 성능 요약 정보
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getPaymentPerformanceSummary() {
        try {
            PaymentPerformanceMonitor.PaymentPerformanceStats stats =
                paymentPerformanceMonitor.getPerformanceStats();
            PaymentCacheService.PaymentCacheStats cacheStats =
                paymentCacheService.getPaymentCacheStats();

            PaymentPerformanceSummary summary = PaymentPerformanceSummary.builder()
                    .totalPayments(stats.getTotalPayments())
                    .performanceGrade(calculatePerformanceGrade(stats.getFastPercent()))
                    .fastPercent(stats.getFastPercent())
                    .averageTimeMs(calculateAverageTime(stats))
                    .cacheEfficiency(calculateCacheEfficiency(stats))
                    .totalCacheEntries(cacheStats.getTotalCacheCount())
                    .status(determineSystemStatus(stats))
                    .recommendations(generateRecommendations(stats, cacheStats))
                    .build();

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("❌ 결제 성능 요약 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("결제 성능 요약 조회에 실패했습니다: " + e.getMessage());
        }
    }

    // ======== 내부 유틸리티 메서드 ========

    private String calculatePerformanceGrade(double fastPercent) {
        if (fastPercent >= 95) return "A+";
        if (fastPercent >= 90) return "A";
        if (fastPercent >= 80) return "B+";
        if (fastPercent >= 70) return "B";
        if (fastPercent >= 60) return "C+";
        if (fastPercent >= 50) return "C";
        return "D";
    }

    private long calculateAverageTime(PaymentPerformanceMonitor.PaymentPerformanceStats stats) {
        if (stats.getTotalPayments() == 0) return 0;
        // 간단한 추정: (최고시간 + 최악시간) / 2
        return (stats.getBestTimeMs() + stats.getWorstTimeMs()) / 2;
    }

    private double calculateCacheEfficiency(PaymentPerformanceMonitor.PaymentPerformanceStats stats) {
        return (stats.getTokenCacheRate() + stats.getUrlCacheRate()) / 2.0;
    }

    private String determineSystemStatus(PaymentPerformanceMonitor.PaymentPerformanceStats stats) {
        if (stats.getFastPercent() >= 90) return "EXCELLENT";
        if (stats.getFastPercent() >= 80) return "GOOD";
        if (stats.getFastPercent() >= 70) return "WARNING";
        return "CRITICAL";
    }

    private String generateRecommendations(
            PaymentPerformanceMonitor.PaymentPerformanceStats stats,
            PaymentCacheService.PaymentCacheStats cacheStats) {

        StringBuilder recommendations = new StringBuilder();

        if (stats.getFastPercent() < 80) {
            recommendations.append("결제 처리 성능이 목표에 미달합니다. ");
        }

        if (stats.getTokenCacheRate() < 70) {
            recommendations.append("토큰 캐시 적중률이 낮습니다. TTL 조정을 고려하세요. ");
        }

        if (stats.getUrlCacheRate() < 70) {
            recommendations.append("URL 캐시 적중률이 낮습니다. 캐시 전략을 검토하세요. ");
        }

        if (cacheStats.getTotalCacheCount() > 10000) {
            recommendations.append("캐시 크기가 큽니다. 메모리 사용량을 모니터링하세요. ");
        }

        return recommendations.length() > 0 ?
                recommendations.toString() : "시스템이 최적 상태로 운영되고 있습니다.";
    }

    // ======== Response DTOs ========

    @lombok.Builder
    @lombok.Getter
    public static class PaymentPerformanceResponse {
        private long totalPayments;
        private long ultraFastPayments;
        private double fastPercent;
        private long bestTimeMs;
        private long worstTimeMs;
        private double tokenCacheRate;
        private double urlCacheRate;
        private long targetTimeMs;
    }

    @lombok.Builder
    @lombok.Getter
    public static class PaymentCacheStatsResponse {
        private long tokenCacheCount;
        private long urlCacheCount;
        private long statusCacheCount;
        private long customerInfoCacheCount;
        private long totalCacheCount;
    }

    @lombok.Builder
    @lombok.Getter
    public static class PaymentPerformanceSummary {
        private long totalPayments;
        private String performanceGrade;
        private double fastPercent;
        private long averageTimeMs;
        private double cacheEfficiency;
        private long totalCacheEntries;
        private String status;
        private String recommendations;
    }
}