package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.model.dto.LoginRequest;
import ac.su.kdt.beauthenticationservice.model.dto.SignupRequest;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequest;
import ac.su.kdt.beauthenticationservice.service.LocalAuthService;
import ac.su.kdt.beauthenticationservice.service.LoginAttemptService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 일반 이메일/비밀번호 기반 인증 API
 * Auth0 소셜 로그인과 별도로 제공되는 자체 인증 시스템
 */
@Slf4j
@RestController
@RequestMapping("/auth/local")
@RequiredArgsConstructor
@Tag(name = "Local Authentication", description = "이메일/비밀번호 기반 자체 인증 API")
public class LocalAuthController {
    
    private final LocalAuthService localAuthService;
    private final LoginAttemptService loginAttemptService;
    private final RedisLoginAttemptService redisLoginAttemptService;
    
    @PostMapping("/signup")
    @Operation(
        summary = "이메일 회원가입",
        description = """
                이메일과 비밀번호를 사용한 일반 회원가입을 처리합니다.
                
                **처리 과정:**
                1. 이메일 중복 검사
                2. 비밀번호 암호화 저장
                3. 이메일 인증 메일 발송
                4. 회원가입 이벤트 발행 (Kafka)
                
                **보안 기능:**
                - 비밀번호는 BCrypt로 암호화
                - 이메일 인증 필요
                - IP 주소 기록
                """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "회원가입 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = """
                    {
                        "success": true,
                        "message": "회원가입이 완료되었습니다. 이메일 인증을 진행해 주세요.",
                        "data": {
                            "userId": "user-uuid-12345",
                            "email": "user@example.com",
                            "requiresEmailVerification": true
                        }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (이메일 중복, 유효성 검사 실패)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = """
                    {
                        "success": false,
                        "message": "이미 가입된 이메일입니다.",
                        "error": "DUPLICATE_EMAIL"
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = """
                    {
                        "success": false,
                        "message": "서버 오류가 발생했습니다.",
                        "error": "INTERNAL_SERVER_ERROR"
                    }
                    """)
            )
        )
    })
    public ResponseEntity<Map<String, Object>> signup(
            @Parameter(
                description = "회원가입 요청 정보",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = """
                            {
                                "email": "user@example.com",
                                "password": "securePassword123!",
                                "name": "홍길동"
                            }
                            """
                    )
                )
            )
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            
            log.info("Local signup attempt for email: {} from IP: {}", request.getEmail(), ipAddress);
            
            // 회원가입 처리
            var result = localAuthService.signup(request, ipAddress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "회원가입이 완료되었습니다.");
            response.put("user_id", result.getUserId());
            response.put("email", result.getEmail());
            response.put("requires_verification", result.isRequiresEmailVerification());
            
            if (result.isRequiresEmailVerification()) {
                response.put("verification_message", "인증 이메일을 발송했습니다. 이메일을 확인해 주세요.");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Local signup failed for email: {}", request.getEmail(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @PostMapping("/login")
    @Operation(
        summary = "이메일 로그인",
        description = "이메일과 비밀번호를 사용한 로그인"
    )
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        // Redis 기반 계정 잠금 확인
        if (redisLoginAttemptService.isAccountLocked(request.getEmail())) {
            var lockInfo = redisLoginAttemptService.getAccountLockInfo(request.getEmail());
            
            log.warn("Login blocked for email: {} from IP: {} - Account is locked until {}", 
                    request.getEmail(), ipAddress, lockInfo.getUnlockTime());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "account_locked");
            errorResponse.put("message", "계정이 잠겨있습니다. 너무 많은 로그인 시도가 감지되었습니다.");
            errorResponse.put("lock_reason", lockInfo.getLockReason());
            errorResponse.put("unlock_time", lockInfo.getUnlockTime());
            errorResponse.put("lock_duration_hours", 1);
            errorResponse.put("remaining_attempts", 0);
            
            return ResponseEntity.status(423).body(errorResponse); // Locked
        }
        
        // IP 기반 로그인 시도 제한 확인 (기존 시스템)
        if (loginAttemptService.isBlocked(ipAddress, request.getEmail())) {
            log.warn("Login blocked for email: {} from IP: {} due to too many attempts", 
                    request.getEmail(), ipAddress);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ip_blocked");
            errorResponse.put("message", "너무 많은 로그인 시도로 인해 IP가 일시적으로 차단되었습니다. 15분 후 다시 시도해 주세요.");
            errorResponse.put("remaining_attempts", 0);
            
            return ResponseEntity.status(429).body(errorResponse); // Too Many Requests
        }
        
        try {
            log.info("Local login attempt for email: {} from IP: {}", request.getEmail(), ipAddress);
            
            // 로그인 처리
            var result = localAuthService.login(request, ipAddress, userAgent);
            
            // 로그인 성공 시 실패 기록 초기화 (두 시스템 모두)
            loginAttemptService.loginSucceeded(ipAddress, request.getEmail());
            redisLoginAttemptService.recordLoginAttempt(request.getEmail(), ipAddress, true);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "로그인이 완료되었습니다.");
            response.put("access_token", result.getAccessToken());
            response.put("refresh_token", result.getRefreshToken());
            response.put("expires_in", result.getExpiresIn());
            response.put("user_info", result.getUserInfo());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Local login failed for email: {} from IP: {}", request.getEmail(), ipAddress, e);
            
            // 로그인 실패 기록 (두 시스템 모두)
            loginAttemptService.loginFailed(ipAddress, request.getEmail());
            redisLoginAttemptService.recordLoginAttempt(request.getEmail(), ipAddress, false);
            
            // Redis 기반 남은 시도 횟수 확인
            int remainingAttempts = redisLoginAttemptService.getRemainingAttempts(request.getEmail());
            var lockInfo = redisLoginAttemptService.getAccountLockInfo(request.getEmail());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("remaining_attempts", remainingAttempts);
            errorResponse.put("current_attempts", redisLoginAttemptService.getCurrentLoginAttempts(request.getEmail()));
            errorResponse.put("max_attempts", 10);
            
            if (remainingAttempts <= 3 && remainingAttempts > 0) {
                errorResponse.put("warning", "경고: " + remainingAttempts + "회 더 실패하면 계정이 1시간 동안 잠깁니다.");
            }
            
            if (lockInfo.isLocked()) {
                errorResponse.put("account_locked", true);
                errorResponse.put("unlock_time", lockInfo.getUnlockTime());
                return ResponseEntity.status(423).body(errorResponse); // Locked
            }
            
            return ResponseEntity.status(401).body(errorResponse);
        }
    }
    
    @PostMapping("/password-reset/request")
    @Operation(
        summary = "비밀번호 재설정 요청",
        description = "이메일을 통한 비밀번호 재설정 링크 발송"
    )
    public ResponseEntity<Map<String, Object>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            
            log.info("Password reset requested for email: {} from IP: {}", request.getEmail(), ipAddress);
            
            // 비밀번호 재설정 처리
            localAuthService.requestPasswordReset(request.getEmail(), ipAddress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "비밀번호 재설정 이메일을 발송했습니다. 이메일을 확인해 주세요.");
            response.put("note", "이메일이 도착하지 않았다면 스팸함을 확인하거나 5분 후 다시 시도해 주세요.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Password reset request failed for email: {}", request.getEmail(), e);
            
            // 보안상 이메일 존재 여부를 알려주지 않음
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "해당 이메일로 비밀번호 재설정 링크를 발송했습니다.");
            
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/password-reset/confirm")
    @Operation(
        summary = "비밀번호 재설정 확인",
        description = "재설정 토큰을 사용한 비밀번호 변경"
    )
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(
            @RequestParam String token,
            @RequestParam String newPassword,
            HttpServletRequest httpRequest
    ) {
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            
            log.info("Password reset confirmation attempt from IP: {}", ipAddress);
            
            // 비밀번호 재설정 확인 처리
            localAuthService.confirmPasswordReset(token, newPassword, ipAddress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "비밀번호가 성공적으로 변경되었습니다.");
            response.put("note", "새 비밀번호로 다시 로그인해 주세요.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Password reset confirmation failed from IP: {}", getClientIpAddress(httpRequest), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "액세스 토큰을 무효화하고 로그아웃 처리"
    )
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            HttpServletRequest httpRequest
    ) {
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            
            // Authorization 헤더에서 토큰 추출
            String token = null;
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
            }
            
            if (token == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "인증 토큰이 필요합니다.");
                
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            log.info("Logout attempt from IP: {}", ipAddress);
            
            // 로그아웃 처리
            localAuthService.logout(token, ipAddress);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "로그아웃이 완료되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Logout failed from IP: {}", getClientIpAddress(httpRequest), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 클라이언트 IP 주소를 추출합니다.
     */
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