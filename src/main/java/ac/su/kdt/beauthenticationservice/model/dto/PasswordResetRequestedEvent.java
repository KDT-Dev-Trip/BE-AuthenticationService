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