package ac.su.kdt.beauthenticationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Producer 성능 최적화 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        /**
         * 🚨 MSA 환경에서 Kafka 역직렬화 문제 해결
         * 
         * [문제 원인]
         * - 다른 서비스(Mission, Payment 등)에서 보낸 이벤트 클래스가 Auth 서비스에 존재하지 않음
         * - ClassNotFoundException: ac.su.kdt.bemissionmanagementservice.event.dto.MissionCreatedEvent
         * 
         * [해결 방법]
         * 1. USE_TYPE_INFO_HEADERS를 false로 설정하여 타입 정보를 무시
         * 2. 이벤트를 Map<String, Object>로 받아서 처리 (타입 독립적 접근)
         * 3. 필요한 필드만 추출하여 사용
         * 
         * [장점]
         * - 서비스 간 클래스 의존성 제거
         * - 이벤트 스키마 변경에 유연하게 대응
         * - MSA 원칙(느슨한 결합) 준수
         */
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);  // 타입 헤더 무시
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Map.class);  // 기본 타입을 Map으로 설정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");  // 모든 패키지 신뢰 (타입 무관)
        
        // Consumer 설정
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // 에러 처리 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(1);
        
        return factory;
    }
    
    // MSA 공유 Kafka 토픽 설정 (서비스명 prefix 추가)
    @Bean
    public NewTopic userSignedUpTopic() {
        return TopicBuilder.name("auth.user-signed-up")  // MSA 네이밍: {service}.{event}
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic userLoggedInTopic() {
        return TopicBuilder.name("auth.user-logged-in")  // MSA 네이밍: {service}.{event}
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic passwordResetRequestedTopic() {
        return TopicBuilder.name("auth.password-reset-requested")  // MSA 네이밍: {service}.{event}
                .partitions(1)
                .replicas(1)
                .build();
    }
    
    // 기존 토픽명 (호환성 유지용) - 필요시 주석 해제
    /*
    @Bean
    public NewTopic legacyUserSignedUpTopic() {
        return TopicBuilder.name("user.signed-up")
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean 
    public NewTopic legacyUserLoggedInTopic() {
        return TopicBuilder.name("user.logged-in")
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic legacyPasswordResetTopic() {
        return TopicBuilder.name("user.password-reset-requested")
                .partitions(1)
                .replicas(1)
                .build();
    }
    */
}