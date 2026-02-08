/*package com.popcorn.order.kafka.consumer;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "store-events",
            groupId = "order-reservation-cg"
    )
    public void consumeStoreEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment ack
    ) {
        try {
            log.info("📥 [STORE-EVENT-TEST] raw 수신");
            log.info("  ▶ topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            log.info("  ▶ key={}", record.key());
            log.info("  ▶ value={}", record.value());

            // JSON 파싱만 해보기
            Map<String, Object> payload =
                    objectMapper.readValue(record.value(), Map.class);

            String eventType = String.valueOf(payload.get("eventType"))
                    .replace("\"", "")
                    .trim();

            log.info("✅ [STORE-EVENT-TEST] eventType={}", eventType);

            // 여기서는 아무 처리 안 함
            ack.acknowledge();

        } catch (Exception e) {
            log.error("🚨 [STORE-EVENT-TEST] 소비 실패", e);

            // ❗ 테스트 단계에서는 ack 해서 offset 고정 방지
            ack.acknowledge();
        }
    }
}*/

