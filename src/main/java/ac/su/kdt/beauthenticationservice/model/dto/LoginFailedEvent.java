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
public class LoginFailedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.login-failed";
    
    @JsonProperty("event_id")
    private String eventId;
    
    private String email;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime attemptTimestamp;
    private String failureReason; // "INVALID_CREDENTIALS", "ACCOUNT_LOCKED", "ACCOUNT_DISABLED"
    private Integer attemptCount; // 현재까지 실패 횟수
    
    @JsonProperty("timestamp")
    private long timestamp;
}