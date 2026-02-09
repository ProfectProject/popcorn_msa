package com.example.orderquery.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
public class KafkaConfig {
    // Kafka listener infrastructure is configured via Spring Boot auto-configuration.
}
