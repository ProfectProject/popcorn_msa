package com.popcorn.store.event.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.store.event.StoreRedisStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Kafka store-requests 토픽 메시지를 받아 기존 Redis Stream 처리를 재사용합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreRequestKafkaListener {

    private final ObjectMapper objectMapper;
    private final StoreRedisStreamListener storeRedisStreamListener;

    @Value("${popcorn.kafka.topics.storeRequests:store-requests}")
    private String storeRequestsTopic;

    @KafkaListener(
            topics = "${popcorn.kafka.topics.storeRequests:store-requests}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            log.debug("⚠️ [KafkaStoreRequests] 빈 메시지 수신, 무시");
            return;
        }

        try {
            Map<String, Object> envelope = objectMapper.readValue(rawMessage, new TypeReference<>() {});
            String eventType = asString(envelope.get("eventType"));
            if (!StringUtils.hasText(eventType)) {
                log.warn("⚠️ [KafkaStoreRequests] eventType 누락 - message={}", rawMessage);
                return;
            }
            log.debug("📥 [KafkaStoreRequests] topic={} eventType={}", storeRequestsTopic, eventType);
            storeRedisStreamListener.handleIncomingEvent(eventType, envelope, "store-requests topic");
        } catch (Exception e) {
            log.error("🚨 [KafkaStoreRequests] 메시지 처리 실패 - rawMessage={}", rawMessage, e);
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString().replace("\"", "").trim();
    }
}
