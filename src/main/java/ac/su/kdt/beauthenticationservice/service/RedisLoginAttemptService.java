package ac.su.kdt.beauthenticationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 로그인 시도 제한 서비스
 * 특정 아이디에 대해 로그인 시도를 추적하고 제한합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLoginAttemptService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // 설정값들
    private static final int MAX_LOGIN_ATTEMPTS = 10; // 최대 로그인 시도 횟수
    private static final int LOCK_DURATION_HOURS = 1; // 계정 잠금 시간 (시간)
    private static final int ATTEMPT_WINDOW_MINUTES = 60; // 시도 횟수 추적 시간 윈도우 (분)
    private static final int WARNING_THRESHOLD = 5; // 경고 임계값
    
    // Redis 키 패턴들
    private static final String LOGIN_ATTEMPTS_KEY = "login_attempts:";
    private static final String ACCOUNT_LOCK_KEY = "account_lock:";
    private static final String LOGIN_HISTORY_KEY = "login_history:";
    private static final String SUSPICIOUS_IP_KEY = "suspicious_ip:";
    
    /**
     * 로그인 시도를 기록합니다
     */
    public void recordLoginAttempt(String email, String ipAddress, boolean success) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        
        if (success) {
            // 로그인 성공 시 모든 실패 기록 초기화
            clearLoginAttempts(email);
            log.info("Login successful for {}, cleared all failed attempts", email);
            
            // 성공 기록 저장
            String historyKey = LOGIN_HISTORY_KEY + email;
            String historyValue = String.format("SUCCESS:%s:%s", ipAddress, timestamp);
            ops.set(historyKey, historyValue, ATTEMPT_WINDOW_MINUTES, TimeUnit.MINUTES);
            
        } else {
            // 로그인 실패 시 시도 횟수 증가
            String attemptsKey = LOGIN_ATTEMPTS_KEY + email;
            String currentAttempts = ops.get(attemptsKey);
            
            int attempts = currentAttempts != null ? Integer.parseInt(currentAttempts) : 0;
            attempts++;
            
            // Redis에 실패 횟수 저장 (1시간 후 만료)
            ops.set(attemptsKey, String.valueOf(attempts), ATTEMPT_WINDOW_MINUTES, TimeUnit.MINUTES);
            
            // 실패 기록 저장
            String historyKey = LOGIN_HISTORY_KEY + email + ":" + timestamp;
            String historyValue = String.format("FAILURE:%s:%s:attempt_%d", ipAddress, timestamp, attempts);
            ops.set(historyKey, historyValue, ATTEMPT_WINDOW_MINUTES, TimeUnit.MINUTES);
            
            log.warn("Login failed for {} from IP: {} (attempt {}/{})", 
                    email, ipAddress, attempts, MAX_LOGIN_ATTEMPTS);
            
            // 최대 시도 횟수 초과 시 계정 잠금
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                lockAccount(email, ipAddress);
            } else if (attempts >= WARNING_THRESHOLD) {
                // 경고 임계값 도달 시 알림
                log.warn("Warning: Account {} approaching lock threshold ({}/{})", 
                        email, attempts, MAX_LOGIN_ATTEMPTS);
            }
            
            // 의심스러운 IP 추적
            trackSuspiciousIP(ipAddress, email);
        }
    }
    
    /**
     * 계정이 잠겨있는지 확인합니다
     */
    public boolean isAccountLocked(String email) {
        String lockKey = ACCOUNT_LOCK_KEY + email;
        String lockInfo = redisTemplate.opsForValue().get(lockKey);
        
        boolean isLocked = lockInfo != null;
        if (isLocked) {
            log.debug("Account {} is locked: {}", email, lockInfo);
        }
        
        return isLocked;
    }
    
    /**
     * 현재 로그인 시도 횟수를 반환합니다
     */
    public int getCurrentLoginAttempts(String email) {
        String attemptsKey = LOGIN_ATTEMPTS_KEY + email;
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        return attempts != null ? Integer.parseInt(attempts) : 0;
    }
    
    /**
     * 남은 시도 횟수를 반환합니다
     */
    public int getRemainingAttempts(String email) {
        if (isAccountLocked(email)) {
            return 0;
        }
        
        int currentAttempts = getCurrentLoginAttempts(email);
        return Math.max(0, MAX_LOGIN_ATTEMPTS - currentAttempts);
    }
    
    /**
     * 계정 잠금 정보를 반환합니다
     */
    public AccountLockInfo getAccountLockInfo(String email) {
        String lockKey = ACCOUNT_LOCK_KEY + email;
        String lockInfo = redisTemplate.opsForValue().get(lockKey);
        
        if (lockInfo == null) {
            return AccountLockInfo.builder()
                    .locked(false)
                    .remainingAttempts(getRemainingAttempts(email))
                    .build();
        }
        
        String[] parts = lockInfo.split(":");
        LocalDateTime lockTime = LocalDateTime.parse(parts[2]);
        LocalDateTime unlockTime = lockTime.plusHours(LOCK_DURATION_HOURS);
        
        return AccountLockInfo.builder()
                .locked(true)
                .lockReason(parts[0])
                .lockTime(lockTime)
                .unlockTime(unlockTime)
                .lockingIP(parts[1])
                .remainingAttempts(0)
                .build();
    }
    
    /**
     * 계정을 잠급니다
     */
    private void lockAccount(String email, String ipAddress) {
        String lockKey = ACCOUNT_LOCK_KEY + email;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String lockValue = String.format("MAX_ATTEMPTS_EXCEEDED:%s:%s", ipAddress, timestamp);
        
        // 계정을 지정된 시간 동안 잠금
        redisTemplate.opsForValue().set(lockKey, lockValue, LOCK_DURATION_HOURS, TimeUnit.HOURS);
        
        log.error("Account {} has been locked for {} hours due to {} failed login attempts from IP: {}", 
                email, LOCK_DURATION_HOURS, MAX_LOGIN_ATTEMPTS, ipAddress);
        
        // 관리자 알림을 위한 추가 로깅 (실제 환경에서는 이메일/Slack 알림 등)
        log.error("SECURITY_ALERT: Account lockout - Email: {}, IP: {}, Time: {}", 
                email, ipAddress, timestamp);
    }
    
    /**
     * 로그인 시도 기록을 초기화합니다
     */
    private void clearLoginAttempts(String email) {
        String attemptsKey = LOGIN_ATTEMPTS_KEY + email;
        redisTemplate.delete(attemptsKey);
    }
    
    /**
     * 의심스러운 IP를 추적합니다
     */
    private void trackSuspiciousIP(String ipAddress, String targetEmail) {
        String suspiciousKey = SUSPICIOUS_IP_KEY + ipAddress;
        String currentValue = redisTemplate.opsForValue().get(suspiciousKey);
        
        int suspiciousCount = currentValue != null ? Integer.parseInt(currentValue.split(":")[0]) : 0;
        suspiciousCount++;
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String newValue = suspiciousCount + ":" + targetEmail + ":" + timestamp;
        
        redisTemplate.opsForValue().set(suspiciousKey, newValue, ATTEMPT_WINDOW_MINUTES, TimeUnit.MINUTES);
        
        // 의심스러운 IP에서 여러 계정 대상 공격 시 경고
        if (suspiciousCount >= 20) {
            log.error("SECURITY_ALERT: Suspicious IP {} has attempted login {} times, latest target: {}", 
                    ipAddress, suspiciousCount, targetEmail);
        }
    }
    
    /**
     * 계정 잠금을 수동으로 해제합니다 (관리자 기능)
     */
    public void unlockAccount(String email, String adminUser) {
        String lockKey = ACCOUNT_LOCK_KEY + email;
        String attemptsKey = LOGIN_ATTEMPTS_KEY + email;
        
        redisTemplate.delete(lockKey);
        redisTemplate.delete(attemptsKey);
        
        log.info("Account {} manually unlocked by admin: {}", email, adminUser);
    }
    
    /**
     * 로그인 실패를 기록합니다 (별도 메서드)
     */
    public void recordFailedAttempt(String email, String ipAddress) {
        recordLoginAttempt(email, ipAddress, false);
    }
    
    /**
     * 로그인 성공을 기록합니다 (별도 메서드)
     */
    public void recordSuccessfulLogin(String email) {
        recordLoginAttempt(email, "unknown", true);
    }
    
    /**
     * 현재 실패 횟수를 반환합니다
     */
    public int getFailedAttempts(String email) {
        String attemptsKey = LOGIN_ATTEMPTS_KEY + email;
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        return attempts != null ? Integer.parseInt(attempts) : 0;
    }
    
    /**
     * 계정 잠금 정보를 Map 형태로 반환합니다 (AuthController용)
     */
    public Map<String, Object> getAccountLockInfoAsMap(String email) {
        Map<String, Object> info = new HashMap<>();
        String lockKey = ACCOUNT_LOCK_KEY + email;
        
        String lockData = redisTemplate.opsForValue().get(lockKey);
        boolean isLocked = lockData != null;
        
        info.put("isLocked", isLocked);
        info.put("failedAttempts", getFailedAttempts(email));
        
        if (isLocked) {
            // 잠금 해제 시간 계산 (현재 시간 + 1시간)
            long lockExpiresAt = System.currentTimeMillis() + (LOCK_DURATION_HOURS * 60 * 60 * 1000);
            info.put("lockExpiresAt", lockExpiresAt);
        }
        
        return info;
    }
    
    /**
     * 의심스러운 IP 주소 목록을 반환합니다
     */
    public Map<String, Integer> getSuspiciousIpAddresses() {
        Map<String, Integer> suspiciousIps = new HashMap<>();
        var keys = redisTemplate.keys(SUSPICIOUS_IP_KEY + "*");
        
        if (keys != null) {
            for (String key : keys) {
                String ip = key.replace(SUSPICIOUS_IP_KEY, "");
                String count = redisTemplate.opsForValue().get(key);
                suspiciousIps.put(ip, count != null ? Integer.parseInt(count) : 0);
            }
        }
        
        return suspiciousIps;
    }
    
    /**
     * 보안 통계를 Map 형태로 반환합니다
     */
    public Map<String, Object> getSecurityStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        var lockedAccounts = redisTemplate.keys(ACCOUNT_LOCK_KEY + "*");
        var failedAttempts = redisTemplate.keys(LOGIN_ATTEMPTS_KEY + "*");
        var suspiciousIPs = redisTemplate.keys(SUSPICIOUS_IP_KEY + "*");
        
        // 총 실패 시도 횟수 계산
        int totalFailedAttempts = 0;
        if (failedAttempts != null) {
            for (String key : failedAttempts) {
                String count = redisTemplate.opsForValue().get(key);
                totalFailedAttempts += count != null ? Integer.parseInt(count) : 0;
            }
        }
        
        stats.put("lockedAccounts", lockedAccounts != null ? lockedAccounts.size() : 0);
        stats.put("totalFailedAttempts", totalFailedAttempts);
        stats.put("suspiciousIpCount", suspiciousIPs != null ? suspiciousIPs.size() : 0);
        
        return stats;
    }
    
    /**
     * 전체 로그인 시도 통계를 반환합니다
     */
    public LoginAttemptStats getLoginAttemptStats() {
        // Redis 패턴 매칭으로 통계 수집
        var lockedAccounts = redisTemplate.keys(ACCOUNT_LOCK_KEY + "*");
        var failedAttempts = redisTemplate.keys(LOGIN_ATTEMPTS_KEY + "*");
        var suspiciousIPs = redisTemplate.keys(SUSPICIOUS_IP_KEY + "*");
        
        return LoginAttemptStats.builder()
                .totalLockedAccounts(lockedAccounts != null ? lockedAccounts.size() : 0)
                .totalAccountsWithFailedAttempts(failedAttempts != null ? failedAttempts.size() : 0)
                .totalSuspiciousIPs(suspiciousIPs != null ? suspiciousIPs.size() : 0)
                .maxAttemptsThreshold(MAX_LOGIN_ATTEMPTS)
                .lockDurationHours(LOCK_DURATION_HOURS)
                .build();
    }
    
    /**
     * 계정 잠금 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class AccountLockInfo {
        private boolean locked;
        private String lockReason;
        private LocalDateTime lockTime;
        private LocalDateTime unlockTime;
        private String lockingIP;
        private int remainingAttempts;
    }
    
    /**
     * 잠긴 계정 목록을 반환합니다
     */
    public List<LockedAccountInfo> getLockedAccounts() {
        List<LockedAccountInfo> lockedAccounts = new ArrayList<>();
        var keys = redisTemplate.keys(ACCOUNT_LOCK_KEY + "*");
        
        if (keys != null) {
            for (String key : keys) {
                String email = key.replace(ACCOUNT_LOCK_KEY, "");
                String lockData = redisTemplate.opsForValue().get(key);
                
                if (lockData != null && lockData.contains(":")) {
                    String[] parts = lockData.split(":");
                    if (parts.length >= 3) {
                        LocalDateTime lockTime = LocalDateTime.parse(parts[2]);
                        LocalDateTime unlockTime = lockTime.plusHours(LOCK_DURATION_HOURS);
                    
                        lockedAccounts.add(LockedAccountInfo.builder()
                                .email(email)
                                .lockReason(parts[0])
                                .lockingIP(parts[1])
                                .lockTime(lockTime)
                                .unlockTime(unlockTime)
                                .build());
                    }
                }
            }
        }
        
        return lockedAccounts;
    }
    
    /**
     * 실패 시도가 있는 계정 목록을 반환합니다
     */
    public List<FailedAttemptAccountInfo> getAccountsWithFailedAttempts() {
        List<FailedAttemptAccountInfo> failedAccounts = new ArrayList<>();
        var keys = redisTemplate.keys(LOGIN_ATTEMPTS_KEY + "*");
        
        if (keys != null) {
            for (String key : keys) {
                String email = key.replace(LOGIN_ATTEMPTS_KEY, "");
                String attempts = redisTemplate.opsForValue().get(key);
                
                if (attempts != null) {
                    int attemptCount = Integer.parseInt(attempts);
                    failedAccounts.add(FailedAttemptAccountInfo.builder()
                            .email(email)
                            .failedAttempts(attemptCount)
                            .remainingAttempts(MAX_LOGIN_ATTEMPTS - attemptCount)
                            .isNearLockThreshold(attemptCount >= WARNING_THRESHOLD)
                            .build());
                }
            }
        }
        
        return failedAccounts;
    }
    
    /**
     * 의심스러운 IP와 관련 정보를 반환합니다
     */
    public List<SuspiciousIPInfo> getSuspiciousIPsWithDetails() {
        List<SuspiciousIPInfo> suspiciousIPs = new ArrayList<>();
        var keys = redisTemplate.keys(SUSPICIOUS_IP_KEY + "*");
        
        if (keys != null) {
            for (String key : keys) {
                String ipAddress = key.replace(SUSPICIOUS_IP_KEY, "");
                String data = redisTemplate.opsForValue().get(key);
                
                if (data != null && data.contains(":")) {
                    String[] parts = data.split(":");
                    if (parts.length >= 3) {
                        int suspiciousCount = Integer.parseInt(parts[0]);
                        String targetEmail = parts[1];
                        LocalDateTime lastAttempt = LocalDateTime.parse(parts[2]);
                    
                        suspiciousIPs.add(SuspiciousIPInfo.builder()
                                .ipAddress(ipAddress)
                                .suspiciousCount(suspiciousCount)
                                .lastTargetEmail(targetEmail)
                                .lastAttemptTime(lastAttempt)
                                .isHighRisk(suspiciousCount >= 20)
                                .build());
                    }
                }
            }
        }
        
        return suspiciousIPs;
    }
    
    /**
     * 모든 계정의 보안 상태 요약을 반환합니다
     */
    public List<AccountSecuritySummary> getAllAccountsSecuritySummary() {
        List<AccountSecuritySummary> summaries = new ArrayList<>();
        Set<String> allEmails = new HashSet<>();
        
        // 잠긴 계정들 수집
        var lockedKeys = redisTemplate.keys(ACCOUNT_LOCK_KEY + "*");
        if (lockedKeys != null) {
            for (String key : lockedKeys) {
                allEmails.add(key.replace(ACCOUNT_LOCK_KEY, ""));
            }
        }
        
        // 실패 시도가 있는 계정들 수집
        var failedKeys = redisTemplate.keys(LOGIN_ATTEMPTS_KEY + "*");
        if (failedKeys != null) {
            for (String key : failedKeys) {
                allEmails.add(key.replace(LOGIN_ATTEMPTS_KEY, ""));
            }
        }
        
        // 각 계정의 상태 정보 수집
        for (String email : allEmails) {
            AccountLockInfo lockInfo = getAccountLockInfo(email);
            int failedAttempts = getCurrentLoginAttempts(email);
            
            summaries.add(AccountSecuritySummary.builder()
                    .email(email)
                    .isLocked(lockInfo.isLocked())
                    .failedAttempts(failedAttempts)
                    .remainingAttempts(lockInfo.getRemainingAttempts())
                    .lockTime(lockInfo.getLockTime())
                    .unlockTime(lockInfo.getUnlockTime())
                    .lockingIP(lockInfo.getLockingIP())
                    .isNearLockThreshold(failedAttempts >= WARNING_THRESHOLD && !lockInfo.isLocked())
                    .build());
        }
        
        return summaries;
    }

    /**
     * 로그인 시도 통계 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class LoginAttemptStats {
        private int totalLockedAccounts;
        private int totalAccountsWithFailedAttempts;
        private int totalSuspiciousIPs;
        private int maxAttemptsThreshold;
        private int lockDurationHours;
    }
    
    /**
     * 잠긴 계정 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class LockedAccountInfo {
        private String email;
        private String lockReason;
        private String lockingIP;
        private LocalDateTime lockTime;
        private LocalDateTime unlockTime;
    }
    
    /**
     * 실패 시도 계정 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class FailedAttemptAccountInfo {
        private String email;
        private int failedAttempts;
        private int remainingAttempts;
        private boolean isNearLockThreshold;
    }
    
    /**
     * 의심스러운 IP 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class SuspiciousIPInfo {
        private String ipAddress;
        private int suspiciousCount;
        private String lastTargetEmail;
        private LocalDateTime lastAttemptTime;
        private boolean isHighRisk;
    }
    
    /**
     * 계정 보안 상태 요약 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class AccountSecuritySummary {
        private String email;
        private boolean isLocked;
        private int failedAttempts;
        private int remainingAttempts;
        private LocalDateTime lockTime;
        private LocalDateTime unlockTime;
        private String lockingIP;
        private boolean isNearLockThreshold;
    }
}