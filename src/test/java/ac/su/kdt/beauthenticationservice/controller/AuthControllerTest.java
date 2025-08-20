package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("Auth Controller Tests")
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AuthService authService;
    
    @MockBean
    private RedisLoginAttemptService redisLoginAttemptService;
    
    @MockBean
    private JwtService jwtService;
    
    @Test
    @DisplayName("회원가입이 성공적으로 처리되어야 한다")
    void shouldSignUpSuccessfully() throws Exception {
        // given
        Map<String, String> signupRequest = Map.of(
            "email", "test@example.com",
            "password", "password123",
            "name", "Test User"
        );
        
        User mockUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .emailVerified(false)
                .build();
        
        when(authService.signUp(anyString(), anyString(), anyString()))
                .thenReturn(mockUser);
        
        // when & then
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.name").value("Test User"));
    }
    
    @Test
    @DisplayName("로그인이 성공적으로 처리되어야 한다")
    void shouldLoginSuccessfully() throws Exception {
        // given
        Map<String, String> loginRequest = Map.of(
            "email", "test@example.com",
            "password", "password123"
        );
        
        User mockUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .currentTickets(100)
                .emailVerified(true)
                .build();
        
        AuthService.LoginResult successResult = AuthService.LoginResult.success(mockUser);
        
        when(redisLoginAttemptService.isAccountLocked(anyString())).thenReturn(false);
        when(authService.authenticateUser(anyString(), anyString(), anyString()))
                .thenReturn(successResult);
        when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access.token.here");
        when(jwtService.generateRefreshToken(anyString()))
                .thenReturn("refresh.token.here");
        
        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }
    
    @Test
    @DisplayName("잘못된 자격증명으로 로그인 시 실패해야 한다")
    void shouldFailLoginWithInvalidCredentials() throws Exception {
        // given
        Map<String, String> loginRequest = Map.of(
            "email", "test@example.com",
            "password", "wrongpassword"
        );
        
        AuthService.LoginResult failureResult = AuthService.LoginResult.failure("Invalid email or password");
        
        when(redisLoginAttemptService.isAccountLocked(anyString())).thenReturn(false);
        when(authService.authenticateUser(anyString(), anyString(), anyString()))
                .thenReturn(failureResult);
        
        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("LOGIN_FAILED"));
    }
    
    @Test
    @DisplayName("계정이 잠긴 경우 로그인이 차단되어야 한다")
    void shouldBlockLoginForLockedAccount() throws Exception {
        // given
        Map<String, String> loginRequest = Map.of(
            "email", "locked@example.com",
            "password", "password123"
        );
        
        Map<String, Object> lockInfo = Map.of(
            "failedAttempts", 5,
            "lockExpiresAt", System.currentTimeMillis() + 3600000L
        );
        
        when(redisLoginAttemptService.isAccountLocked(anyString())).thenReturn(true);
        when(redisLoginAttemptService.getAccountLockInfoAsMap(anyString())).thenReturn(lockInfo);
        
        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));
    }
    
    @Test
    @DisplayName("토큰 갱신이 성공적으로 처리되어야 한다")
    void shouldRefreshTokenSuccessfully() throws Exception {
        // given
        Map<String, String> refreshRequest = Map.of(
            "refresh_token", "valid.refresh.token"
        );
        
        User mockUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .build();
        
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn("user123");
        when(authService.getUserById(anyString())).thenReturn(Optional.of(mockUser));
        when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("new.access.token");
        
        // when & then
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").value("new.access.token"));
    }
    
    @Test
    @DisplayName("현재 사용자 정보 조회가 성공해야 한다")
    void shouldGetCurrentUserSuccessfully() throws Exception {
        // given
        String token = "valid.jwt.token";
        User mockUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .currentTickets(100)
                .emailVerified(true)
                .pictureUrl("https://example.com/picture.jpg")
                .isActive(true)
                .socialProvider("LOCAL")
                .build();
        
        when(authService.getUserFromToken(anyString())).thenReturn(Optional.of(mockUser));
        
        // when & then
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.socialProvider").value("LOCAL"));
    }
    
    @Test
    @DisplayName("유효하지 않은 토큰으로 현재 사용자 조회 시 실패해야 한다")
    void shouldFailGetCurrentUserWithInvalidToken() throws Exception {
        // given
        String invalidToken = "invalid.jwt.token";
        
        when(authService.getUserFromToken(anyString())).thenReturn(Optional.empty());
        
        // when & then
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }
    
    @Test
    @DisplayName("비밀번호 재설정 요청이 성공해야 한다")
    void shouldRequestPasswordResetSuccessfully() throws Exception {
        // given
        Map<String, String> resetRequest = Map.of(
            "email", "test@example.com"
        );
        
        // when & then
        mockMvc.perform(post("/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    @DisplayName("토큰 검증이 성공해야 한다")
    void shouldValidateTokenSuccessfully() throws Exception {
        // given
        Map<String, String> validateRequest = Map.of(
            "token", "valid.jwt.token"
        );
        
        User mockUser = User.builder()
                .id("user123")
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .currentTickets(100)
                .build();
        
        when(authService.validateToken(anyString())).thenReturn(true);
        when(authService.getUserFromToken(anyString())).thenReturn(Optional.of(mockUser));
        
        // when & then
        mockMvc.perform(post("/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }
    
    @Test
    @DisplayName("헬스체크가 성공해야 한다")
    void shouldReturnHealthyStatus() throws Exception {
        // when & then
        mockMvc.perform(get("/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("oauth2-authentication-service"))
                .andExpect(jsonPath("$.features.localAuth").value("enabled"));
    }
    
    @Test
    @DisplayName("테스트 엔드포인트가 올바른 정보를 반환해야 한다")
    void shouldReturnCorrectTestInfo() throws Exception {
        // when & then
        mockMvc.perform(get("/auth/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OAuth 2.0 인증 서비스가 정상 작동합니다"))
                .andExpect(jsonPath("$.endpoints").exists());
    }
}