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
// 로그인 실패 이벤트
// 로그인 실패 이벤트는 사용자가 로그인 시도 시 실패할 때 발생하는 이벤트입니다.
// 이벤트는 실패한 이유, 실패 시간, 실패 횟수 등의 정보를 포함합니다.
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