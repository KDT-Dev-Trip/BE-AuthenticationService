package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka가 없는 환경에서 사용되는 Fallback EventPublisher
 * 이벤트를 로그로 출력하고 필요한 경우 다른 방식으로 처리
 */
@Slf4j
@Service
@ConditionalOnMissingBean(KafkaTemplate.class)
public class FallbackEventPublisher implements EventPublisherInterface {
    
    public void publishUserSignedUpEvent(UserSignedUpEvent event) {
        log.info("=== USER SIGNED UP EVENT (No Kafka) ===");
        log.info("User ID: {}", event.getUserId());
        log.info("Email: {}", event.getEmail());
        log.info("IP Address: {}", event.getIpAddress());
        log.info("Timestamp: {}", event.getTimestamp());
        log.info("==========================================");
    }
    
    public void publishUserLoggedInEvent(UserLoggedInEvent event) {
        log.info("=== USER LOGGED IN EVENT (No Kafka) ===");
        log.info("User ID: {}", event.getUserId());
        log.info("Email: {}", event.getEmail());
        log.info("IP Address: {}", event.getIpAddress());
        log.info("User Agent: {}", event.getUserAgent());
        log.info("Timestamp: {}", event.getTimestamp());
        log.info("=========================================");
    }
    
    public void publishPasswordResetRequestedEvent(PasswordResetRequestedEvent event) {
        log.info("=== PASSWORD RESET REQUESTED EVENT (No Kafka) ===");
        log.info("User ID: {}", event.getUserId());
        log.info("Email: {}", event.getEmail());
        log.info("IP Address: {}", event.getIpAddress());
        log.info("Timestamp: {}", event.getTimestamp());
        log.info("==================================================");
    }
    
    // 편의 메서드들
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
}