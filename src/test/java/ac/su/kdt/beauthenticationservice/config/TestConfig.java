package ac.su.kdt.beauthenticationservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;

@TestConfiguration
@Profile("test")
public class TestConfig {
    
    @Bean
    @Primary
    public MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    @Primary
    public KafkaTemplate<String, Object> testKafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }
    
    @Bean
    @Primary
    public ProducerFactory<String, Object> testProducerFactory() {
        return Mockito.mock(ProducerFactory.class);
    }
    
    @Bean
    @Primary
    public RedisConnectionFactory testRedisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}