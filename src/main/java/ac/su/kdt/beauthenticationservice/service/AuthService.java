package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.dto.*;
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
                publishLoginFailedEvent(email, ipAddress, "INVALID_CREDENTIALS", 0);
                return LoginResult.failure("Invalid email or password");
            }
            
            User user = userOpt.get();
            
            // 계정 활성 상태 확인
            if (!user.getIsActive()) {
                log.warn("Login failed - account inactive: {} from IP: {}", email, ipAddress);
                loginFailureCounter.increment();
                publishLoginFailedEvent(email, ipAddress, "ACCOUNT_DISABLED", 0);
                return LoginResult.failure("Account is inactive");
            }
            
            // 비밀번호 확인
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("Login failed - invalid password: {} from IP: {}", email, ipAddress);
                loginFailureCounter.increment();
                
                // 실패 횟수 증가 및 계정 잠금 체크
                int failedAttempts = incrementFailedLoginAttempts(user.getId());
                publishLoginFailedEvent(email, ipAddress, "INVALID_CREDENTIALS", failedAttempts);
                
                if (failedAttempts >= 5) {
                    lockUserAccount(user, ipAddress, failedAttempts);
                    return LoginResult.failure("Account locked due to multiple failed attempts");
                }
                
                return LoginResult.failure("Invalid email or password");
            }
            
            // 로그인 성공
            loginSuccessCounter.increment();
            resetFailedLoginAttempts(user.getId());
            
            // 로그인 이벤트 발행
            publishUserLoggedInEvent(user, ipAddress);
            
            log.info("User login successful: {} from IP: {}", email, ipAddress);
            
            return LoginResult.success(user);
            
        } finally {
            sample.stop(jwtIssuanceTimer);
        }
    }
    
    /**
     * 사용자 로그아웃
     */
    public void logoutUser(String userId, String sessionId, String ipAddress, String reason) {
        log.info("User logout: userId={}, sessionId={}, reason={}", userId, sessionId, reason);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            publishUserLoggedOutEvent(userOpt.get(), sessionId, ipAddress, reason);
        }
    }
    
    private int incrementFailedLoginAttempts(String userId) {
        // Redis 또는 DB에서 실패 횟수 관리
        // 임시로 5를 반환 (실제 구현 필요)
        return 3;
    }
    
    private void resetFailedLoginAttempts(String userId) {
        // Redis 또는 DB에서 실패 횟수 초기화
    }
    
    private void lockUserAccount(User user, String ipAddress, int failedAttempts) {
        user.setIsActive(false);
        userRepository.save(user);
        
        publishAccountLockedEvent(user, ipAddress, failedAttempts, "MULTIPLE_FAILED_ATTEMPTS");
        log.warn("Account locked for user: {} after {} failed attempts", user.getEmail(), failedAttempts);
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
    public void resetPassword(String email, String newPassword, String resetToken, String ipAddress) {
        log.info("Password reset execution for email: {}", email);
        
        // TODO: 실제로는 resetToken 검증 로직 필요
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        // 비밀번호 재설정 완료 이벤트 발행
        publishPasswordResetCompletedEvent(user, resetToken, ipAddress);
        
        log.info("Password reset successful for user: {}", email);
    }
    
    /**
     * 비밀번호 변경 (사용자 직접 변경)
     */
    public void changePassword(String userId, String oldPassword, String newPassword, String ipAddress) {
        log.info("Password change request for userId: {}", userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        
        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid current password");
        }
        
        // 새 비밀번호로 변경
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        // 비밀번호 변경 이벤트 발행
        publishPasswordChangedEvent(user, ipAddress, "USER_INITIATED");
        
        log.info("Password changed successfully for user: {}", user.getEmail());
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
                        .eventId(UUID.randomUUID().toString())
                        .authUserId(user.getId())
                        .userId(convertAuthUserIdToLong(user.getId()))
                        .email(user.getEmail())
                        .name(user.getName())
                        .planType(determinePlanType(user))
                        .source(user.getSocialProvider() != null ? "SOCIAL" : "EMAIL")
                        .socialProvider(user.getSocialProvider())
                        .signupTimestamp(user.getCreatedAt())
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishUserSignedUpEvent(event);
                log.debug("Published UserSignedUpEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish UserSignedUpEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    // UUID를 Long으로 변환하는 헬퍼 메서드
    private Long convertAuthUserIdToLong(String authUserId) {
        if (authUserId == null) return null;
        // UUID의 hashCode를 사용하여 Long으로 변환
        return Math.abs((long) authUserId.hashCode());
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
    
    private void publishUserLoggedOutEvent(User user, String sessionId, String ipAddress, String reason) {
        if (eventPublisher.isPresent()) {
            try {
                UserLoggedOutEvent event = UserLoggedOutEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .authUserId(user.getId())
                        .userId(convertAuthUserIdToLong(user.getId()))
                        .email(user.getEmail())
                        .sessionId(sessionId)
                        .ipAddress(ipAddress)
                        .logoutTimestamp(LocalDateTime.now())
                        .reason(reason)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishUserLoggedOutEvent(event);
                log.debug("Published UserLoggedOutEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish UserLoggedOutEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    private void publishLoginFailedEvent(String email, String ipAddress, String failureReason, int attemptCount) {
        if (eventPublisher.isPresent()) {
            try {
                LoginFailedEvent event = LoginFailedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .email(email)
                        .ipAddress(ipAddress)
                        .attemptTimestamp(LocalDateTime.now())
                        .failureReason(failureReason)
                        .attemptCount(attemptCount)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishLoginFailedEvent(event);
                log.debug("Published LoginFailedEvent for email: {}", email);
            } catch (Exception e) {
                log.warn("Failed to publish LoginFailedEvent for email: {}: {}", email, e.getMessage());
            }
        }
    }
    
    private void publishAccountLockedEvent(User user, String ipAddress, int failedAttempts, String lockReason) {
        if (eventPublisher.isPresent()) {
            try {
                AccountLockedEvent event = AccountLockedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .authUserId(user.getId())
                        .userId(convertAuthUserIdToLong(user.getId()))
                        .email(user.getEmail())
                        .lockedAt(LocalDateTime.now())
                        .unlockAt(LocalDateTime.now().plusHours(1)) // 1시간 후 자동 해제
                        .lockReason(lockReason)
                        .failedAttemptCount(failedAttempts)
                        .lastFailedIp(ipAddress)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishAccountLockedEvent(event);
                log.debug("Published AccountLockedEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish AccountLockedEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    private void publishPasswordResetCompletedEvent(User user, String resetToken, String ipAddress) {
        if (eventPublisher.isPresent()) {
            try {
                PasswordResetCompletedEvent event = PasswordResetCompletedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .authUserId(user.getId())
                        .userId(convertAuthUserIdToLong(user.getId()))
                        .email(user.getEmail())
                        .resetAt(LocalDateTime.now())
                        .resetToken(resetToken)
                        .ipAddress(ipAddress)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishPasswordResetCompletedEvent(event);
                log.debug("Published PasswordResetCompletedEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish PasswordResetCompletedEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    private void publishPasswordChangedEvent(User user, String ipAddress, String changeMethod) {
        if (eventPublisher.isPresent()) {
            try {
                PasswordChangedEvent event = PasswordChangedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .authUserId(user.getId())
                        .userId(convertAuthUserIdToLong(user.getId()))
                        .email(user.getEmail())
                        .changedAt(LocalDateTime.now())
                        .changeMethod(changeMethod)
                        .ipAddress(ipAddress)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                eventPublisher.get().publishPasswordChangedEvent(event);
                log.debug("Published PasswordChangedEvent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish PasswordChangedEvent for user: {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }
    
    // === User Synchronization Methods ===
    
    /**
     * 모든 사용자를 다른 서비스와 동기화
     */
    public int syncAllUsersToServices() {
        try {
            // 모든 활성 사용자 조회
            var allUsers = userRepository.findAll();
            var syncDataList = allUsers.stream()
                    .map(this::convertToSyncData)
                    .toList();
            
            if (eventPublisher.isPresent()) {
                // UserSyncEvent import 추가 필요
                var syncEvent = ac.su.kdt.beauthenticationservice.model.dto.UserSyncEvent.createFullSync(syncDataList);
                eventPublisher.get().publishUserSyncEvent(syncEvent);
                
                log.info("전체 사용자 동기화 이벤트 발행 완료: {} users", syncDataList.size());
                return syncDataList.size();
            } else {
                log.warn("EventPublisher가 활성화되지 않아 동기화를 수행할 수 없습니다.");
                return 0;
            }
            
        } catch (Exception e) {
            log.error("사용자 동기화 중 오류 발생", e);
            throw new RuntimeException("사용자 동기화 실패", e);
        }
    }
    
    /**
     * User 엔티티를 UserSyncData로 변환
     */
    private ac.su.kdt.beauthenticationservice.model.dto.UserSyncEvent.UserSyncData convertToSyncData(User user) {
        return ac.su.kdt.beauthenticationservice.model.dto.UserSyncEvent.UserSyncData.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .planType(determinePlanType(user))
                .signupTimestamp(user.getCreatedAt())
                .source(user.getSocialProvider() != null ? "SOCIAL" : "EMAIL")
                .socialProvider(user.getSocialProvider())
                .status(user.getIsActive() ? "ACTIVE" : "INACTIVE")
                .build();
    }
    
    /**
     * 사용자의 구독 플랜 타입 결정
     */
    private String determinePlanType(User user) {
        // 현재 User 엔티티에는 구독 플랜 정보가 없으므로 기본값 설정
        // 향후 User 엔티티에 planType 필드 추가 또는 별도 테이블 참조
        if (user.getRole() == User.UserRole.ADMIN) {
            return "ENTERPRISE";
        }
        return "FREE"; // 기본값
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