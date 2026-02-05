/*package com.popcorn.order.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TestProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private String testTopicName = "order-events";
    
    public void sendMessage(String msg) {
        kafkaTemplate.send(testTopicName, msg);
    }
}*/
