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
public class AccountLockedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.account-locked";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("auth_user_id")
    private String authUserId;
    
    private String email;
    private LocalDateTime lockedAt;
    private LocalDateTime unlockAt; // null이면 영구 잠금
    private String lockReason; // "MULTIPLE_FAILED_ATTEMPTS", "ADMIN_ACTION", "SECURITY_BREACH"
    private Integer failedAttemptCount;
    private String lastFailedIp;
    
    @JsonProperty("timestamp")
    private long timestamp;
}