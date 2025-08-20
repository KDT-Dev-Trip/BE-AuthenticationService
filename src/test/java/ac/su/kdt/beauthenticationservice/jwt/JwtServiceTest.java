package ac.su.kdt.beauthenticationservice.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JWT Service Tests")
class JwtServiceTest {
    
    private JwtService jwtService;
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        
        // 테스트용 JWT 설정 주입 (256bit = 32byte minimum)
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-for-jwt-testing-purposes-minimum-256-bits-required-for-hmac-sha256-algorithm");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 86400L);
        ReflectionTestUtils.setField(jwtService, "issuer", "test-issuer");
    }
    
    @Test
    @DisplayName("유효하지 않은 토큰으로 검증 시 false를 반환해야 한다")
    void shouldReturnFalseForInvalidToken() {
        // given
        String invalidToken = "invalid.jwt.token";
        
        // when & then
        assertThat(jwtService.isTokenValid(invalidToken)).isFalse();
    }
    
    @Test
    @DisplayName("Access Token 생성 및 검증이 정상 작동해야 한다")
    void shouldGenerateAndValidateAccessToken() {
        // given
        String userId = "user123";
        String email = "test@example.com";
        String role = "USER";
        
        // when
        String token = jwtService.generateAccessToken(userId, email, role);
        
        // then
        assertThat(token).isNotNull();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.extractRole(token)).isEqualTo(role);
    }
    
    @Test
    @DisplayName("Refresh Token 생성 및 검증이 정상 작동해야 한다")
    void shouldGenerateAndValidateRefreshToken() {
        // given
        String userId = "user123";
        
        // when
        String refreshToken = jwtService.generateRefreshToken(userId);
        
        // then
        assertThat(refreshToken).isNotNull();
        assertThat(jwtService.isTokenValid(refreshToken)).isTrue();
        assertThat(jwtService.extractUserId(refreshToken)).isEqualTo(userId);
    }
    
    @Test
    @DisplayName("ID Token 생성 및 검증이 정상 작동해야 한다")
    void shouldGenerateAndValidateIdToken() {
        // given
        String userId = "user123";
        String email = "test@example.com";
        String name = "Test User";
        String picture = "https://example.com/picture.jpg";
        
        // when
        String idToken = jwtService.generateIdToken(userId, email, name, picture, null);
        
        // then
        assertThat(idToken).isNotNull();
        assertThat(jwtService.isTokenValid(idToken)).isTrue();
        assertThat(jwtService.extractUserId(idToken)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(idToken)).isEqualTo(email);
    }
    
    @Test
    @DisplayName("비밀번호 재설정 토큰 생성 및 검증이 정상 작동해야 한다")
    void shouldGenerateAndValidatePasswordResetToken() {
        // given
        String userId = "user123";
        
        // when
        String resetToken = jwtService.generatePasswordResetToken(userId);
        
        // then
        assertThat(resetToken).isNotNull();
        assertThat(jwtService.validatePasswordResetToken(resetToken)).isTrue();
        assertThat(jwtService.extractUserId(resetToken)).isEqualTo(userId);
    }
    
    @Test
    @DisplayName("만료된 토큰은 유효하지 않아야 한다")
    void shouldReturnFalseForExpiredToken() {
        // given - 만료 시간을 과거로 설정
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1L);
        String userId = "user123";
        String email = "test@example.com";
        String role = "USER";
        
        // when
        String expiredToken = jwtService.generateAccessToken(userId, email, role);
        
        // then
        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
        assertThat(jwtService.isTokenExpired(expiredToken)).isTrue();
    }
    
    @Test
    @DisplayName("토큰 정보 추출이 정상 작동해야 한다")
    void shouldExtractTokenInfo() {
        // given
        String userId = "user123";
        String email = "test@example.com";
        String role = "USER";
        String token = jwtService.generateAccessToken(userId, email, role);
        
        // when
        var tokenInfo = jwtService.getTokenInfo(token);
        
        // then
        assertThat(tokenInfo).containsKey("sub");
        assertThat(tokenInfo).containsKey("email");
        assertThat(tokenInfo).containsKey("role");
        assertThat(tokenInfo).containsKey("iss");
        assertThat(tokenInfo).containsKey("exp");
    }
}