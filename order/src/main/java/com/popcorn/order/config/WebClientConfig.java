package com.popcorn.order.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.popcorn.order.http.EventHttpLoggingFilter;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

/**
 * 마이크로서비스 간 HTTP 통신을 위한 WebClient 설정
 *
 * [초보자 가이드]
 * - WebClient: Spring에서 제공하는 비동기 HTTP 클라이언트
 * - Netty: 비동기 네트워크 통신 라이브러리
 * - Timeout: 요청이 너무 오래 걸리면 자동으로 끊어줌
 */
@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${microservices.payment.timeout:30s}")
    private Duration paymentTimeout;
    private final EventHttpLoggingFilter eventHttpLoggingFilter;

    public WebClientConfig(EventHttpLoggingFilter eventHttpLoggingFilter) {
        this.eventHttpLoggingFilter = eventHttpLoggingFilter;
    }

    /**
     * Payment 마이크로서비스 통신용 WebClient 설정
     *
     * [설정 내용]
     * - Connection Timeout: 연결 시도 시간 제한 (10초)
     * - Read Timeout: 응답 읽기 시간 제한 (30초)
     * - Write Timeout: 요청 쓰기 시간 제한 (10초)
     * - Connection Pool: 최대 연결 수 (500개)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Netty HTTP Client 설정 (성능 최적화)
        HttpClient httpClient = HttpClient.create()
                // Connection Pool 설정
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 연결 타임아웃 10초
                .responseTimeout(paymentTimeout) // 응답 타임아웃 30초
                // Read/Write 타임아웃 설정
                .doOnConnected(conn -> conn
                    .addHandlerLast(new ReadTimeoutHandler(30))  // 읽기 타임아웃 30초
                    .addHandlerLast(new WriteTimeoutHandler(10))); // 쓰기 타임아웃 10초

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 기본 헤더 설정
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Order-Service/1.0.0")
                .defaultHeader("X-Internal-Service", "order-service")
                .defaultHeader("X-Internal-Call", "true")
                .filter(eventHttpLoggingFilter)
                // 요청/응답 로깅 (개발환경용)
                .filter((request, next) -> {
                    log.debug("HTTP 요청 - {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .doOnNext(response ->
                                log.debug("HTTP 응답 - {} {} -> {}",
                                    request.method(), request.url(), response.statusCode()));
                });
    }

    /**
     * 일반적인 마이크로서비스 통신용 WebClient
     * (다른 서비스와 통신할 때 사용)
     */
    @Bean
    public WebClient defaultWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

}
