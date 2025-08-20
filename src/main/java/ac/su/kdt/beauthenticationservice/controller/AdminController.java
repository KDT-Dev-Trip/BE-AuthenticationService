package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 API 컨트롤러
 * 계정 잠금 해제, 로그인 시도 통계 등을 관리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin APIs", description = "관리자용 API들")
public class AdminController {
    
    private final RedisLoginAttemptService redisLoginAttemptService;
    
    @GetMapping("/login-attempts/stats")
    @Operation(
        summary = "로그인 시도 통계 조회",
        description = "전체 로그인 시도 및 계정 잠금 통계를 조회합니다"
    )
    public ResponseEntity<RedisLoginAttemptService.LoginAttemptStats> getLoginAttemptStats() {
        var stats = redisLoginAttemptService.getLoginAttemptStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/account/{email}/lock-info")
    @Operation(
        summary = "계정 잠금 정보 조회",
        description = "특정 계정의 잠금 상태 및 로그인 시도 정보를 조회합니다"
    )
    public ResponseEntity<RedisLoginAttemptService.AccountLockInfo> getAccountLockInfo(
            @PathVariable String email
    ) {
        var lockInfo = redisLoginAttemptService.getAccountLockInfo(email);
        return ResponseEntity.ok(lockInfo);
    }
    
    @PostMapping("/account/{email}/unlock")
    @Operation(
        summary = "계정 잠금 해제",
        description = """
                관리자가 수동으로 계정 잠금을 해제합니다.
                
                **적용 범위:**
                - 로컬 인증 (이메일/비밀번호 로그인)
                - Auth0 인증 (소셜 로그인)
                - 모든 로그인 방식에 통합 적용
                
                **해제 효과:**
                - Redis 기반 로그인 실패 카운트 초기화
                - 계정 잠금 상태 해제
                - IP 기반 의심 활동 기록 초기화
                """
    )
    public ResponseEntity<Map<String, Object>> unlockAccount(
            @PathVariable String email,
            @RequestParam(defaultValue = "admin") String adminUser
    ) {
        try {
            redisLoginAttemptService.unlockAccount(email, adminUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "계정 잠금이 해제되었습니다.");
            response.put("email", email);
            response.put("admin_user", adminUser);
            
            log.info("Account {} unlocked by admin: {}", email, adminUser);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to unlock account: {}", email, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "계정 잠금 해제에 실패했습니다: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/security/dashboard")
    @Operation(
        summary = "보안 대시보드",
        description = "전체 보안 상황을 요약한 대시보드 정보를 제공합니다"
    )
    public ResponseEntity<Map<String, Object>> getSecurityDashboard() {
        var stats = redisLoginAttemptService.getLoginAttemptStats();
        
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("login_attempt_stats", stats);
        dashboard.put("security_alerts", Map.of(
            "high_priority", stats.getTotalLockedAccounts(),
            "medium_priority", stats.getTotalAccountsWithFailedAttempts(),
            "low_priority", stats.getTotalSuspiciousIPs()
        ));
        dashboard.put("system_status", Map.of(
            "redis_connection", "healthy",
            "login_protection", "active",
            "max_attempts_threshold", stats.getMaxAttemptsThreshold(),
            "lock_duration_hours", stats.getLockDurationHours()
        ));
        
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/accounts/locked")
    @Operation(
        summary = "잠긴 계정 목록 조회",
        description = "현재 잠금 상태인 모든 계정의 상세 정보를 조회합니다"
    )
    public ResponseEntity<List<RedisLoginAttemptService.LockedAccountInfo>> getLockedAccounts() {
        var lockedAccounts = redisLoginAttemptService.getLockedAccounts();
        return ResponseEntity.ok(lockedAccounts);
    }
    
    @GetMapping("/accounts/failed")
    @Operation(
        summary = "실패 시도 계정 목록 조회",
        description = "로그인 실패 시도가 있는 모든 계정의 정보를 조회합니다"
    )
    public ResponseEntity<List<RedisLoginAttemptService.FailedAttemptAccountInfo>> getAccountsWithFailedAttempts() {
        var failedAccounts = redisLoginAttemptService.getAccountsWithFailedAttempts();
        return ResponseEntity.ok(failedAccounts);
    }
    
    @GetMapping("/ips/suspicious")
    @Operation(
        summary = "의심스러운 IP 목록 조회",
        description = "의심스러운 활동을 보이는 IP 주소와 관련 정보를 조회합니다"
    )
    public ResponseEntity<List<RedisLoginAttemptService.SuspiciousIPInfo>> getSuspiciousIPs() {
        var suspiciousIPs = redisLoginAttemptService.getSuspiciousIPsWithDetails();
        return ResponseEntity.ok(suspiciousIPs);
    }
    
    @GetMapping("/accounts/all")
    @Operation(
        summary = "모든 계정 보안 상태 요약",
        description = "Redis에 기록된 모든 계정의 보안 상태를 요약하여 조회합니다"
    )
    public ResponseEntity<List<RedisLoginAttemptService.AccountSecuritySummary>> getAllAccountsSecuritySummary() {
        var summaries = redisLoginAttemptService.getAllAccountsSecuritySummary();
        return ResponseEntity.ok(summaries);
    }
    
    @GetMapping("/accounts/overview")
    @Operation(
        summary = "계정 보안 상태 개요",
        description = "전체 계정 보안 상태의 개요와 각 카테고리별 상세 정보를 제공합니다"
    )
    public ResponseEntity<Map<String, Object>> getAccountsOverview() {
        var lockedAccounts = redisLoginAttemptService.getLockedAccounts();
        var failedAccounts = redisLoginAttemptService.getAccountsWithFailedAttempts();
        var suspiciousIPs = redisLoginAttemptService.getSuspiciousIPsWithDetails();
        var allAccounts = redisLoginAttemptService.getAllAccountsSecuritySummary();
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("summary", Map.of(
            "total_accounts_tracked", allAccounts.size(),
            "locked_accounts", lockedAccounts.size(),
            "accounts_with_failed_attempts", failedAccounts.size(),
            "suspicious_ips", suspiciousIPs.size(),
            "accounts_near_lock_threshold", 
                failedAccounts.stream().mapToInt(acc -> acc.isNearLockThreshold() ? 1 : 0).sum()
        ));
        overview.put("locked_accounts", lockedAccounts);
        overview.put("failed_attempt_accounts", failedAccounts);
        overview.put("suspicious_ips", suspiciousIPs);
        overview.put("high_risk_alerts", Map.of(
            "accounts_near_lock", 
                failedAccounts.stream().filter(acc -> acc.isNearLockThreshold()).toList(),
            "high_risk_ips", 
                suspiciousIPs.stream().filter(ip -> ip.isHighRisk()).toList()
        ));
        
        return ResponseEntity.ok(overview);
    }
}