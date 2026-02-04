package com.popcorn.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;

import java.time.Duration;

/**
 * 서킷 브레이커 설정
 *
 * 외부 서비스 호출에 대한 장애 격리 및 자동 복구를 담당합니다.
 * MSA 환경에서 서비스 간 장애 전파를 방지하고 시스템 안정성을 향상시킵니다.
 */
@Configuration
public class OrderResilienceConfig {

    /**
     * 기본 서킷 브레이커 설정
     *
     * 서킷 브레이커 3가지 상태:
     * - CLOSED: 정상 상태, 요청이 정상적으로 처리됨
     * - OPEN: 장애 상태, 모든 요청이 즉시 실패 응답 반환
     * - HALF_OPEN: 복구 시도 상태, 일부 요청을 통해 서비스 상태 확인
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                // 서킷 브레이커 기본 설정
                .circuitBreakerConfig(getDefaultCircuitBreakerConfig())
                // 타임아웃 기본 설정
                .timeLimiterConfig(getDefaultTimeLimiterConfig())
                .build());
    }

    /**
     * 결제 서비스 전용 서킷 브레이커 설정 (🚀 극한 성능 최적화)
     *
     * 결제 서비스는 중요도가 높아 더 엄격한 설정을 적용:
     * - 초고속 장애 감지 (3회 실패시)
     * - 극한 타임아웃 (5초 → 800ms, 84% 단축)
     * - 짧은 복구 대기 시간 (30초 → 15초)
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> paymentServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(8)               // 최근 8회 요청 기준으로 판단 (빠른 감지)
                    .failureRateThreshold(40.0f)        // 40% 실패시 OPEN (더 민감한 감지)
                    .minimumNumberOfCalls(3)             // 최소 3회 호출 후 통계 시작 (빠른 반응)
                    .waitDurationInOpenState(Duration.ofSeconds(15))  // 15초 후 HALF_OPEN (빠른 복구)
                    .permittedNumberOfCallsInHalfOpenState(2)         // HALF_OPEN에서 2회 테스트 (빠른 판단)
                    .slowCallRateThreshold(70.0f)       // 70% 느린 호출시 OPEN (더 민감)
                    .slowCallDurationThreshold(Duration.ofMillis(500)) // 500ms 이상이 느린 호출 (극한 기준)
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(800))  // 🚀 5초 → 800ms (84% 단축) - 극한 성능 최적화
                    .build())
                .build(), "payment-service");
    }

    /**
     * 사용자 서비스 전용 서킷 브레이커 설정
     *
     * 사용자 정보는 캐시 가능하므로 상대적으로 관대한 설정:
     * - 느린 장애 감지 (10회 실패시)
     * - 긴 복구 대기 시간 (60초)
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> userServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(20)               // 최근 20회 요청 기준
                    .failureRateThreshold(60.0f)         // 60% 실패시 OPEN
                    .minimumNumberOfCalls(10)            // 최소 10회 호출 후 통계
                    .waitDurationInOpenState(Duration.ofSeconds(60))  // 60초 후 HALF_OPEN
                    .permittedNumberOfCallsInHalfOpenState(5)         // HALF_OPEN에서 5회 테스트
                    .slowCallRateThreshold(70.0f)        // 70% 느린 호출시 OPEN
                    .slowCallDurationThreshold(Duration.ofSeconds(2)) // 2초 이상이 느린 호출
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(3))  // 3초 타임아웃
                    .build())
                .build(), "user-service");
    }

    /**
     * 스토어 서비스 전용 서킷 브레이커 설정
     *
     * 가격 조회는 성능이 중요하므로 빠른 감지 및 복구:
     * - 빠른 장애 감지 (5회 실패시)
     * - 빠른 타임아웃 (2초)
     * - 빠른 복구 시도 (30초)
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> storeServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)               // 최근 10회 요청 기준
                    .failureRateThreshold(50.0f)         // 50% 실패시 OPEN
                    .minimumNumberOfCalls(5)             // 최소 5회 호출 후 통계
                    .waitDurationInOpenState(Duration.ofSeconds(30))  // 30초 후 HALF_OPEN
                    .permittedNumberOfCallsInHalfOpenState(3)         // HALF_OPEN에서 3회 테스트
                    .slowCallRateThreshold(70.0f)        // 70% 느린 호출시 OPEN
                    .slowCallDurationThreshold(Duration.ofMillis(1500)) // 1.5초 이상이 느린 호출
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(2))  // 2초 타임아웃
                    .build())
                .build(), "store-service");
    }

    /**
     * 재고 서비스 전용 서킷 브레이커 설정
     *
     * 재고는 정확성이 중요하므로 신중한 설정:
     * - 중간 수준의 장애 감지
     * - 충분한 복구 대기 시간
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> inventoryServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(15)               // 최근 15회 요청 기준
                    .failureRateThreshold(55.0f)         // 55% 실패시 OPEN
                    .minimumNumberOfCalls(8)             // 최소 8회 호출 후 통계
                    .waitDurationInOpenState(Duration.ofSeconds(45))  // 45초 후 HALF_OPEN
                    .permittedNumberOfCallsInHalfOpenState(4)         // HALF_OPEN에서 4회 테스트
                    .slowCallRateThreshold(75.0f)        // 75% 느린 호출시 OPEN
                    .slowCallDurationThreshold(Duration.ofSeconds(2)) // 2초 이상이 느린 호출
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(4))  // 4초 타임아웃
                    .build())
                .build(), "inventory-service");
    }

    // ========================= 내부 설정 메서드 =========================

    /**
     * 기본 서킷 브레이커 설정
     */
    private CircuitBreakerConfig getDefaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(100)                    // 최근 100회 요청으로 통계 계산
                .failureRateThreshold(50.0f)               // 50% 실패시 OPEN 상태로 전환
                .minimumNumberOfCalls(10)                  // 최소 10회 호출 후부터 통계 적용
                .waitDurationInOpenState(Duration.ofSeconds(60))    // OPEN 상태에서 60초 후 HALF_OPEN 시도
                .permittedNumberOfCallsInHalfOpenState(10)          // HALF_OPEN에서 10회 호출로 상태 확인
                .slowCallRateThreshold(60.0f)              // 60% 이상이 느린 호출이면 OPEN
                .slowCallDurationThreshold(Duration.ofSeconds(3))  // 3초 이상 걸리는 호출을 느린 호출로 판단
                .enableAutomaticTransitionFromOpenToHalfOpen()      // 자동으로 HALF_OPEN 전환
                .build();
    }

    /**
     * 기본 재시도 설정 (임시 주석 처리 - API 호환성 문제)
     */
    /*
    private RetryConfig getDefaultRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)                           // 최대 3회 재시도
                .waitDuration(Duration.ofSeconds(1))      // 재시도 간격 1초
                .retryOnException(throwable ->
                    throwable instanceof java.net.ConnectException ||      // 연결 실패
                    throwable instanceof java.net.SocketTimeoutException   // 타임아웃
                )
                .build();
    }
    */

    /**
     * 기본 타임아웃 설정
     */
    private TimeLimiterConfig getDefaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))  // 기본 10초 타임아웃
                .cancelRunningFuture(true)                // 실행 중인 작업 취소
                .build();
    }

}