package com.popcorn.coupon.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * 쿠폰 서비스 전용 DLQ/Retry 토픽 설정
 * - common-lib의 KafkaConfig를 사용하고, 추가 토픽만 정의
 */
@Configuration
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class CouponKafkaConfig {

    private val log = LoggerFactory.getLogger(CouponKafkaConfig::class.java)

    // === 쿠폰 전용 DLQ/Retry 토픽들 ===

    @Bean
    fun couponEventsRetryTopic(): NewTopic {
        return TopicBuilder.name("coupon-events-retry")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun couponEventsDlqTopic(): NewTopic {
        return TopicBuilder.name("coupon-events-dlq")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun couponRequestsRetryTopic(): NewTopic {
        return TopicBuilder.name("coupon-requests-retry")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun couponRequestsDlqTopic(): NewTopic {
        return TopicBuilder.name("coupon-requests-dlq")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun couponAnalyticsEventsTopic(): NewTopic {
        return TopicBuilder.name("coupon-analytics-events")
            .partitions(12)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000") // 14일 보관 (분석 데이터)
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }
}