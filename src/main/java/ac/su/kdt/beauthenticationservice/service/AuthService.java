package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequestedEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserLoggedInEvent;
import ac.su.kdt.beauthenticationservice.model.dto.UserSignedUpEvent;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 인증 서비스 (완전 리팩토링됨)
 * Auth0 의존성을 제거하고 자체 OAuth 2.0 시스템으로 전환
 */
@Slf4j
@Service
@Transactional
public class AuthService {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final Optional<EventPublisher> eventPublisher;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter signupSuccessCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Timer jwtIssuanceTimer;
    
    public AuthService(UserRepository userRepository, JwtService jwtService, 
                      PasswordEncoder passwordEncoder, Optional<EventPublisher> eventPublisher, 
                      MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.signupSuccessCounter = Counter.builder("signup_success_count")
                .description("Number of successful user signups")
                .register(meterRegistry);
        
        this.loginSuccessCounter = Counter.builder("login_success_count")
                .description("Number of successful user logins")
                .register(meterRegistry);
        
        this.loginFailureCounter = Counter.builder("login_failure_count")
                .description("Number of failed user logins")
                .register(meterRegistry);
        
        this.jwtIssuanceTimer = Timer.builder("jwt_issuance_duration")
                .description("Time taken to issue JWT tokens")
                .register(meterRegistry);
    }
    
    /**
     * 사용자 회원가입 (이메일/비밀번호)
     */
    public User signUp(String email, String password, String name) {
        log.info("User signup attempt for email: {}", email);
        
        // 이메일 중복 확인
        if (userRepository.existsByEmail(email)) {
            log.warn("Signup failed - email already exists: {}", email);
            throw new IllegalArgumentException("Email already exists");
        }
        
        // 사용자 생성
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name(name)
                .socialProvider("local")
                .role(User.UserRole.USER)
                .isActive(true)
                .emailVerified(false) // 이메일 인증 필요
                .currentTickets(100) // 신규 사용자 초기 티켓
                .build();
        
        user = userRepository.save(user);
        
        // 메트릭 업데이트
        signupSuccessCounter.increment();
        
        // 회원가입 이벤트 발행
        publishUserSignedUpEvent(user);
        
        log.info("User signup successful: {}", user.getEmail());
        return user;
    }
    
    /**
     * 이메일/비밀번호 로그인
     */
    public LoginResult authenticateUser(String email, String password, String ipAddress) {
        log.info("User login attempt for email: {} from IP: {}", email, ipAddress);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // 사용자 조회
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                log.warn("Login failed - user not found: {} from IP: {}", email, ipAddress);
                loginFailureCounter.increment();
                return LoginResult.failure("Invalid email or password");
            }
            
            User user = userOpt.get();
            
            // 계정 활성 상태 확인
            if (!user.getIsActive()) {
                log.warn("Login failed - account inactive: {} from IP: {}", email, ipAddress);
                loginFailureCounter.increment();
                return LoginResult.failure("Account is inactive");
            }
            
            // 비밀번호 확인
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("Login failed - invalid password: {} from IP: {}", email, ipAddress);
                loginFailureCounter.increment();
                return LoginResult.failure("Invalid email or password");
            }
            
            // 로그인 성공
            loginSuccessCounter.increment();
            
            // 로그인 이벤트 발행
            publishUserLoggedInEvent(user, ipAddress);
            
            log.info("User login successful: {} from IP: {}", email, ipAddress);
            
            return LoginResult.success(user);
            
        } finally {
            sample.stop(jwtIssuanceTimer);
        }
    }
    
    /**
     * 사용자 ID로 조회
     */
    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * 이메일로 사용자 조회
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * 토큰에서 사용자 정보 추출
     */
    public Optional<User> getUserFromToken(String token) {
        try {
            if (!jwtService.isTokenValid(token)) {
                return Optional.empty();
            }
            
            String userId = jwtService.extractUserId(token);
            return getUserById(userId);
            
        } catch (Exception e) {
            log.warn("Failed to extract user from token: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        return jwtService.isTokenValid(token);
    }
    
    /**
     * 비밀번호 재설정 요청
     */
    public void requestPasswordReset(String email, String ipAddress) {
        log.info("Password reset request for email: {} from IP: {}", email, ipAddress);
        
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // 보안상 사용자가 존재하지 않아도 성공으로 응답
            log.warn("Password reset request for non-existent user: {} from IP: {}", email, ipAddress);
            return;
        }
        
        User user = userOpt.get();
        
        // 비밀번호 재설정 이벤트 발행
        publishPasswordResetEvent(user, ipAddress);
        
        log.info("Password reset event published for user: {} from IP: {}", email, ipAddress);
    }
    
    /**
     * 비밀번호 재설정 실행
     */
    public void resetPassword(String email, String newPassword, String resetToken) {
        log.info("Password reset execution for email: {}", email);
        
        // TODO: 실제로는 resetToken 검증 로직 필요
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Password reset successful for user: {}", email);
    }
    
    /**
     * 이메일 인증 처리
     */
    public void verifyEmail(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setEmailVerified(true);
        userRepository.save(user);
        
        log.info("Email verified for user: {}", user.getEmail());
    }
    
    /**
     * 사용자 역할 업데이트
     */
    public void updateUserRole(String userId, User.UserRole newRole) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        User.UserRole oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        
        log.info("User role updated: {} from {} to {}", user.getEmail(), oldRole, newRole);
    }
    
    /**
     * 사용자 계정 비활성화
     */
    public void deactivateUser(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setIsActive(false);
        userRepository.save(user);
        
        log.info("User account deactivated: {}", user.getEmail());
    }
    
    // === Event Publishing Methods ===
    
    private void publishUserSignedUpEvent(User user) {
        if (eventPublisher.isPresent()) {
            try {
                UserSignedUpEvent event = UserSignedUpEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .socialProvider(user.getSocialProvider())
                        .signupTimestamp(user.getCreatedAt())
                        .build();
                
                eventPublisher.get().publishEvent("user.signed_up", event);
                log.debug("Published UserSignedUpEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish UserSignedUpEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    private void publishUserLoggedInEvent(User user, String ipAddress) {
        if (eventPublisher.isPresent()) {
            try {
                UserLoggedInEvent event = UserLoggedInEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .ipAddress(ipAddress)
                        .loginTimestamp(LocalDateTime.now())
                        .socialProvider(user.getSocialProvider())
                        .userAgent("OAuth2-Client") // TODO: 실제 User-Agent 전달
                        .build();
                
                eventPublisher.get().publishEvent("user.logged_in", event);
                log.debug("Published UserLoggedInEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish UserLoggedInEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    private void publishPasswordResetEvent(User user, String ipAddress) {
        if (eventPublisher.isPresent()) {
            try {
                PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .requestTimestamp(LocalDateTime.now())
                        .ipAddress(ipAddress)
                        .resetToken(UUID.randomUUID().toString()) // 실제로는 안전한 토큰 생성
                        .build();
                
                eventPublisher.get().publishEvent("password.reset_requested", event);
                log.debug("Published PasswordResetRequestedEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish PasswordResetRequestedEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    // === Inner Classes ===
    
    /**
     * 로그인 결과 DTO
     */
    @Data
    @Builder
    public static class LoginResult {
        private final boolean success;
        private final User user;
        private final String errorMessage;
        
        private LoginResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }
        
        public static LoginResult success(User user) {
            return new LoginResult(true, user, null);
        }
        
        public static LoginResult failure(String errorMessage) {
            return new LoginResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public User getUser() { return user; }
        public String getErrorMessage() { return errorMessage; }
    }
}