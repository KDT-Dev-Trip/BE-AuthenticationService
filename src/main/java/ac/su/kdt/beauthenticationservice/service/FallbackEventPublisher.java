package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.*;
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
    
    @Override
    public void publishUserLoggedOutEvent(UserLoggedOutEvent event) {
        log.info("=== USER LOGGED OUT EVENT (No Kafka) ===");
        log.info("Auth User ID: {}", event.getAuthUserId());
        log.info("Email: {}", event.getEmail());
        log.info("Logout Reason: {}", event.getReason());
        log.info("=========================================");
    }

    @Override
    public void publishLoginFailedEvent(LoginFailedEvent event) {
        log.info("=== LOGIN FAILED EVENT (No Kafka) ===");
        log.info("Email: {}", event.getEmail());
        log.info("Failure Reason: {}", event.getFailureReason());
        log.info("Attempt Count: {}", event.getAttemptCount());
        log.info("======================================");
    }

    @Override
    public void publishAccountLockedEvent(AccountLockedEvent event) {
        log.info("=== ACCOUNT LOCKED EVENT (No Kafka) ===");
        log.info("Auth User ID: {}", event.getAuthUserId());
        log.info("Email: {}", event.getEmail());
        log.info("Lock Reason: {}", event.getLockReason());
        log.info("Failed Attempt Count: {}", event.getFailedAttemptCount());
        log.info("=======================================");
    }

    @Override
    public void publishPasswordChangedEvent(PasswordChangedEvent event) {
        log.info("=== PASSWORD CHANGED EVENT (No Kafka) ===");
        log.info("Auth User ID: {}", event.getAuthUserId());
        log.info("Change Method: {}", event.getChangeMethod());
        log.info("==========================================");
    }

    @Override
    public void publishPasswordResetCompletedEvent(PasswordResetCompletedEvent event) {
        log.info("=== PASSWORD RESET COMPLETED EVENT (No Kafka) ===");
        log.info("Auth User ID: {}", event.getAuthUserId());
        log.info("Email: {}", event.getEmail());
        log.info("Reset At: {}", event.getResetAt());
        log.info("================================================");
    }

    @Override
    public void publishTeamCreatedEvent(TeamCreatedEvent event) {
        log.info("=== TEAM CREATED EVENT (No Kafka) ===");
        log.info("Team ID: {}", event.getTeamId());
        log.info("Team Name: {}", event.getTeamName());
        log.info("Creator: {}", event.getCreatorAuthUserId());
        log.info("=====================================");
    }

    @Override
    public void publishTeamMemberAddedEvent(TeamMemberAddedEvent event) {
        log.info("=== TEAM MEMBER ADDED EVENT (No Kafka) ===");
        log.info("Team ID: {}", event.getTeamId());
        log.info("User ID: {}", event.getAuthUserId());
        log.info("Member Role: {}", event.getMemberRole());
        log.info("==========================================");
    }

    public void publishUserSyncEvent(UserSyncEvent event) {
        log.info("=== USER SYNC EVENT (No Kafka) ===");
        log.info("Event Type: {}", event.getEventType());
        log.info("User Count: {}", event.getUsers().size());
        log.info("==================================");
    }

    public void publishEvent(String eventType, Object event) {
        log.info("=== GENERIC EVENT (No Kafka) ===");
        log.info("Event Type: {}", eventType);
        log.info("Event: {}", event);
        log.info("=================================");
    }
}