package ac.su.kdt.beauthenticationservice.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 로그인 시도 횟수 제한을 위한 서비스
 * 특정 IP 주소나 사용자에 대한 로그인 실패 횟수를 추적하고 제한합니다.
 */
@Slf4j
@Service
public class LoginAttemptService {
    
    private static final int MAX_ATTEMPTS = 5; // 최대 로그인 시도 횟수
    private static final int ATTEMPT_INCREMENT = 1;
    private static final int LOCK_TIME_DURATION = 15; // 잠금 시간 (분)
    
    // IP 기반 로그인 시도 추적 캐시
    private final LoadingCache<String, Integer> ipAttemptsCache;
    
    // 사용자 기반 로그인 시도 추적 캐시
    private final LoadingCache<String, Integer> userAttemptsCache;
    
    public LoginAttemptService() {
        super();
        
        // IP 기반 캐시 초기화
        ipAttemptsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(LOCK_TIME_DURATION, TimeUnit.MINUTES)
                .build(new CacheLoader<String, Integer>() {
                    @Override
                    public Integer load(String key) {
                        return 0;
                    }
                });
        
        // 사용자 기반 캐시 초기화
        userAttemptsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(LOCK_TIME_DURATION, TimeUnit.MINUTES)
                .build(new CacheLoader<String, Integer>() {
                    @Override
                    public Integer load(String key) {
                        return 0;
                    }
                });
    }
    
    /**
     * 로그인 성공 시 해당 IP와 사용자의 실패 기록을 초기화합니다.
     */
    public void loginSucceeded(String key, String userEmail) {
        ipAttemptsCache.invalidate(key);
        if (userEmail != null) {
            userAttemptsCache.invalidate(userEmail);
        }
        log.debug("Login attempts cleared for IP: {} and user: {}", key, userEmail);
    }
    
    /**
     * 로그인 실패 시 해당 IP와 사용자의 실패 횟수를 증가시킵니다.
     */
    public void loginFailed(String key, String userEmail) {
        int attempts = 0;
        try {
            attempts = ipAttemptsCache.get(key);
        } catch (ExecutionException e) {
            attempts = 0;
        }
        
        attempts += ATTEMPT_INCREMENT;
        ipAttemptsCache.put(key, attempts);
        
        if (userEmail != null) {
            int userAttempts = 0;
            try {
                userAttempts = userAttemptsCache.get(userEmail);
            } catch (ExecutionException e) {
                userAttempts = 0;
            }
            
            userAttempts += ATTEMPT_INCREMENT;
            userAttemptsCache.put(userEmail, userAttempts);
            
            log.warn("Login failed for IP: {} (attempts: {}) and user: {} (attempts: {})", 
                    key, attempts, userEmail, userAttempts);
        } else {
            log.warn("Login failed for IP: {} (attempts: {})", key, attempts);
        }
    }
    
    /**
     * IP 주소가 차단되었는지 확인합니다.
     */
    public boolean isIpBlocked(String key) {
        try {
            return ipAttemptsCache.get(key) >= MAX_ATTEMPTS;
        } catch (ExecutionException e) {
            return false;
        }
    }
    
    /**
     * 사용자가 차단되었는지 확인합니다.
     */
    public boolean isUserBlocked(String userEmail) {
        if (userEmail == null) {
            return false;
        }
        
        try {
            return userAttemptsCache.get(userEmail) >= MAX_ATTEMPTS;
        } catch (ExecutionException e) {
            return false;
        }
    }
    
    /**
     * IP 또는 사용자가 차단되었는지 확인합니다.
     */
    public boolean isBlocked(String ipAddress, String userEmail) {
        return isIpBlocked(ipAddress) || isUserBlocked(userEmail);
    }
    
    /**
     * 현재 IP의 로그인 시도 횟수를 반환합니다.
     */
    public int getIpAttempts(String ipAddress) {
        try {
            return ipAttemptsCache.get(ipAddress);
        } catch (ExecutionException e) {
            return 0;
        }
    }
    
    /**
     * 현재 사용자의 로그인 시도 횟수를 반환합니다.
     */
    public int getUserAttempts(String userEmail) {
        if (userEmail == null) {
            return 0;
        }
        
        try {
            return userAttemptsCache.get(userEmail);
        } catch (ExecutionException e) {
            return 0;
        }
    }
    
    /**
     * 남은 시도 횟수를 반환합니다.
     */
    public int getRemainingAttempts(String ipAddress, String userEmail) {
        int ipAttempts = getIpAttempts(ipAddress);
        int userAttempts = getUserAttempts(userEmail);
        int maxAttempts = Math.max(ipAttempts, userAttempts);
        
        return Math.max(0, MAX_ATTEMPTS - maxAttempts);
    }
    
    /**
     * 특정 IP나 사용자의 차단을 수동으로 해제합니다. (관리자 기능)
     */
    public void resetAttempts(String ipAddress, String userEmail) {
        if (ipAddress != null) {
            ipAttemptsCache.invalidate(ipAddress);
            log.info("Reset login attempts for IP: {}", ipAddress);
        }
        
        if (userEmail != null) {
            userAttemptsCache.invalidate(userEmail);
            log.info("Reset login attempts for user: {}", userEmail);
        }
    }
    
    /**
     * 캐시 통계 정보를 반환합니다. (모니터링 용도)
     */
    public String getCacheStats() {
        return String.format("IP Cache - Size: %d, Hit Rate: %.2f%% | User Cache - Size: %d, Hit Rate: %.2f%%",
                ipAttemptsCache.size(), ipAttemptsCache.stats().hitRate() * 100,
                userAttemptsCache.size(), userAttemptsCache.stats().hitRate() * 100);
    }
}