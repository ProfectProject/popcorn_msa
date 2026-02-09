package com.popcorn.payment.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

import com.popcorn.payment.http.EventHttpLoggingFilter

/**
 * WebClient 설정 클래스 (Non-blocking HTTP 클라이언트)
 *
 * 🌐 RestTemplate vs WebClient:
 * - RestTemplate: Blocking I/O, 스레드당 하나의 요청
 * - WebClient: Non-blocking I/O, 적은 스레드로 많은 요청 처리
 *
 * ⚡ 성능 개선:
 * - CPU 코어 수만큼의 스레드로 수천 개 동시 요청 처리
 * - 메모리 사용량 크게 절약 (스레드 스택 메모리 → 힙 메모리)
 * - 응답 시간 개선 (네트워크 대기 시간 중 다른 요청 처리)
 */
@Configuration
class WebClientConfig(
    private val eventHttpLoggingFilter: EventHttpLoggingFilter
) {

    /**
     * 토스페이먼츠 전용 WebClient 빈 생성
     *
     * 🔧 최적화 설정:
     * - 커넥션 풀: 기본값 사용 (500개 최대 커넥션)
     * - 타임아웃: 연결 3초, 읽기 5초, 쓰기 3초
     * - 메모리: 응답 버퍼 1MB 제한
     */
    @Bean
    fun tossPaymentsWebClient(
        properties: TossPaymentsProperties
    ): WebClient {

        // 🚀 고성능 최적화된 Reactor Netty HTTP 클라이언트 설정
        val connectionProvider = reactor.netty.resources.ConnectionProvider.builder("payment-pool")
            .maxConnections(100)              // 최대 연결 수: 기본 500 → 100 (충분함)
            .maxIdleTime(Duration.ofSeconds(30))    // 유휴 연결 유지: 30초
            .maxLifeTime(Duration.ofMinutes(5))     // 연결 최대 생명: 5분
            .pendingAcquireMaxCount(50)            // 대기 중인 요청: 50개
            .evictInBackground(Duration.ofSeconds(30)) // 백그라운드 정리: 30초
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            // 🔥 연결 풀 최적화 (성능 극대화)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000) // 2초로 단축 (3→2)
            .option(ChannelOption.SO_KEEPALIVE, true)           // Keep-Alive 활성화
            .option(ChannelOption.TCP_NODELAY, true)           // TCP Nagle 비활성화 (지연 최소화)

            // ⚡ 타임아웃 최적화
            .responseTimeout(Duration.ofSeconds(4))             // 4초로 단축 (5→4)

            .doOnConnected { connection ->
                connection.addHandlerLast(ReadTimeoutHandler(4, TimeUnit.SECONDS))  // 4초로 단축 (5→4)
                    .addHandlerLast(WriteTimeoutHandler(2, TimeUnit.SECONDS)) // 2초로 단축 (3→2)
            }

        return WebClient.builder()
            // 🏠 Base URL 설정 (예: https://api.tosspayments.com)
            .baseUrl(properties.baseUrl)

            // 🌐 Reactor Netty 클라이언트 적용
            .clientConnector(ReactorClientHttpConnector(httpClient))

            // 📦 메모리 사용량 제한 (1MB)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            }

            // 🔧 기본 헤더 설정 (필요시)
            .defaultHeaders { headers ->
                headers.add("User-Agent", "Popcorn-Payment-Service/1.0")
                headers.add("Accept", "application/json")
                headers.add("Content-Type", "application/json")
            }
            .filter(eventHttpLoggingFilter)
            .build()

        // 💡 이렇게 설정된 WebClient의 장점:
        // 1. 커넥션 풀링으로 TCP 연결 재사용
        // 2. 비동기 처리로 높은 처리량
        // 3. 적절한 타임아웃으로 장애 상황 대응
        // 4. 메모리 사용량 제한으로 안정성 확보
    }

    /**
     * 공통 WebClient (다른 외부 API용)
     */
    @Bean
    @Primary
    fun defaultWebClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) // 2MB
            }
            .filter(eventHttpLoggingFilter)
            .build()
    }
}

/**
 * 토스페이먼츠 API 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "payment.toss")
data class TossPaymentsProperties(
    /**
     * 토스페이먼츠 API Base URL
     * - 개발환경: https://api.tosspayments.com
     * - 테스트환경: https://api.tosspayments.com (동일, 시크릿 키로 구분)
     */
    val baseUrl: String = "https://api.tosspayments.com",

    /**
     * 토스페이먼츠 시크릿 키
     * - 테스트: test_sk_...로 시작
     * - 라이브: live_sk_...로 시작
     */
    val secretKey: String,

    /**
     * 클라이언트 키 (프론트엔드에서 사용)
     */
    val clientKey: String? = null,

    /**
     * API 타임아웃 설정
     */
    val timeout: TimeoutProperties = TimeoutProperties()
)

/**
 * 타임아웃 설정
 */
data class TimeoutProperties(
    val connect: Duration = Duration.ofSeconds(3),
    val read: Duration = Duration.ofSeconds(5),
    val write: Duration = Duration.ofSeconds(3)
)
