package com.popcorn.payment.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * Payment 서비스 전용 DLQ/Retry 토픽 설정
 * - common-lib의 KafkaConfig를 사용하고, 추가 토픽만 정의
 */
@Configuration
@ConditionalOnProperty(value = ["kafka.enabled"], havingValue = "true")
class PaymentKafkaConfig {

    private val log = LoggerFactory.getLogger(PaymentKafkaConfig::class.java)

    // === Payment 전용 DLQ/Retry 토픽들 ===

    @Bean
    fun paymentEventsRetryTopic(): NewTopic {
        return TopicBuilder.name("payment-events-retry")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun paymentEventsDlqTopic(): NewTopic {
        return TopicBuilder.name("payment-events-dlq")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun paymentRequestsRetryTopic(): NewTopic {
        return TopicBuilder.name("payment-requests-retry")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun paymentRequestsDlqTopic(): NewTopic {
        return TopicBuilder.name("payment-requests-dlq")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }

    @Bean
    fun orderRequestsDlqTopic(): NewTopic {
        return TopicBuilder.name("order-requests-dlq")
            .partitions(12)
            .replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") // 30일 보관
            .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            .build()
    }
}
