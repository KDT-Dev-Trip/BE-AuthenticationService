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
// 비밀번호 변경 이벤트
// 비밀번호 변경 이벤트는 사용자가 비밀번호를 변경할 때 발생하는 이벤트입니다.
// 이벤트는 변경된 비밀번호, 변경 시간, 변경 방법 등의 정보를 포함합니다.
public class PasswordChangedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.password-changed";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("auth_user_id")
    private String authUserId;
    
    private String email;
    private LocalDateTime changedAt;
    private String changeMethod; // "USER_INITIATED", "ADMIN_RESET", "RECOVERY"
    private String ipAddress;
    
    @JsonProperty("timestamp")
    private long timestamp;
}