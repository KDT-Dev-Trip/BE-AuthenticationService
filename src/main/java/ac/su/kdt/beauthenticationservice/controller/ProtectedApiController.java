package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.security.JwtUserDetails;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT 인증이 필요한 보호된 API 엔드포인트들
 */
@Slf4j
@RestController
@RequestMapping("/api/protected")
@RequiredArgsConstructor
@Tag(name = "Protected APIs", description = "JWT 인증이 필요한 보호된 API들")
public class ProtectedApiController {
    
    private final AuthService authService;
    
    @GetMapping("/profile")
    @Operation(
        summary = "사용자 프로필 조회", 
        description = "JWT 토큰을 통해 인증된 사용자의 프로필 정보를 조회합니다",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Map<String, Object>> getUserProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        log.info("Profile requested for user: {}", userDetails.getEmail());
        
        // 데이터베이스에서 사용자 정보 조회
        Optional<User> userOpt = authService.getUserFromToken(userDetails.getRawToken());
        
        Map<String, Object> response = new HashMap<>();
        response.put("user_id", userDetails.getUserId());
        response.put("email", userDetails.getEmail());
        response.put("authenticated_at", LocalDateTime.now());
        response.put("authorities", auth.getAuthorities());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            response.put("user_id", user.getId());
            response.put("name", user.getName());
            response.put("current_tickets", user.getCurrentTickets());
            response.put("role", user.getRole());
            response.put("is_active", user.getIsActive());
            response.put("email_verified", user.getEmailVerified());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/dashboard")
    @Operation(
        summary = "사용자 대시보드 정보",
        description = "인증된 사용자의 대시보드 정보를 반환합니다",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        log.info("Dashboard requested for user: {}", userDetails.getEmail());
        
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("welcome_message", "안녕하세요, " + userDetails.getEmail() + "님!");
        dashboard.put("last_login", LocalDateTime.now());
        dashboard.put("available_features", new String[]{
            "DevOps 워크플로우 관리",
            "실습 환경 프로비저닝",
            "AI 기반 추천 시스템",
            "학습 진도 추적"
        });
        dashboard.put("system_status", "정상 운영");
        dashboard.put("user_permissions", auth.getAuthorities());
        
        return ResponseEntity.ok(dashboard);
    }
    
    @PostMapping("/tickets/use")
    @Operation(
        summary = "티켓 사용",
        description = "사용자의 티켓을 사용합니다 (실습 환경 생성 등)",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Map<String, Object>> useTicket(
            @RequestBody Map<String, String> request
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        String purpose = request.getOrDefault("purpose", "실습 환경 생성");
        
        log.info("Ticket usage requested by user: {} for purpose: {}", 
                userDetails.getEmail(), purpose);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "티켓이 성공적으로 사용되었습니다");
        response.put("purpose", purpose);
        response.put("used_at", LocalDateTime.now());
        response.put("user_email", userDetails.getEmail());
        response.put("remaining_tickets", "조회 중...");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health-check")
    @Operation(
        summary = "인증된 사용자 헬스체크",
        description = "JWT 인증이 정상적으로 작동하는지 확인합니다",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Map<String, Object>> authenticatedHealthCheck() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "authenticated");
        healthInfo.put("timestamp", LocalDateTime.now());
        
        if (auth != null && auth.getPrincipal() instanceof JwtUserDetails) {
            JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
            healthInfo.put("user_id", userDetails.getUserId());
            healthInfo.put("user_email", userDetails.getEmail());
            healthInfo.put("authorities", auth.getAuthorities());
        }
        
        return ResponseEntity.ok(healthInfo);
    }
}