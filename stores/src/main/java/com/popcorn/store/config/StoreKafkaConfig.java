package com.popcorn.store.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Slf4j
@Configuration("storeKafkaConfig")
public class StoreKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${popcorn.kafka.topics.storeEvents:store-events}")
    private String storeEventsTopic;

    @Value("${popcorn.kafka.partitions.storeEvents:12}")
    private int storeEventsPartitions;

    @Value("${popcorn.kafka.topics.storeRequests:store-requests}")
    private String storeRequestsTopic;

    @Value("${popcorn.kafka.partitions.storeRequests:12}")
    private int storeRequestsPartitions;

    @Value("${popcorn.kafka.topic.replicas:1}")
    private short replicas;

    // -------------------------
    // Producer (Store 이벤트 발행용)
    // -------------------------
    @Bean("storeProducerFactory")
    public ProducerFactory<String, String> storeProducerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 안정성 (기본값 의존 줄이기)
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("storeKafkaTemplate")
    public KafkaTemplate<String, String> storeKafkaTemplate(
            @Qualifier("storeProducerFactory") ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    // -------------------------
    // Consumer (Store가 요청 수신용)
    // -------------------------
    @Bean("storeConsumerFactory")
    public ConsumerFactory<String, String> storeConsumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);

        // 로컬에서는 earliest로 테스트 편의, 운영은 latest도 고려
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // at-least-once 전제 -> 수동 커밋
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("storeKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> storeKafkaListenerContainerFactory(
            @Qualifier("storeConsumerFactory")
            ConsumerFactory<String, String> cf
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(cf);

        // 기본 에러 핸들러 사용 (의존성 문제 방지)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * 실패 처리 정책:
     * - 재시도(지수 백오프)
     * - 최대 초과 시 <원본토픽>.DLT 로 전송
     *
     * 예) store-requests.DLT
     */
    @Bean
    public DefaultErrorHandler storeErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);

        // 재시도 의미 없는 예외는 즉시 DLT로 보내고 싶으면 여기에 추가
        // handler.addNotRetryableExceptions(IllegalArgumentException.class);

        return handler;
    }

    // -------------------------
    // Topic 생성 (선택)
    // -------------------------
    /**
     * Store가 발행하는 store-events 토픽을 "로컬에서만" 자동 생성하고 싶을 때 사용.
     * 운영에서는 보통 인프라에서 생성/관리하므로 local 프로필에만 두는 걸 추천.
     */
    @Bean
    @Profile("local")
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name(storeEventsTopic)
                .partitions(storeEventsPartitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }

    @Bean
    @Profile("local")
    public NewTopic storeRequestsTopic() {
        return TopicBuilder.name(storeRequestsTopic)
                .partitions(storeRequestsPartitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }
}
