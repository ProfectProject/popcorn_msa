package com.popcorn.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄링에 필요한 TaskScheduler를 등록합니다.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler storeTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("store-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
