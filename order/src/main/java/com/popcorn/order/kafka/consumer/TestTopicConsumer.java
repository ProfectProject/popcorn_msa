package com.popcorn.order.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TestTopicConsumer {

    @KafkaListener(
            topics = "${kafka.test-topic.name:test-topic}",
            groupId = "${kafka.test-topic.group-id:order-test-topic-cg}",
            autoStartup = "${kafka.test-topic.enabled:false}"
    )
    public void listen(String message) {
        try {
            log.info("메시지 수신: {}", message);
            // 비즈니스 로직 처리
            //String processedMessage = kafkaConsumerService.processMessage(message);
            log.info("메시지 처리 완료: {}", message);

        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
