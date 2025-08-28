package ac.su.kdt.beauthenticationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
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