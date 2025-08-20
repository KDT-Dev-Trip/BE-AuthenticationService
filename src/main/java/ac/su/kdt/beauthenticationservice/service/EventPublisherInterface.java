package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;

/**
 * 이벤트 발행 서비스 인터페이스
 * Kafka 사용 여부에 관계없이 통일된 이벤트 발행 API 제공
 */
public interface EventPublisherInterface {
    
    void publishUserSignedUpEvent(UserSignedUpEvent event);
    void publishUserLoggedInEvent(UserLoggedInEvent event);
    void publishPasswordResetRequestedEvent(PasswordResetRequestedEvent event);
    
    // 편의 메서드들
    void publishUserSignedUp(String userId, String email, String ipAddress);
    void publishUserLoggedIn(String userId, String email, String ipAddress, String userAgent);
    void publishPasswordResetRequested(String userId, String email, String ipAddress);
}