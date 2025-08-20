package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 로컬 인증 컨트롤러
 * OAuth 2.0 Authorization Server와 함께 사용되는 로컬 이메일/비밀번호 로그인 API
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Authentication", description = "로컬 이메일/비밀번호 인증 API")
public class AuthController {
    
    private final AuthService authService;
    private final RedisLoginAttemptService redisLoginAttemptService;
    private final JwtService jwtService;
    
    @GetMapping("/test")
    @Operation(summary = "Authentication Service Test", description = "인증 서비스 동작 확인 및 사용 가능한 엔드포인트 조회")
    public ResponseEntity<?> testAuth() {
        return ResponseEntity.ok(Map.of(
            "message", "OAuth 2.0 인증 서비스가 정상 작동합니다",
            "timestamp", System.currentTimeMillis(),
            "endpoints", Map.of(
                "POST /auth/signup", "이메일/비밀번호 회원가입",
                "POST /auth/login", "이메일/비밀번호 로그인",
                "POST /auth/validate", "JWT 토큰 검증",
                "POST /auth/password-reset", "비밀번호 재설정",
                "GET /oauth/authorize", "OAuth 2.0 Authorization Code 발급",
                "POST /oauth/token", "OAuth 2.0 Access Token 교환"
            )
        ));
    }

    @PostMapping("/signup")
    @Operation(summary = "User Signup", description = "이메일/비밀번호로 새 사용자 회원가입")
    public ResponseEntity<?> signup(
            @Parameter(description = "회원가입 요청 데이터") @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        
        try {
            String email = request.get("email");
            String password = request.get("password");
            String name = request.get("name");
            
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "EMAIL_REQUIRED", "message", "이메일이 필요합니다"));
            }
            
            if (password == null || password.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "PASSWORD_REQUIRED", "message", "비밀번호가 필요합니다"));
            }
            
            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "NAME_REQUIRED", "message", "이름이 필요합니다"));
            }
            
            log.info("User signup attempt for email: {} from IP: {}", email, ipAddress);
            
            User user = authService.signUp(email, password, name);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원가입이 완료되었습니다",
                "user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "role", user.getRole().toString(),
                    "emailVerified", user.getEmailVerified()
                )
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Signup failed from IP: {}: {}", ipAddress, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "SIGNUP_FAILED", "message", e.getMessage()));
                
        } catch (Exception e) {
            log.error("Signup error from IP: {}", ipAddress, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "회원가입 처리 중 오류가 발생했습니다"));
        }
    }
    
    @PostMapping("/login")
    @Operation(summary = "User Login", description = "이메일/비밀번호로 로그인")
    public ResponseEntity<?> login(
            @Parameter(description = "로그인 요청 데이터") @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String email = request.get("email");
        String password = request.get("password");
        
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "EMAIL_REQUIRED", "message", "이메일이 필요합니다"));
        }
        
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "PASSWORD_REQUIRED", "message", "비밀번호가 필요합니다"));
        }
        
        // Redis 로그인 시도 제한 확인
        if (redisLoginAttemptService.isAccountLocked(email)) {
            var lockInfo = redisLoginAttemptService.getAccountLockInfoAsMap(email);
            log.warn("Login blocked - account locked: {} from IP: {}", email, ipAddress);
            
            return ResponseEntity.status(423).body(Map.of(
                "error", "ACCOUNT_LOCKED",
                "message", "계정이 잠겨있습니다. 1시간 후 다시 시도해 주세요.",
                "details", Map.of(
                    "failedAttempts", lockInfo.get("failedAttempts"),
                    "lockExpiresAt", lockInfo.get("lockExpiresAt"),
                    "isLocked", true
                )
            ));
        }
        
        try {
            // 사용자 인증
            AuthService.LoginResult loginResult = authService.authenticateUser(email, password, ipAddress);
            
            if (!loginResult.isSuccess()) {
                // 로그인 실패 기록
                redisLoginAttemptService.recordFailedAttempt(email, ipAddress);
                log.warn("Login failed for email: {} from IP: {}: {}", email, ipAddress, loginResult.getErrorMessage());
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "LOGIN_FAILED", "message", loginResult.getErrorMessage()));
            }
            
            User user = loginResult.getUser();
            
            // JWT 토큰 생성
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
            String refreshToken = jwtService.generateRefreshToken(user.getId());
            
            // 로그인 성공 기록
            redisLoginAttemptService.recordSuccessfulLogin(email);
            
            log.info("Login successful for email: {} from IP: {}", email, ipAddress);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "로그인이 완료되었습니다",
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "role", user.getRole().toString(),
                    "tickets", user.getCurrentTickets(),
                    "emailVerified", user.getEmailVerified()
                )
            ));
            
        } catch (Exception e) {
            // 예외가 발생해도 실패 시도로 기록
            redisLoginAttemptService.recordFailedAttempt(email, ipAddress);
            log.error("Login error for email: {} from IP: {}", email, ipAddress, e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "로그인 처리 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Refresh Token으로 새 Access Token 발급")
    public ResponseEntity<?> refreshToken(
            @Parameter(description = "Refresh Token 요청") @RequestBody Map<String, String> request) {
        
        try {
            String refreshToken = request.get("refresh_token");
            
            if (refreshToken == null || refreshToken.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "REFRESH_TOKEN_REQUIRED", "message", "Refresh token이 필요합니다"));
            }
            
            // Refresh Token 검증
            if (!jwtService.isTokenValid(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_REFRESH_TOKEN", "message", "유효하지 않은 refresh token입니다"));
            }
            
            // 사용자 정보 추출
            String userId = jwtService.extractUserId(refreshToken);
            Optional<User> userOpt = authService.getUserById(userId);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "USER_NOT_FOUND", "message", "사용자를 찾을 수 없습니다"));
            }
            
            User user = userOpt.get();
            
            // 새 Access Token 생성
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "accessToken", accessToken,
                "message", "새 Access Token이 발급되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("Refresh token failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "REFRESH_FAILED", "message", "토큰 갱신 중 오류가 발생했습니다"));
        }
    }
    
    @PostMapping("/password-reset")
    public ResponseEntity<?> requestPasswordReset(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email is required"));
            }
            
            String ipAddress = getClientIpAddress(httpRequest);
            authService.requestPasswordReset(email, ipAddress);
            
            // 보안상 항상 성공 응답
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "If the email exists, a password reset link has been sent"
            ));
            
        } catch (Exception e) {
            log.error("Password reset request failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Password reset request failed"));
        }
    }
    
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Token is required"));
            }
            
            boolean isValid = authService.validateToken(token);
            
            if (isValid) {
                Optional<User> userOpt = authService.getUserFromToken(token);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "user", Map.of(
                            "id", user.getId(),
                            "email", user.getEmail(),
                            "name", user.getName(),
                            "role", user.getRole().toString(),
                            "tickets", user.getCurrentTickets()
                        )
                    ));
                }
            }
            
            return ResponseEntity.ok(Map.of("valid", false));
            
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return ResponseEntity.ok(Map.of("valid", false));
        }
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get Current User", description = "현재 로그인한 사용자 정보 조회")
    public ResponseEntity<?> getCurrentUser(
            @Parameter(description = "Bearer JWT Token") @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_HEADER", "message", "유효하지 않은 Authorization 헤더입니다"));
            }
            
            String token = authHeader.substring(7);
            Optional<User> userOpt = authService.getUserFromToken(token);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "role", user.getRole().toString(),
                    "tickets", user.getCurrentTickets(),
                    "emailVerified", user.getEmailVerified(),
                    "pictureUrl", user.getPictureUrl(),
                    "isActive", user.getIsActive(),
                    "socialProvider", user.getSocialProvider()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_TOKEN", "message", "유효하지 않은 토큰입니다"));
            }
            
        } catch (Exception e) {
            log.error("Get current user failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "사용자 정보 조회 중 오류가 발생했습니다"));
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "인증 서비스 헬스체크")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "oauth2-authentication-service",
            "version", "2.0",
            "features", Map.of(
                "localAuth", "enabled",
                "socialLogin", "enabled",
                "oauth2", "enabled",
                "redisRateLimit", "enabled"
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}