package com.popcorn.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * ⚡ 고성능 비동기 처리를 위한 최적화된 설정
 */
@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * 🚀 고성능 Payment 서비스용 비동기 실행자
     * - 더 큰 스레드 풀로 동시성 향상
     * - 최적화된 큐 설정으로 대기시간 최소화
     */
    @Bean("asyncTaskExecutor")
    fun asyncTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()

        // 🔥 성능 최적화 설정
        executor.corePoolSize = 10        // 기본 스레드: 5→10
        executor.maxPoolSize = 50         // 최대 스레드: 20→50
        executor.queueCapacity = 200      // 큐 크기: 100→200

        // 🎯 성능 튜닝
        executor.setAllowCoreThreadTimeOut(true)
        executor.keepAliveSeconds = 120   // 유휴 스레드 유지 시간

        executor.setThreadNamePrefix("payment-turbo-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(90)

        // 거부 정책: CallerRuns (백프레셔 제어)
        executor.setRejectedExecutionHandler { runnable, _ ->
            if (!Thread.currentThread().isInterrupted) {
                runnable.run()
            }
        }

        executor.initialize()
        return executor
    }

    /**
     * 🏎️ 고속 배치 처리용 전용 실행자
     */
    @Bean("batchTaskExecutor")
    fun batchTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 20
        executor.maxPoolSize = 100
        executor.queueCapacity = 500
        executor.setThreadNamePrefix("payment-batch-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(120)
        executor.initialize()
        return executor
    }
}