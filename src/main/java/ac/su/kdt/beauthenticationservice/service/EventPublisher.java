package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnBean(KafkaTemplate.class)
public class EventPublisher implements EventPublisherInterface {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    private static final String USER_SIGNED_UP_TOPIC = "user.signed-up";
    private static final String USER_LOGGED_IN_TOPIC = "user.logged-in";
    private static final String PASSWORD_RESET_REQUESTED_TOPIC = "user.password-reset-requested";
    
    public void publishUserSignedUpEvent(UserSignedUpEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(USER_SIGNED_UP_TOPIC, event.getUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish UserSignedUpEvent for userId: {}", 
                             event.getUserId(), exception);
                } else {
                    log.info("Successfully published UserSignedUpEvent for userId: {}", 
                             event.getUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing UserSignedUpEvent for userId: {}", 
                     event.getUserId(), e);
        }
    }
    
    public void publishUserLoggedInEvent(UserLoggedInEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(USER_LOGGED_IN_TOPIC, event.getUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish UserLoggedInEvent for userId: {}", 
                             event.getUserId(), exception);
                } else {
                    log.debug("Successfully published UserLoggedInEvent for userId: {}", 
                             event.getUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing UserLoggedInEvent for userId: {}", 
                     event.getUserId(), e);
        }
    }
    
    public void publishPasswordResetRequestedEvent(PasswordResetRequestedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(PASSWORD_RESET_REQUESTED_TOPIC, event.getUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish PasswordResetRequestedEvent for userId: {}", 
                             event.getUserId(), exception);
                } else {
                    log.info("Successfully published PasswordResetRequestedEvent for userId: {}", 
                             event.getUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing PasswordResetRequestedEvent for userId: {}", 
                     event.getUserId(), e);
        }
    }
    
    // 편의 메서드들 - 직접 이벤트 객체를 생성하지 않고 사용 가능
    public void publishUserSignedUp(String userId, String email, String ipAddress) {
        UserSignedUpEvent event = UserSignedUpEvent.builder()
                .userId(userId)
                .email(email)
                .ipAddress(ipAddress)
                .timestamp(System.currentTimeMillis())
                .build();
        publishUserSignedUpEvent(event);
    }
    
    public void publishUserLoggedIn(String userId, String email, String ipAddress, String userAgent) {
        UserLoggedInEvent event = UserLoggedInEvent.builder()
                .userId(userId)
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .timestamp(System.currentTimeMillis())
                .build();
        publishUserLoggedInEvent(event);
    }
    
    public void publishPasswordResetRequested(String userId, String email, String ipAddress) {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .userId(userId)
                .email(email)
                .ipAddress(ipAddress)
                .timestamp(System.currentTimeMillis())
                .build();
        publishPasswordResetRequestedEvent(event);
    }
    
    // 범용 이벤트 발행 메서드
    public void publishEvent(String eventType, Object event) {
        switch (eventType) {
            case "user.signed_up":
                if (event instanceof UserSignedUpEvent) {
                    publishUserSignedUpEvent((UserSignedUpEvent) event);
                }
                break;
            case "user.logged_in":
                if (event instanceof UserLoggedInEvent) {
                    publishUserLoggedInEvent((UserLoggedInEvent) event);
                }
                break;
            case "password.reset_requested":
                if (event instanceof PasswordResetRequestedEvent) {
                    publishPasswordResetRequestedEvent((PasswordResetRequestedEvent) event);
                }
                break;
            default:
                log.warn("Unknown event type: {}", eventType);
        }
    }
}