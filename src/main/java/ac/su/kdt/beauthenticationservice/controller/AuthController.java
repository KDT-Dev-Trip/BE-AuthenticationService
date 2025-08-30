package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.client.UserManagementServiceClient;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
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
    // TODO: MSA 통합 - User Management Service 클라이언트 추가
    private final UserManagementServiceClient userManagementServiceClient;
    
    @GetMapping("/test")
    @Operation(summary = "Authentication Service Test", description = "인증 서비스 동작 확인 및 사용 가능한 엔드포인트 조회")
    public Mono<Map<String, Object>> testAuth() {
        return Mono.just(Map.of(
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
            ServerWebExchange exchange) {
        
        String ipAddress = getClientIpAddress(exchange);
        
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
            ServerWebExchange exchange) {
        
        String ipAddress = getClientIpAddress(exchange);
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
            
            // TODO: MSA 통합 - User Management Service에서 실제 User ID (Long) 조회
            Long realUserId = null;
            try {
                // 먼저 기존 사용자 ID 조회 시도
                realUserId = userManagementServiceClient.getUserIdByAuthUserId(user.getId());
                
                // 사용자가 User Management Service에 없으면 생성/동기화
                if (realUserId == null) {
                    log.info("User not found in User Management Service, creating/syncing: authUserId={}", user.getId());
                    realUserId = userManagementServiceClient.createOrSyncUser(
                        user.getId(), 
                        user.getEmail(), 
                        user.getName(), 
                        user.getRole().toString()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to get/create user in User Management Service for authUserId: {}", user.getId(), e);
                // User Management Service 호출 실패 시 fallback으로 기본 JWT 토큰 생성
            }
            
            // JWT 토큰 생성 - 실제 User ID 포함
            String accessToken;
            if (realUserId != null) {
                accessToken = jwtService.generateAccessTokenWithRealUserId(
                    user.getId(), realUserId, user.getEmail(), user.getRole().toString());
                log.info("JWT 토큰 생성 완료 - authUserId: {}, realUserId: {}", user.getId(), realUserId);
            } else {
                // Fallback: 기존 방식으로 토큰 생성 (주석 처리된 기존 코드)
                // accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
                accessToken = jwtService.generateAccessTokenWithRealUserId(
                    user.getId(), null, user.getEmail(), user.getRole().toString());
                log.warn("User Management Service 연결 실패로 realUserId 없이 JWT 토큰 생성: authUserId={}", user.getId());
            }
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
    @Operation(
        summary = "Refresh Token", 
        description = """
                Refresh Token을 사용하여 새로운 Access Token을 발급합니다.
                Access Token이 만료되었을 때 사용자의 재로그인 없이 새 토큰을 발급할 수 있습니다.
                """
    )
    public ResponseEntity<?> refreshToken(
            @Parameter(
                description = "Refresh Token을 포함한 요청 데이터", 
                example = "{\"refresh_token\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\"}"
            ) 
            @RequestBody Map<String, String> request) {
        
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
    @Operation(
        summary = "Password Reset Request", 
        description = """
                비밀번호 재설정 요청을 처리합니다.
                유효한 이메일인 경우 비밀번호 재설정 링크가 이메일로 발송됩니다.
                보안상 이메일 존재 여부에 관계없이 항상 성공 응늵을 반환합니다.
                """
    )
    public ResponseEntity<?> requestPasswordReset(
            @Parameter(description = "비밀번호 재설정을 요청할 이메일 주소") 
            @RequestBody Map<String, String> request,
            ServerWebExchange exchange) {
        
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email is required"));
            }
            
            String ipAddress = getClientIpAddress(exchange);
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
    @Operation(
        summary = "JWT Token Validation", 
        description = """
                JWT 토큰의 유효성을 검증하고 사용자 정보를 반환합니다.
                마이크로서비스 간 토큰 검증 및 인가에 사용됩니다.
                """
    )
    public ResponseEntity<?> validateToken(
        @Parameter(description = "JWT 토큰을 포함한 검증 요청 데이터") 
        @RequestBody Map<String, String> request) {
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
            @Parameter(
                description = "Bearer JWT Token - 'Bearer ' 접두어를 포함한 JWT 토큰", 
                required = true, 
                example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            ) 
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_HEADER", "message", "유효하지 않은 Authorization 헤더입니다"));
            }
            
            String token = authHeader.substring(7);
            Optional<User> userOpt = authService.getUserFromToken(token);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("id", user.getId());
                response.put("email", user.getEmail());
                response.put("name", user.getName());
                response.put("role", user.getRole().toString());
                response.put("tickets", user.getCurrentTickets());
                response.put("emailVerified", user.getEmailVerified());
                response.put("pictureUrl", user.getPictureUrl());
                response.put("isActive", user.getIsActive());
                response.put("socialProvider", user.getSocialProvider());
                return ResponseEntity.ok(response);
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
    
    @PostMapping("/sync-users")
    @Operation(
        summary = "Sync Users to Other Services", 
        description = """
                모든 사용자 데이터를 다른 서비스와 동기화합니다.
                """
    )
    public ResponseEntity<?> syncUsers() {
        try {
            // 전체 사용자 동기화 실행
            int syncedCount = authService.syncAllUsersToServices();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "사용자 동기화가 완료되었습니다",
                "syncedCount", syncedCount
            ));
            
        } catch (Exception e) {
            log.error("User sync failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SYNC_FAILED", "message", "사용자 동기화 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "User Logout", description = "사용자 로그아웃 처리")
    public ResponseEntity<?> logout(
            @Parameter(description = "로그아웃 요청 데이터") @RequestBody(required = false) Map<String, String> request,
            @Parameter(
                description = "Bearer JWT Token - 'Bearer ' 접두어를 포함한 JWT 토큰", 
                required = true, 
                example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            ) 
            @RequestHeader("Authorization") String authHeader,
            ServerWebExchange exchange) {
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_HEADER", "message", "유효하지 않은 Authorization 헤더입니다"));
            }
            
            String token = authHeader.substring(7);
            String ipAddress = getClientIpAddress(exchange);
            String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
            String logoutReason = request != null ? request.get("reason") : "USER_LOGOUT";
            
            Optional<User> userOpt = authService.getUserFromToken(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_TOKEN", "message", "유효하지 않은 토큰입니다"));
            }
            
            User user = userOpt.get();
            
            // 로그아웃 처리 및 이벤트 발행
            authService.logoutUser(user.getId(), "session_" + System.currentTimeMillis(), ipAddress, logoutReason);
            
            log.info("User logged out: {} from IP: {}", user.getEmail(), ipAddress);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "로그아웃이 완료되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("Logout error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "로그아웃 처리 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "인증 서비스 헬스체크")
    public Mono<Map<String, Object>> healthCheck() {
        return Mono.just(Map.of(
            "status", "healthy",
            "service", "oauth2-authentication-service", 
            "version", "2.0",
            "deprecated", "Use /api/health instead",
            "features", Map.of(
                "localAuth", "enabled",
                "socialLogin", "enabled",
                "oauth2", "enabled",
                "redisRateLimit", "enabled"
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    
    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null 
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
            : "unknown";
    }
}