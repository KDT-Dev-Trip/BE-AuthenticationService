package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.SSOTokenService;
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
public class SSOController {
    
    private final SSOTokenService ssoTokenService;
    
    /**
     * JWT 토큰을 SSO 토큰으로 업그레이드
     */
    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, Object>> upgradeToSSO(@RequestBody Map<String, String> request) {
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
    
    /**
     * SSO 토큰 검증
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateSSOToken(@RequestBody Map<String, String> request) {
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
    
    /**
     * 애플리케이션 등록 (SSO 세션에 앱 추가)
     */
    @PostMapping("/register-app")
    public ResponseEntity<Map<String, Object>> registerApplication(@RequestBody Map<String, String> request) {
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
    
    /**
     * SSO 세션 정보 조회
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSessionInfo(@RequestParam String sso_token) {
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
    
    /**
     * SSO 로그아웃 (전체 세션 종료)
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestBody Map<String, String> request) {
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
    
    /**
     * SSO 상태 확인
     */
    @GetMapping("/status")
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