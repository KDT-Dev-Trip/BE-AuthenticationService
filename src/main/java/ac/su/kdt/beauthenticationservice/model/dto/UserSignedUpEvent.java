package ac.su.kdt.beauthenticationservice.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.user-signed-up";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private Long userId; // Long으로 변경
    
    @JsonProperty("authUserId")
    private String authUserId; // 기존 String ID 유지
    
    private String email;
    private String name;
    private String planType;
    private LocalDateTime signupTimestamp;
    private String source; // "EMAIL", "GOOGLE", "GITHUB" 등
    private String socialProvider; // 소셜 로그인 제공자
    private String ipAddress; // IP 주소 추가
    private long timestamp; // 타임스탬프 추가
    
    public static UserSignedUpEvent from(String authUserId, String email, String name, 
                                       String planType, String source, String ipAddress) {
        return UserSignedUpEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .authUserId(authUserId)
                .userId(convertAuthUserIdToLong(authUserId)) // UUID를 Long으로 변환
                .email(email)
                .name(name)
                .planType(planType)
                .source(source)
                .ipAddress(ipAddress)
                .signupTimestamp(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    // UUID를 Long으로 변환하는 헬퍼 메서드
    private static Long convertAuthUserIdToLong(String authUserId) {
        if (authUserId == null) return null;
        // UUID의 hashCode를 사용하여 Long으로 변환
        return Math.abs((long) authUserId.hashCode());
    }
}