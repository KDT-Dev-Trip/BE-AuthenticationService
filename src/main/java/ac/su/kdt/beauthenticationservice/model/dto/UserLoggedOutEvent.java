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
public class UserLoggedOutEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.user-logged-out";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("auth_user_id")
    private String authUserId;
    
    private String email;
    private String sessionId;
    private String ipAddress;
    private LocalDateTime logoutTimestamp;
    private String reason; // "USER_INITIATED", "SESSION_EXPIRED", "FORCED_LOGOUT"
    
    @JsonProperty("timestamp")
    private long timestamp;
}