package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.AccountLockedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.LoginFailedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordChangedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetCompletedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedOutEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSyncEvent;
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
    
    // MSA 공유 Kafka 토픽명 - 모든 인증 이벤트는 단일 토픽 사용
    private static final String AUTH_EVENTS_TOPIC = "auth-events";
    
    // 기존 토픽명 (호환성 유지용) - 필요시 주석 해제하여 dual publish 가능
    /*
    private static final String LEGACY_USER_SIGNED_UP_TOPIC = "user.signed-up";
    private static final String LEGACY_USER_LOGGED_IN_TOPIC = "user.logged-in";
    private static final String LEGACY_PASSWORD_RESET_TOPIC = "user.password-reset-requested";
    */
    
    public void publishUserSignedUpEvent(UserSignedUpEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getUserId().toString(), event);
            
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
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getUserId(), event);
            
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
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getUserId(), event);
            
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
    public void publishUserSignedUp(String authUserId, String email, String ipAddress) {
        UserSignedUpEvent event = UserSignedUpEvent.builder()
                .authUserId(authUserId)
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
    
    public void publishUserSyncEvent(UserSyncEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, "sync", event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish UserSyncEvent: eventType={}, userCount={}", 
                             event.getEventType(), event.getUsers().size(), exception);
                } else {
                    log.info("Successfully published UserSyncEvent: eventType={}, userCount={}", 
                             event.getEventType(), event.getUsers().size());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing UserSyncEvent: eventType={}, userCount={}", 
                     event.getEventType(), event.getUsers().size(), e);
        }
    }
    
    // 새로운 이벤트 발행 메서드들
    public void publishUserLoggedOutEvent(UserLoggedOutEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getAuthUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish UserLoggedOutEvent for userId: {}", 
                             event.getAuthUserId(), exception);
                } else {
                    log.info("Successfully published UserLoggedOutEvent for userId: {}", 
                             event.getAuthUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing UserLoggedOutEvent for userId: {}", 
                     event.getAuthUserId(), e);
        }
    }
    
    public void publishLoginFailedEvent(LoginFailedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getEmail(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish LoginFailedEvent for email: {}", 
                             event.getEmail(), exception);
                } else {
                    log.info("Successfully published LoginFailedEvent for email: {}", 
                             event.getEmail());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing LoginFailedEvent for email: {}", 
                     event.getEmail(), e);
        }
    }
    
    public void publishAccountLockedEvent(AccountLockedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getAuthUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish AccountLockedEvent for userId: {}", 
                             event.getAuthUserId(), exception);
                } else {
                    log.info("Successfully published AccountLockedEvent for userId: {}", 
                             event.getAuthUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing AccountLockedEvent for userId: {}", 
                     event.getAuthUserId(), e);
        }
    }
    
    public void publishPasswordChangedEvent(PasswordChangedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getAuthUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish PasswordChangedEvent for userId: {}", 
                             event.getAuthUserId(), exception);
                } else {
                    log.info("Successfully published PasswordChangedEvent for userId: {}", 
                             event.getAuthUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing PasswordChangedEvent for userId: {}", 
                     event.getAuthUserId(), e);
        }
    }
    
    public void publishPasswordResetCompletedEvent(PasswordResetCompletedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(AUTH_EVENTS_TOPIC, event.getAuthUserId(), event);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to publish PasswordResetCompletedEvent for userId: {}", 
                             event.getAuthUserId(), exception);
                } else {
                    log.info("Successfully published PasswordResetCompletedEvent for userId: {}", 
                             event.getAuthUserId());
                }
            });
        } catch (Exception e) {
            log.error("Error publishing PasswordResetCompletedEvent for userId: {}", 
                     event.getAuthUserId(), e);
        }
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