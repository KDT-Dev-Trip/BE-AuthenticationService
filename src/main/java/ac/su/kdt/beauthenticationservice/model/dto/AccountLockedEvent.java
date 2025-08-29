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
// 계정 잠금 이벤트
// 계정 잠금 이벤트는 사용자가 여러 번 실패한 로그인 시도 후 계정이 잠금될 때 발생하는 이벤트입니다.
// 이벤트는 계정 잠금 시간, 잠금 이유, 잠금 해제 시간 등의 정보를 포함합니다.

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