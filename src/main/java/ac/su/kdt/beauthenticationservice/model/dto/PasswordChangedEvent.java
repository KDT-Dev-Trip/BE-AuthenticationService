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