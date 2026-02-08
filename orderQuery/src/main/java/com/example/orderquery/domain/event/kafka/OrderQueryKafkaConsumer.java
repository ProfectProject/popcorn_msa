package com.example.orderquery.domain.event.kafka;

import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderQueryKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final OrderEventProcessor orderEventProcessor;

    @KafkaListener(
            topics = {
                    "${popcorn.kafka.topics.order-events:order-events}",
                    "${popcorn.kafka.topics.payment-events:payment-events}",
                    "${popcorn.kafka.topics.store-events:store-events}",
                    "${popcorn.kafka.topics.checkin-events:checkin-events}"
            },
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String rawMessage, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (!StringUtils.hasText(rawMessage)) {
            log.debug("Ignoring empty Kafka message from topic {}", topic);
            return;
        }

        try {
            Map<String, Object> envelope = objectMapper.readValue(rawMessage, new TypeReference<>() {});
            orderEventProcessor.processEvent(topic, envelope);
        } catch (Exception e) {
            log.error("Failed to handle Kafka message from topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
