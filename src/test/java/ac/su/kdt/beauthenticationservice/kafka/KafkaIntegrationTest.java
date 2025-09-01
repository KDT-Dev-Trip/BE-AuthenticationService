package ac.su.kdt.beauthenticationservice.kafka;

import ac.su.kdt.beauthenticationservice.config.IntegrationTestBase;
import ac.su.kdt.beauthenticationservice.config.TestConfig;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@DisplayName("Kafka Integration Tests")
class KafkaIntegrationTest extends IntegrationTestBase {
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private Consumer<String, Object> testConsumer;
    
    @BeforeEach
    void setUp() {
        // 테스트용 Kafka Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        ConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        testConsumer = consumerFactory.createConsumer();
    }
    
    @Test
    @DisplayName("사용자 회원가입 이벤트가 Kafka를 통해 성공적으로 전송되어야 한다")
    void userSignedUpEvent_ShouldBeSentSuccessfully() throws Exception {
        // Given
        String topic = "user.signed-up";
        testConsumer.subscribe(Collections.singletonList(topic));
        
        UserSignedUpEvent event = UserSignedUpEvent.builder()
                .userId(123L)
                .authUserId("test-user-123")
                .email("test@example.com")
                .name("Test User")
                .planType("FREE")
                .signupTimestamp(LocalDateTime.now())
                .source("AUTH0_SOCIAL")
                .build();
        
        // When
        kafkaTemplate.send(topic, String.valueOf(event.getUserId()), event).get();
        
        // Then
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo(topic);
        assertThat(record.key()).isEqualTo("123");
        
        // JSON 변환 검증
        Map<String, Object> recordValue = (Map<String, Object>) record.value();
        assertThat(recordValue.get("userId")).isEqualTo(123L);
        assertThat(recordValue.get("email")).isEqualTo("test@example.com");
        assertThat(recordValue.get("planType")).isEqualTo("FREE");
        assertThat(recordValue.get("source")).isEqualTo("AUTH0_SOCIAL");
    }
    
    @Test
    @DisplayName("사용자 로그인 이벤트가 Kafka를 통해 성공적으로 전송되어야 한다")
    void userLoggedInEvent_ShouldBeSentSuccessfully() throws Exception {
        // Given
        String topic = "user.logged-in";
        testConsumer.subscribe(Collections.singletonList(topic));
        
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .userId("test-user-456")
                .email("login@example.com")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .loginMethod("AUTH0_SOCIAL")
                .loginTimestamp(LocalDateTime.now())
                .build();
        
        // When
        kafkaTemplate.send(topic, event.getUserId(), event).get();
        
        // Then
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo(topic);
        assertThat(record.key()).isEqualTo("test-user-456");
        
        Map<String, Object> recordValue = (Map<String, Object>) record.value();
        assertThat(recordValue.get("userId")).isEqualTo("test-user-456");
        assertThat(recordValue.get("email")).isEqualTo("login@example.com");
        assertThat(recordValue.get("ipAddress")).isEqualTo("192.168.1.100");
        assertThat(recordValue.get("loginMethod")).isEqualTo("AUTH0_SOCIAL");
    }
    
    @Test
    @DisplayName("비밀번호 재설정 요청 이벤트가 Kafka를 통해 성공적으로 전송되어야 한다")
    void passwordResetRequestedEvent_ShouldBeSentSuccessfully() throws Exception {
        // Given
        String topic = "user.password-reset-requested";
        testConsumer.subscribe(Collections.singletonList(topic));
        
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .userId("test-user-789")
                .email("reset@example.com")
                .resetToken("reset-token-123456")
                .requestTimestamp(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .ipAddress("192.168.1.200")
                .build();
        
        // When
        kafkaTemplate.send(topic, event.getUserId(), event).get();
        
        // Then
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo(topic);
        assertThat(record.key()).isEqualTo("test-user-789");
        
        Map<String, Object> recordValue = (Map<String, Object>) record.value();
        assertThat(recordValue.get("userId")).isEqualTo("test-user-789");
        assertThat(recordValue.get("email")).isEqualTo("reset@example.com");
        assertThat(recordValue.get("resetToken")).isEqualTo("reset-token-123456");
        assertThat(recordValue.get("ipAddress")).isEqualTo("192.168.1.200");
    }
    
    @Test
    @DisplayName("여러 이벤트가 동시에 전송되어도 올바르게 처리되어야 한다")
    void multipleEvents_ShouldBeHandledCorrectly() throws Exception {
        // Given
        List<String> topics = Arrays.asList(
            "user.signed-up", 
            "user.logged-in", 
            "user.password-reset-requested"
        );
        testConsumer.subscribe(topics);
        
        UserSignedUpEvent signupEvent = UserSignedUpEvent.builder()
                .userId(1L)
                .authUserId("user-1")
                .email("user1@example.com")
                .name("User 1")
                .planType("FREE")
                .source("AUTH0_SOCIAL")
                .signupTimestamp(LocalDateTime.now())
                .build();
        
        UserLoggedInEvent loginEvent = UserLoggedInEvent.builder()
                .userId("user-2")
                .email("user2@example.com")
                .loginMethod("AUTH0_SOCIAL")
                .loginTimestamp(LocalDateTime.now())
                .build();
        
        PasswordResetRequestedEvent resetEvent = PasswordResetRequestedEvent.builder()
                .userId("user-3")
                .email("user3@example.com")
                .resetToken("token-123")
                .requestTimestamp(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        
        // When
        kafkaTemplate.send("user.signed-up", String.valueOf(signupEvent.getUserId()), signupEvent).get();
        kafkaTemplate.send("user.logged-in", loginEvent.getUserId(), loginEvent).get();
        kafkaTemplate.send("user.password-reset-requested", resetEvent.getUserId(), resetEvent).get();
        
        // Then
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(15));
        assertThat(records).hasSize(3);
        
        Set<String> receivedTopics = new HashSet<>();
        Set<String> receivedUserIds = new HashSet<>();
        
        for (ConsumerRecord<String, Object> record : records) {
            receivedTopics.add(record.topic());
            receivedUserIds.add(record.key());
        }
        
        assertThat(receivedTopics).containsExactlyInAnyOrder(
            "user.signed-up", 
            "user.logged-in", 
            "user.password-reset-requested"
        );
        assertThat(receivedUserIds).containsExactlyInAnyOrder("123", "user-2", "user-3");
    }
    
    @Test
    @DisplayName("잘못된 이벤트 데이터 전송 시에도 시스템이 안정적으로 동작해야 한다")
    void malformedEvent_ShouldNotCrashSystem() throws Exception {
        // Given
        String topic = "user.signed-up";
        testConsumer.subscribe(Collections.singletonList(topic));
        
        Map<String, Object> malformedEvent = Map.of(
            "invalidField", "invalidValue",
            "missingRequiredFields", true
        );
        
        // When & Then - should not throw exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            kafkaTemplate.send(topic, "malformed-key", malformedEvent).get();
        });
        
        // 이벤트가 전송은 되어야 함
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(5));
        assertThat(records).isNotEmpty();
    }
}