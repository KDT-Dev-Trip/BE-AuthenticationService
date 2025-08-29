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
// 비밀번호 재설정 완료 이벤트
// 비밀번호 재설정 완료 이벤트는 사용자가 비밀번호를 재설정할 때 발생하는 이벤트입니다.
// 이벤트는 재설정된 비밀번호, 재설정 시간, 재설정 방법 등의 정보를 포함합니다.
public class PasswordResetCompletedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.password-reset-completed";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("auth_user_id")
    private String authUserId;
    
    private String email;
    private LocalDateTime resetAt;
    private String resetToken;
    private String ipAddress;
    
    @JsonProperty("timestamp")
    private long timestamp;
}