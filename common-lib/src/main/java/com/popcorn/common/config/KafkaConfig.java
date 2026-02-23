package com.popcorn.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 *  Kafka 
 * -     Kafka 
 * - DLQ, Retry,  
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true")
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    @Value("${kafka.consumer.group-instance-id:}")
    private String groupInstanceId;

    // === Producer  ===

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        //   -   
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        //  
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        //  
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        log.info(" Kafka Producer   - : {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setProducerListener(kafkaProducerTraceListener());
        //     (  )
        log.info(" KafkaTemplate  ");
        return template;
    }

    @Bean
    public ProducerListener<String, Object> kafkaProducerTraceListener() {
        return new ProducerListener<>() {
            @Override
            public void onSuccess(ProducerRecord<String, Object> record, RecordMetadata metadata) {
                log.info(
                        "[KAFKA_PUBLISH_OK] topic={}, partition={}, offset={}, key={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        record.key()
                );
            }

            @Override
            public void onError(ProducerRecord<String, Object> record, RecordMetadata metadata, Exception exception) {
                log.error(
                        "[KAFKA_PUBLISH_FAIL] topic={}, partition={}, key={}, error={}",
                        record.topic(),
                        metadata != null ? metadata.partition() : null,
                        record.key(),
                        exception.getMessage(),
                        exception
                );
            }
        };
    }

    // === Consumer  ===

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        //  
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        //       static member id  ()
        if (groupInstanceId != null && !groupInstanceId.isBlank()) {
            props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, groupInstanceId);
        }

        //  
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        //  
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.popcorn.*");
        // TYPE_MAPPINGS     

        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.lang.Object");

        log.info(" Kafka Consumer   - : {}, : {}", groupId, concurrency);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        //    ( )
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        //    
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setMissingTopicsFatal(false);

        //      
        factory.getContainerProperties().setStopContainerWhenFenced(true);

        // DLQ   
        factory.setCommonErrorHandler(deadLetterErrorHandler());

        log.info(" Kafka Listener Container Factory   - : {}, DLQ ", concurrency);
        return factory;
    }

    // === DLQ   ===

    @Bean
    public DefaultErrorHandler deadLetterErrorHandler() {
        // DLQ  Recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate(),
            (record, exception) -> {
                // retry   ,  dlq  
                String dlqTopic = record.topic() + "-dlq";
                return new TopicPartition(dlqTopic, record.partition());
            });

        // Backoff : 1  3 
        FixedBackOff backOff = new FixedBackOff(60000L, 3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        //    
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class
            //   ...
        );

        log.info(" DLQ     - Backoff: 1m ,  3 ");
        return errorHandler;
    }

    // ===   ===

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(12)
                .replicas(3)  //  3   
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000") // 1
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")  //  2 
                .build();
    }

    @Bean
    public NewTopic orderRequestsTopic() {
        return TopicBuilder.name("order-requests")
                .partitions(12)
                .replicas(3)  //   
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(6)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic paymentRequestsTopic() {
        return TopicBuilder.name("payment-requests")
                .partitions(6)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name("store-events")
                .partitions(12)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic storeRequestsTopic() {
        return TopicBuilder.name("store-requests")
                .partitions(12)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic checkinEventsTopic() {
        return TopicBuilder.name("checkin-events")
                .partitions(3)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    @Bean
    public NewTopic checkinRequestsTopic() {
        return TopicBuilder.name("checkin-requests")
                .partitions(3)
                .replicas(3)  //     
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }
}
