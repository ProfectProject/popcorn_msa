package com.popcorn.order.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 처리 설정 - 성능 최적화를 위한 전용 스레드 풀들
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 외부 서비스 호출 전용 스레드 풀 (Users, Stores 서비스 등)
     */
    @Bean("externalServiceExecutor")
    public Executor externalServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);  // 외부 호출이 많으므로 크게 설정
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ExternalAPI-");
        executor.setKeepAliveSeconds(60);

        // 큐가 가득 찰 때의 정책 - 호출자 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 우아한 종료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("🔧 External Service Executor 초기화 완료 - Core: {}, Max: {}",
                 executor.getCorePoolSize(), executor.getMaxPoolSize());

        return executor;
    }

    /**
     * Redis 이벤트 처리 전용 스레드 풀
     */
    @Bean("redisEventExecutor")
    public Executor redisEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);   // Redis 이벤트 처리용
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500); // 큐를 크게 해서 이벤트 버퍼링
        executor.setThreadNamePrefix("RedisEvent-");
        executor.setKeepAliveSeconds(120);

        // 이벤트 처리는 중요하므로 CallerRuns 정책
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        log.info("🔧 Redis Event Executor 초기화 완료 - Core: {}, Max: {}",
                 executor.getCorePoolSize(), executor.getMaxPoolSize());

        return executor;
    }

    /**
     * 일반 비동기 작업용 스레드 풀 (주문 처리 등)
     */
    @Bean("orderTaskExecutor")
    public Executor orderTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("OrderTask-");
        executor.setKeepAliveSeconds(60);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("🔧 Order Task Executor 초기화 완료 - Core: {}, Max: {}",
                 executor.getCorePoolSize(), executor.getMaxPoolSize());

        return executor;
    }
}