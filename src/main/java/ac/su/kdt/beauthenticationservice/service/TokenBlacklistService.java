package ac.su.kdt.beauthenticationservice.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * JWT 토큰 블랙리스트 관리 서비스
 * 로그아웃된 토큰들을 블랙리스트에 추가하여 무효화 처리합니다.
 */
@Slf4j
@Service
public class TokenBlacklistService {
    
    // 블랙리스트 토큰들을 저장하는 캐시 (토큰 만료 시간까지 유지)
    private final Cache<String, LocalDateTime> blacklistedTokens;
    
    public TokenBlacklistService() {
        this.blacklistedTokens = CacheBuilder.newBuilder()
                .maximumSize(10000) // 최대 10,000개 토큰 저장
                .expireAfterWrite(24, TimeUnit.HOURS) // 24시간 후 자동 만료
                .recordStats() // 통계 기록 활성화
                .build();
    }
    
    /**
     * 토큰을 블랙리스트에 추가합니다 (로그아웃 시 호출)
     */
    public void blacklistToken(String tokenId, long expirationTime) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            log.warn("Attempted to blacklist null or empty token");
            return;
        }
        
        // Unix timestamp를 LocalDateTime으로 변환
        LocalDateTime expirationDateTime = Instant.ofEpochSecond(expirationTime)
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
        
        blacklistedTokens.put(tokenId, expirationDateTime);
        log.info("Token blacklisted: {} (expires at: {})", 
                tokenId.substring(0, Math.min(10, tokenId.length())) + "...", 
                expirationDateTime);
    }
    
    /**
     * 토큰이 블랙리스트에 있는지 확인합니다
     */
    public boolean isTokenBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return false;
        }
        
        LocalDateTime blacklistTime = blacklistedTokens.getIfPresent(tokenId);
        boolean isBlacklisted = blacklistTime != null;
        
        if (isBlacklisted) {
            log.debug("Token found in blacklist: {} (blacklisted at: {})", 
                    tokenId.substring(0, Math.min(10, tokenId.length())) + "...", 
                    blacklistTime);
        }
        
        return isBlacklisted;
    }
    
    /**
     * 만료된 토큰들을 수동으로 정리합니다
     */
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int initialSize = (int) blacklistedTokens.size();
        
        // 만료된 토큰들 제거
        blacklistedTokens.asMap().entrySet().removeIf(entry -> 
                entry.getValue().isBefore(now)
        );
        
        int finalSize = (int) blacklistedTokens.size();
        int removed = initialSize - finalSize;
        
        if (removed > 0) {
            log.info("Cleaned up {} expired tokens from blacklist. Current size: {}", 
                    removed, finalSize);
        }
    }
    
    /**
     * 특정 토큰을 블랙리스트에서 제거합니다 (관리자 기능)
     */
    public void removeFromBlacklist(String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return;
        }
        
        LocalDateTime removed = blacklistedTokens.asMap().remove(tokenId);
        if (removed != null) {
            log.info("Token manually removed from blacklist: {}", 
                    tokenId.substring(0, Math.min(10, tokenId.length())) + "...");
        }
    }
    
    /**
     * 블랙리스트 전체를 초기화합니다 (관리자 기능 - 주의해서 사용)
     */
    public void clearBlacklist() {
        int size = (int) blacklistedTokens.size();
        blacklistedTokens.invalidateAll();
        log.warn("Entire token blacklist cleared. {} tokens were removed.", size);
    }
    
    /**
     * 블랙리스트 통계 정보를 반환합니다
     */
    public BlacklistStats getBlacklistStats() {
        var stats = blacklistedTokens.stats();
        return BlacklistStats.builder()
                .totalTokens((int) blacklistedTokens.size())
                .hitCount(stats.hitCount())
                .missCount(stats.missCount())
                .hitRate(stats.hitRate())
                .evictionCount(stats.evictionCount())
                .build();
    }
    
    /**
     * 블랙리스트 통계 정보를 담는 클래스
     */
    @lombok.Builder
    @lombok.Data
    public static class BlacklistStats {
        private int totalTokens;
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long evictionCount;
    }
}