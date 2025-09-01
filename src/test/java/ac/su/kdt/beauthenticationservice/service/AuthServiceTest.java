package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.client.UserManagementServiceClient;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Tests")
class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private UserManagementServiceClient userManagementServiceClient;
    
    @Captor
    private ArgumentCaptor<User> userCaptor;
    
    private AuthService authService;
    private MeterRegistry meterRegistry;
    private PasswordEncoder passwordEncoder;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository,
                jwtService,
                passwordEncoder,
                Optional.of(eventPublisher),
                meterRegistry,
                userManagementServiceClient
        );
    }
    
    @Test
    @DisplayName("사용자 회원가입이 성공적으로 처리되어야 한다")
    void shouldSignUpUserSuccessfully() {
        // given
        String email = "test@example.com";
        String password = "password123";
        String name = "Test User";
        
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });
        
        // when
        User result = authService.signUp(email, password, name);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(name);
        
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getName()).isEqualTo(name);
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
    }
    
    @Test
    @DisplayName("이미 존재하는 이메일로 회원가입 시 예외가 발생해야 한다")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // given
        String email = "existing@example.com";
        String password = "password123";
        String name = "Test User";
        
        User existingUser = User.builder()
                .email(email)
                .build();
        
        when(userRepository.existsByEmail(email)).thenReturn(true);
        
        // when & then
        assertThatThrownBy(() -> authService.signUp(email, password, name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }
    
    @Test
    @DisplayName("유효한 자격증명으로 로그인이 성공해야 한다")
    void shouldAuthenticateUserWithValidCredentials() {
        // given
        String email = "test@example.com";
        String password = "password123";
        String ipAddress = "192.168.1.1";
        
        User user = User.builder()
                .id("user123")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name("Test User")
                .role(User.UserRole.USER)
                .isActive(true)
                .emailVerified(true)
                .build();
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        
        // when
        AuthService.LoginResult result = authService.authenticateUser(email, password, ipAddress);
        
        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getErrorMessage()).isNull();
    }
    
    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 실패해야 한다")
    void shouldFailAuthenticationWithInvalidPassword() {
        // given
        String email = "test@example.com";
        String correctPassword = "password123";
        String wrongPassword = "wrongpassword";
        String ipAddress = "192.168.1.1";
        
        User user = User.builder()
                .id("user123")
                .email(email)
                .passwordHash(passwordEncoder.encode(correctPassword))
                .name("Test User")
                .role(User.UserRole.USER)
                .isActive(true)
                .emailVerified(true)
                .build();
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        
        // when
        AuthService.LoginResult result = authService.authenticateUser(email, wrongPassword, ipAddress);
        
        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).contains("Invalid email or password");
    }
    
    @Test
    @DisplayName("존재하지 않는 사용자로 로그인 시 실패해야 한다")
    void shouldFailAuthenticationWithNonExistentUser() {
        // given
        String email = "nonexistent@example.com";
        String password = "password123";
        String ipAddress = "192.168.1.1";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        
        // when
        AuthService.LoginResult result = authService.authenticateUser(email, password, ipAddress);
        
        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).contains("Invalid email or password");
    }
    
    @Test
    @DisplayName("비활성화된 사용자로 로그인 시 실패해야 한다")
    void shouldFailAuthenticationWithInactiveUser() {
        // given
        String email = "inactive@example.com";
        String password = "password123";
        String ipAddress = "192.168.1.1";
        
        User inactiveUser = User.builder()
                .id("user123")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name("Inactive User")
                .role(User.UserRole.USER)
                .isActive(false) // 비활성화된 계정
                .emailVerified(true)
                .build();
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(inactiveUser));
        
        // when
        AuthService.LoginResult result = authService.authenticateUser(email, password, ipAddress);
        
        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).contains("Account is inactive");
    }
    
    @Test
    @DisplayName("유효한 토큰으로 사용자 조회가 성공해야 한다")
    void shouldGetUserFromValidToken() {
        // given
        String token = "valid.jwt.token";
        String userId = "user123";
        
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .name("Test User")
                .role(User.UserRole.USER)
                .build();
        
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // when
        Optional<User> result = authService.getUserFromToken(token);
        
        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(user);
    }
    
    @Test
    @DisplayName("유효하지 않은 토큰으로 사용자 조회 시 빈 결과를 반환해야 한다")
    void shouldReturnEmptyForInvalidToken() {
        // given
        String invalidToken = "invalid.jwt.token";
        
        when(jwtService.isTokenValid(invalidToken)).thenReturn(false);
        
        // when
        Optional<User> result = authService.getUserFromToken(invalidToken);
        
        // then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("비밀번호 재설정 요청이 성공적으로 처리되어야 한다")
    void shouldRequestPasswordResetSuccessfully() {
        // given
        String email = "test@example.com";
        String ipAddress = "192.168.1.1";
        
        User user = User.builder()
                .id("user123")
                .email(email)
                .name("Test User")
                .build();
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        
        // when
        authService.requestPasswordReset(email, ipAddress);
        
        // then
        verify(eventPublisher).publishEvent(eq("password.reset_requested"), any());
    }
    
    @Test
    @DisplayName("존재하지 않는 이메일로 비밀번호 재설정 요청 시 조용히 실패해야 한다")
    void shouldSilentlyFailPasswordResetForNonExistentEmail() {
        // given
        String email = "nonexistent@example.com";
        String ipAddress = "192.168.1.1";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        
        // when
        authService.requestPasswordReset(email, ipAddress);
        
        // then
        // 존재하지 않는 이메일이어도 이벤트는 발행되지 않음 (보안상)
        verify(eventPublisher, never()).publishEvent(eq("password.reset_requested"), any());
    }
}