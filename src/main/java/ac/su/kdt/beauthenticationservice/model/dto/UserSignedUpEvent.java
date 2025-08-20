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
public class UserSignedUpEvent {
    
    private String userId;
    private String email;
    private String name;
    private String auth0UserId;
    private String planType;
    private LocalDateTime signupTimestamp;
    private String source; // "EMAIL", "GOOGLE", "GITHUB" 등
    private String socialProvider; // 소셜 로그인 제공자
    private String ipAddress; // IP 주소 추가
    private long timestamp; // 타임스탬프 추가
    
    public static UserSignedUpEvent from(String userId, String email, String name, 
                                       String auth0UserId, String planType, String source, String ipAddress) {
        return UserSignedUpEvent.builder()
                .userId(userId)
                .email(email)
                .name(name)
                .auth0UserId(auth0UserId)
                .planType(planType)
                .source(source)
                .ipAddress(ipAddress)
                .signupTimestamp(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}