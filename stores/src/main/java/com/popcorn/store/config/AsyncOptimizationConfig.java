package com.popcorn.store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 🚀 극한 성능 최적화된 비동기 처리 설정
 *
 * - 최적화된 스레드 풀로 비동기 작업 처리량 극대화
 * - 적절한 큐 크기와 reject policy로 안정성 보장
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncOptimizationConfig {

    /**
     * 🚀 Redis 비동기 작업 전용 스레드 풀
     * - registerPopupLookup 등의 비동기 Redis 작업에 사용
     */
    @Bean("redisAsyncExecutor")
    public TaskExecutor redisAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);        // 코어 스레드 수
        executor.setMaxPoolSize(16);        // 최대 스레드 수
        executor.setQueueCapacity(200);     // 큐 크기
        executor.setKeepAliveSeconds(60);   // 유휴 스레드 생존 시간
        executor.setThreadNamePrefix("redis-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 🚀 큐가 가득 찰 경우 호출 스레드에서 직접 실행 (성능 최우선)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("🚀 Redis 비동기 작업 전용 스레드 풀 생성 완료 - core: {}, max: {}, queue: {}",
                 8, 16, 200);

        return executor;
    }

    /**
     * 🚀 데이터베이스 비동기 작업 전용 스레드 풀
     * - 예약 생성 등의 비동기 DB 작업에 사용
     */
    @Bean("dbAsyncExecutor")
    public TaskExecutor dbAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);        // DB 연결 수를 고려한 코어 스레드 수
        executor.setMaxPoolSize(12);        // 최대 스레드 수
        executor.setQueueCapacity(100);     // 큐 크기 (DB 부하 고려)
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("db-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 🚀 큐가 가득 찰 경우 호출 스레드에서 직접 실행
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("🚀 DB 비동기 작업 전용 스레드 풀 생성 완료 - core: {}, max: {}, queue: {}",
                 6, 12, 100);

        return executor;
    }

    /**
     * 🚀 일반 비동기 작업 전용 스레드 풀
     */
    @Bean("generalAsyncExecutor")
    public TaskExecutor generalAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("general-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("🚀 일반 비동기 작업 전용 스레드 풀 생성 완료 - core: {}, max: {}, queue: {}",
                 4, 8, 50);

        return executor;
    }
}