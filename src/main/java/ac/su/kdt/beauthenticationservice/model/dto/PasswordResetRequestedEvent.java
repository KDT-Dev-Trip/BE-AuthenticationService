package ac.su.kdt.beauthenticationservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 비밀번호 재설정 요청 이벤트
// 비밀번호 재설정 요청 이벤트는 사용자가 비밀번호를 재설정할 때 발생하는 이벤트입니다.
// 이벤트는 재설정 토큰, 요청 시간, 만료 시간 등의 정보를 포함합니다.

public class PasswordResetRequestedEvent {
    
    private String userId;
    private String email;
    private String resetToken;
    private LocalDateTime requestTimestamp;
    private LocalDateTime expiresAt;
    private String ipAddress;
    private long timestamp; // 타임스탬프 추가
    
    public static PasswordResetRequestedEvent from(String userId, String email, String resetToken, 
                                                 String ipAddress, int expirationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return PasswordResetRequestedEvent.builder()
                .userId(userId)
                .email(email)
                .resetToken(resetToken)
                .requestTimestamp(now)
                .expiresAt(now.plusMinutes(expirationMinutes))
                .ipAddress(ipAddress)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}