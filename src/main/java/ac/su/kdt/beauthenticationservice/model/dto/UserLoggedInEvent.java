package ac.su.kdt.beauthenticationservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoggedInEvent {
    
    private String userId;
    private String email;
    private String auth0UserId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime loginTimestamp;
    private String loginMethod; // "PASSWORD", "SOCIAL", "SSO"
    private String socialProvider; // 소셜 로그인 제공자
    private long timestamp; // 타임스탬프 추가
    
    public static UserLoggedInEvent from(String userId, String email, String auth0UserId, 
                                       String ipAddress, String userAgent, String loginMethod) {
        return UserLoggedInEvent.builder()
                .userId(userId)
                .email(email)
                .auth0UserId(auth0UserId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginMethod(loginMethod)
                .loginTimestamp(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}