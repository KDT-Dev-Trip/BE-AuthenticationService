package ac.su.kdt.beauthenticationservice.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT에서 추출된 사용자 정보를 담는 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtUserDetails {
    
    private String userId;
    private String email;
    private String rawToken;
    
    /**
     * User ID를 반환합니다.
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * 사용자의 이메일을 반환합니다.
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * 원본 JWT 토큰을 반환합니다.
     */
    public String getRawToken() {
        return rawToken;
    }
    
    @Override
    public String toString() {
        return "JwtUserDetails{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", hasToken=" + (rawToken != null) +
                '}';
    }
}