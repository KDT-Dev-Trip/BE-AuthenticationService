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
// 사용자 로그인 이벤트
// 사용자 로그인 이벤트는 사용자가 로그인할 때 발생하는 이벤트입니다.
// 이벤트는 로그인 시간, 로그인 방법, 로그인 IP 주소, 사용자 에이전트 등의 정보를 포함합니다.

public class UserLoggedInEvent {
    
    private String userId;
    private String email;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime loginTimestamp;
    private String loginMethod; // "PASSWORD", "SOCIAL", "SSO"
    private String socialProvider; // 소셜 로그인 제공자
    private long timestamp; // 타임스탬프 추가
    
    public static UserLoggedInEvent from(String userId, String email, 
                                       String ipAddress, String userAgent, String loginMethod) {
        return UserLoggedInEvent.builder()
                .userId(userId)
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginMethod(loginMethod)
                .loginTimestamp(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}