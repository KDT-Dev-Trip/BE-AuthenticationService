package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Event Publisher Tests")
class EventPublisherTest {
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Mock
    private SendResult<String, Object> sendResult;
    
    @Captor
    private ArgumentCaptor<String> topicCaptor;
    
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    
    @Captor
    private ArgumentCaptor<Object> valueCaptor;
    
    private EventPublisher eventPublisher;
    
    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(kafkaTemplate);
    }
    
    @Test
    @DisplayName("사용자 회원가입 이벤트를 성공적으로 발행해야 한다")
    void publishUserSignedUpEvent_ShouldSendEventSuccessfully() {
        // Given
        UserSignedUpEvent event = UserSignedUpEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .name("Test User")
                .auth0UserId("auth0|123")
                .planType("FREE")
                .signupTimestamp(LocalDateTime.now())
                .source("AUTH0_SOCIAL")
                .build();
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
        
        // When
        eventPublisher.publishUserSignedUpEvent(event);
        
        // Then
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("user.signed-up");
        assertThat(keyCaptor.getValue()).isEqualTo("user-123");
        assertThat(valueCaptor.getValue()).isEqualTo(event);
    }
    
    @Test
    @DisplayName("사용자 로그인 이벤트를 성공적으로 발행해야 한다")
    void publishUserLoggedInEvent_ShouldSendEventSuccessfully() {
        // Given
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .auth0UserId("auth0|123")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .loginMethod("AUTH0_SOCIAL")
                .loginTimestamp(LocalDateTime.now())
                .build();
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
        
        // When
        eventPublisher.publishUserLoggedInEvent(event);
        
        // Then
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("user.logged-in");
        assertThat(keyCaptor.getValue()).isEqualTo("user-123");
        assertThat(valueCaptor.getValue()).isEqualTo(event);
    }
    
    @Test
    @DisplayName("비밀번호 재설정 요청 이벤트를 성공적으로 발행해야 한다")
    void publishPasswordResetRequestedEvent_ShouldSendEventSuccessfully() {
        // Given
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .resetToken("reset-token-123")
                .requestTimestamp(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .ipAddress("192.168.1.100")
                .build();
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
        
        // When
        eventPublisher.publishPasswordResetRequestedEvent(event);
        
        // Then
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("user.password-reset-requested");
        assertThat(keyCaptor.getValue()).isEqualTo("user-123");
        assertThat(valueCaptor.getValue()).isEqualTo(event);
    }
    
    @Test
    @DisplayName("Kafka 전송 실패 시 예외를 삼켜야 한다")
    void publishEvent_WhenKafkaFails_ShouldNotThrowException() {
        // Given
        UserSignedUpEvent event = UserSignedUpEvent.builder()
                .userId("user-123")
                .email("test@example.com")
                .build();
        
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka connection failed"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);
        
        // When & Then - should not throw exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            eventPublisher.publishUserSignedUpEvent(event);
        });
    }
}