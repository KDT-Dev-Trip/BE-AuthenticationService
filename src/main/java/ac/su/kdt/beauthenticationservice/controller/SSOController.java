package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.SSOTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SSO (Single Sign-On) 인증 컨트롤러
 * 여러 애플리케이션 간 단일 인증 서비스를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "SSO (Single Sign-On)", description = "SSO 인증 및 세션 관리 API")
public class SSOController {
    
    private final SSOTokenService ssoTokenService;
    
    @PostMapping("/upgrade")
    @Operation(
        summary = "JWT 토큰을 SSO 토큰으로 업그레이드",
        description = """
                JWT 토큰을 SSO 토큰으로 변환하여 여러 애플리케이션 간 단일 로그인 기능을 제공합니다.
                SSO 토큰은 8시간 동안 유효하며, 여러 애플리케이션에 등록하여 사용할 수 있습니다.
                """
    )
    public ResponseEntity<Map<String, Object>> upgradeToSSO(
        @Parameter(description = "JWT 토큰을 포함한 요청 데이터") 
        @RequestBody Map<String, String> request) {
        try {
            String jwtToken = request.get("jwt_token");
            
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "JWT token is required",
                    "success", false
                ));
            }
            
            String ssoToken = ssoTokenService.upgradeToSSO(jwtToken);
            
            if (ssoToken != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("sso_token", ssoToken);
                response.put("message", "JWT successfully upgraded to SSO token");
                response.put("expires_in", 28800); // 8 hours in seconds
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to upgrade JWT to SSO token",
                    "success", false
                ));
            }
            
        } catch (Exception e) {
            log.error("Error upgrading to SSO token: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "success", false
            ));
        }
    }
    
    @PostMapping("/validate")
    @Operation(
        summary = "SSO 토큰 검증",
        description = """
                SSO 토큰의 유효성을 검증하고 사용자 정보를 반환합니다.
                유효한 토큰인 경우 사용자 ID, 이메일, 이름, 역할 등의 정보를 포함합니다.
                """
    )
    public ResponseEntity<Map<String, Object>> validateSSOToken(
        @Parameter(description = "SSO 토큰을 포함한 요청 데이터") 
        @RequestBody Map<String, String> request) {
        try {
            String ssoToken = request.get("sso_token");
            
            if (ssoToken == null || ssoToken.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "SSO token is required",
                    "valid", false
                ));
            }
            
            Optional<User> userOpt = ssoTokenService.validateSSOToken(ssoToken);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("valid", true);
                response.put("user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "role", user.getRole().name(),
                    "emailVerified", user.getEmailVerified(),
                    "isActive", user.getIsActive()
                ));
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", "Invalid or expired SSO token"
                ));
            }
            
        } catch (Exception e) {
            log.error("Error validating SSO token: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "valid", false
            ));
        }
    }
    
    @PostMapping("/register-app")
    @Operation(
        summary = "애플리케이션 등록",
        description = """
                SSO 세션에 애플리케이션을 등록하여 단일 로그인 기능을 활성화합니다.
                등록된 애플리케이션들은 SSO 로그아웃 시 함께 로그아웃됩니다.
                """
    )
    public ResponseEntity<Map<String, Object>> registerApplication(
        @Parameter(description = "SSO 토큰, 애플리케이션 ID, 애플리케이션 이름을 포함한 요청 데이터") 
        @RequestBody Map<String, String> request) {
        try {
            String ssoToken = request.get("sso_token");
            String applicationId = request.get("application_id");
            String applicationName = request.get("application_name");
            
            if (ssoToken == null || applicationId == null || applicationName == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "sso_token, application_id, and application_name are required",
                    "success", false
                ));
            }
            
            boolean registered = ssoTokenService.registerApplication(ssoToken, applicationId, applicationName);
            
            if (registered) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Application registered successfully",
                    "application_id", applicationId,
                    "application_name", applicationName
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to register application",
                    "success", false
                ));
            }
            
        } catch (Exception e) {
            log.error("Error registering application: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "success", false
            ));
        }
    }
    
    @GetMapping("/session")
    @Operation(
        summary = "SSO 세션 정보 조회",
        description = """
                SSO 세션에 등록된 모든 애플리케이션 정보와 사용자 데이터를 조회합니다.
                세션 만료 시간, 등록된 애플리케이션 목록 등의 정보를 포함합니다.
                """
    )
    public ResponseEntity<Map<String, Object>> getSessionInfo(
        @Parameter(description = "SSO 세션 정보를 조회할 SSO 토큰", required = true, example = "sso_xxxx1234567890abcdef") 
        @RequestParam String sso_token) {
        try {
            if (sso_token == null || sso_token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "SSO token is required",
                    "success", false
                ));
            }
            
            Map<String, Object> sessionInfo = ssoTokenService.getSessionInfo(sso_token);
            
            if (!sessionInfo.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("session", sessionInfo);
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Session not found or expired"
                ));
            }
            
        } catch (Exception e) {
            log.error("Error getting session info: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "success", false
            ));
        }
    }
    
    @PostMapping("/logout")
    @Operation(
        summary = "SSO 로그아웃",
        description = """
                SSO 세션을 종료하여 모든 등록된 애플리케이션에서 로그아웃합니다.
                이 작업은 되돌릴 수 없으며, 다시 로그인하려면 새로운 JWT 토큰으로 SSO 업그레이드를 수행해야 합니다.
                """
    )
    public ResponseEntity<Map<String, Object>> logout(
        @Parameter(description = "SSO 토큰을 포함한 요청 데이터") 
        @RequestBody Map<String, String> request) {
        try {
            String ssoToken = request.get("sso_token");
            
            if (ssoToken == null || ssoToken.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "SSO token is required",
                    "success", false
                ));
            }
            
            boolean loggedOut = ssoTokenService.logout(ssoToken);
            
            if (loggedOut) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "SSO logout successful"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to logout or token not found",
                    "success", false
                ));
            }
            
        } catch (Exception e) {
            log.error("Error during SSO logout: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "success", false
            ));
        }
    }
    
    @GetMapping("/status")
    @Operation(
        summary = "SSO 서비스 상태 확인",
        description = """
                SSO 서비스의 동작 상태와 사용 가능한 기능들을 확인합니다.
                서비스 상태, 지원 기능, 사용 가능한 엔드포인트 목록을 담고 있습니다.
                """
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "sso-authentication-service");
        response.put("status", "healthy");
        response.put("features", Map.of(
            "sso_validation", "enabled",
            "multi_app_support", "enabled",
            "session_management", "enabled",
            "auto_logout", "enabled"
        ));
        response.put("endpoints", Map.of(
            "POST /sso/upgrade", "JWT를 SSO 토큰으로 업그레이드",
            "POST /sso/validate", "SSO 토큰 검증",
            "POST /sso/register-app", "애플리케이션 등록",
            "GET /sso/session", "세션 정보 조회",
            "POST /sso/logout", "SSO 전체 로그아웃"
        ));
        
        return ResponseEntity.ok(response);
    }
}