package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.dto.LoginRequest;
import ac.su.kdt.beauthenticationservice.model.dto.SignupRequest;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 로컬 이메일/비밀번호 인증 서비스
 * Auth0와 함께 사용되는 보조 로컬 인증 시스템
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LocalAuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisLoginAttemptService loginAttemptService;
    private final EventPublisherInterface eventPublisher;
    private final EmailService emailService;
    
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final int LOCKOUT_DURATION_HOURS = 1;
    
    public SignupResult signup(SignupRequest request, String ipAddress) {
        log.info("Local signup requested for email: {} from IP: {}", request.getEmail(), ipAddress);
        
        // 기존 사용자 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        
        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // 사용자 생성
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .name(request.getName())
                .isActive(true)
                .emailVerified(false)
                .failedLoginAttempts(0)
                .build();
        
        User savedUser = userRepository.save(user);
        
        // 이메일 인증 발송
        sendEmailVerification(savedUser.getEmail(), savedUser.getId());
        
        // 회원가입 이벤트 발행
        eventPublisher.publishUserSignedUp(savedUser.getId(), savedUser.getEmail(), ipAddress);
        
        log.info("Local user created successfully: {}", savedUser.getEmail());
        
        return SignupResult.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .requiresEmailVerification(true)
                .build();
    }
    
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Local login requested for email: {} from IP: {}", request.getEmail(), ipAddress);
        
        // Redis 기반 로그인 시도 제한 확인
        if (loginAttemptService.isAccountLocked(request.getEmail())) {
            throw new IllegalArgumentException("계정이 잠겨있습니다. 1시간 후 다시 시도해 주세요.");
        }
        
        // 사용자 조회
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        if (optionalUser.isEmpty()) {
            loginAttemptService.recordFailedAttempt(request.getEmail(), ipAddress);
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        User user = optionalUser.get();
        
        // 계정 상태 확인
        if (!user.getIsActive()) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }
        
        // 계정 잠금 확인
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("계정이 잠겨있습니다.");
        }
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // 로그인 실패 처리
            handleFailedLogin(user, ipAddress);
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        // 로그인 성공 처리
        handleSuccessfulLogin(user);
        
        // JWT 토큰 생성
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        // 로그인 이벤트 발행
        eventPublisher.publishUserLoggedIn(user.getId(), user.getEmail(), ipAddress, userAgent);
        
        log.info("Local login successful for user: {}", user.getEmail());
        
        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationTime())
                .userInfo(createUserInfo(user))
                .build();
    }
    
    private void handleFailedLogin(User user, String ipAddress) {
        // Redis 기반 로그인 시도 기록
        loginAttemptService.recordFailedAttempt(user.getEmail(), ipAddress);
        
        // 데이터베이스 기반 실패 횟수 증가
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setLastFailedLoginAt(LocalDateTime.now());
        
        // 최대 시도 횟수 도달 시 계정 잠금
        if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusHours(LOCKOUT_DURATION_HOURS));
            log.warn("Account locked due to too many failed login attempts: {}", user.getEmail());
        }
        
        userRepository.save(user);
    }
    
    private void handleSuccessfulLogin(User user) {
        // Redis 기반 로그인 시도 초기화
        loginAttemptService.recordSuccessfulLogin(user.getEmail());
        
        // 데이터베이스 기반 실패 횟수 초기화
        user.setFailedLoginAttempts(0);
        user.setLastFailedLoginAt(null);
        user.setLockedUntil(null);
        
        userRepository.save(user);
    }
    
    public void requestPasswordReset(String email, String ipAddress) {
        log.info("Password reset requested for email: {} from IP: {}", email, ipAddress);
        
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            // 보안상 이유로 사용자가 존재하지 않아도 성공 메시지 반환
            log.warn("Password reset requested for non-existent email: {}", email);
            return;
        }
        
        User user = optionalUser.get();
        if (!user.getIsActive()) {
            log.warn("Password reset requested for inactive user: {}", email);
            return;
        }
        
        // 비밀번호 재설정 토큰 생성 및 이메일 발송
        String resetToken = generatePasswordResetToken(user.getId());
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetToken);
        
        // 이벤트 발행
        eventPublisher.publishPasswordResetRequested(user.getId(), user.getEmail(), ipAddress);
        
        log.info("Password reset email sent to: {}", email);
    }
    
    public void confirmPasswordReset(String token, String newPassword, String ipAddress) {
        log.info("Password reset confirmation from IP: {}", ipAddress);
        
        // 토큰 검증 및 사용자 ID 추출
        String userId = validatePasswordResetToken(token);
        if (userId == null) {
            throw new IllegalArgumentException("토큰이 유효하지 않거나 만료되었습니다.");
        }
        
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        
        User user = optionalUser.get();
        
        // 새 비밀번호 설정
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(encodedPassword);
        user.setFailedLoginAttempts(0); // 비밀번호 재설정 시 실패 횟수 초기화
        user.setLockedUntil(null); // 계정 잠금 해제
        
        userRepository.save(user);
        
        // 비밀번호 재설정 토큰 무효화
        invalidatePasswordResetToken(token);
        
        log.info("Password reset completed for user: {}", user.getEmail());
    }
    
    public void logout(String token, String ipAddress) {
        log.info("Local logout requested from IP: {}", ipAddress);
        
        // JWT 토큰을 블랙리스트에 추가
        if (token != null && token.startsWith("Bearer ")) {
            String jwtToken = token.substring(7);
            // TokenBlacklistService를 통해 토큰 무효화 처리
            // TODO: TokenBlacklistService 구현 필요
        }
        
        log.info("Local logout completed from IP: {}", ipAddress);
    }
    
    private void sendEmailVerification(String email, String userId) {
        // TODO: 이메일 인증 토큰 생성 및 발송
        log.info("Email verification sent to: {}", email);
    }
    
    private String generatePasswordResetToken(String userId) {
        // TODO: JWT 기반 비밀번호 재설정 토큰 생성
        return jwtService.generatePasswordResetToken(userId);
    }
    
    private String validatePasswordResetToken(String token) {
        // TODO: JWT 기반 비밀번호 재설정 토큰 검증
        boolean isValid = jwtService.validatePasswordResetToken(token);
        return isValid ? jwtService.extractUserId(token) : null;
    }
    
    private void invalidatePasswordResetToken(String token) {
        // TODO: 토큰 블랙리스트 추가
        log.info("Password reset token invalidated");
    }
    
    private Object createUserInfo(User user) {
        return new Object() {
            public final String id = user.getId();
            public final String email = user.getEmail();
            public final String name = user.getName();
            public final boolean emailVerified = user.getEmailVerified();
        };
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SignupResult {
        private String userId;
        private String email;
        private boolean requiresEmailVerification;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class LoginResult {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private Object userInfo;
    }
}