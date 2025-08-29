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
// 사용자 로그아웃 이벤트
// 사용자 로그아웃 이벤트는 사용자가 로그아웃할 때 발생하는 이벤트입니다.
// 이벤트는 로그아웃 시간, 로그아웃 방법, 로그아웃 IP 주소, 사용자 에이전트 등의 정보를 포함합니다.

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