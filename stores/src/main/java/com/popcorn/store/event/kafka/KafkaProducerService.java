package com.popcorn.store.event.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka에 JSON Payload를 전달하는 공통 유틸리티.
 * 입력 객체를 Jackson으로 직렬화한 뒤, 지정된 토픽으로 보내고 결과를 로깅한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 주어진 토픽으로 JSON 메시지를 발행한다.
     *
     * @param topic   전송 대상 토픽 이름
     * @param key     파티셔닝 키 (없을 경우 eventId를 사용)
     * @param payload 직렬화할 데이터 객체
     */
    public void publish(String topic, String key, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);

            kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("❌ Kafka 메시지 발행 실패 - topic={}, key={}, error={}", topic, key, ex.getMessage(), ex);
                } else if (result != null) {
                    log.info("🎯 Kafka 메시지 발행 성공 - topic={}, key={}, partition={}, offset={}",
                            topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("🚨 Kafka 메시지 직렬화 실패 - topic={}, key={}, error={}", topic, key, e.getMessage(), e);
        }
    }

}
