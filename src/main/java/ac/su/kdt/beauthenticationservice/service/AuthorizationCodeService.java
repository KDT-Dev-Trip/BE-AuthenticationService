package ac.su.kdt.beauthenticationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 Authorization Code 관리 서비스
 * Authorization Code Flow에서 사용되는 임시 코드를 Redis에 저장하고 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationCodeService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Redis 키 접두사
    private static final String AUTH_CODE_KEY = "oauth:auth_code:";
    private static final String AUTH_CODE_DATA_KEY = "oauth:auth_code_data:";
    
    // Authorization Code 만료시간 (10분)
    private static final long AUTH_CODE_EXPIRATION_MINUTES = 10;
    
    /**
     * Authorization Code 생성
     */
    public String generateAuthorizationCode(String userId, String clientId, String redirectUri, 
                                          String scope, String state, String codeChallenge, 
                                          String codeChallengeMethod) {
        // 안전한 랜덤 코드 생성 (URL-safe Base64)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String authCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        // Authorization Code 데이터 저장
        String authCodeData = String.format("%s:%s:%s:%s:%s:%s:%s:%s",
            userId, clientId, redirectUri, scope, state, 
            codeChallenge != null ? codeChallenge : "",
            codeChallengeMethod != null ? codeChallengeMethod : "",
            LocalDateTime.now().toString()
        );
        
        // Redis에 저장 (10분 만료)
        String key = AUTH_CODE_KEY + authCode;
        String dataKey = AUTH_CODE_DATA_KEY + authCode;
        
        redisTemplate.opsForValue().set(key, "valid", AUTH_CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(dataKey, authCodeData, AUTH_CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        
        log.info("Generated authorization code for user: {} client: {}", userId, clientId);
        return authCode;
    }
    
    /**
     * Authorization Code 검증 및 소비 (일회용)
     */
    public AuthorizationCodeData consumeAuthorizationCode(String authCode) {
        String key = AUTH_CODE_KEY + authCode;
        String dataKey = AUTH_CODE_DATA_KEY + authCode;
        
        // Authorization Code 존재 여부 확인
        String codeStatus = redisTemplate.opsForValue().get(key);
        if (codeStatus == null) {
            log.warn("Authorization code not found or expired: {}", authCode);
            return null;
        }
        
        // Authorization Code 데이터 가져오기
        String authCodeData = redisTemplate.opsForValue().get(dataKey);
        if (authCodeData == null) {
            log.warn("Authorization code data not found: {}", authCode);
            return null;
        }
        
        // Authorization Code를 즉시 무효화 (일회용)
        redisTemplate.delete(key);
        redisTemplate.delete(dataKey);
        
        // 데이터 파싱
        try {
            String[] parts = authCodeData.split(":");
            if (parts.length >= 6) {
                AuthorizationCodeData data = AuthorizationCodeData.builder()
                        .userId(parts[0])
                        .clientId(parts[1])
                        .redirectUri(parts[2])
                        .scope(parts[3])
                        .state(parts[4])
                        .codeChallenge(parts.length > 5 && !parts[5].isEmpty() ? parts[5] : null)
                        .codeChallengeMethod(parts.length > 6 && !parts[6].isEmpty() ? parts[6] : null)
                        .createdAt(parts.length > 7 ? LocalDateTime.parse(parts[7]) : LocalDateTime.now())
                        .build();
                
                log.info("Consumed authorization code for user: {} client: {}", data.getUserId(), data.getClientId());
                return data;
            }
        } catch (Exception e) {
            log.error("Failed to parse authorization code data: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Authorization Code 유효성 검증만 (소비하지 않음)
     */
    public boolean isAuthorizationCodeValid(String authCode) {
        String key = AUTH_CODE_KEY + authCode;
        return redisTemplate.hasKey(key);
    }
    
    /**
     * 사용자의 모든 Authorization Code 무효화
     */
    public void revokeUserAuthorizationCodes(String userId) {
        // 패턴 매칭으로 사용자의 모든 Authorization Code 찾기
        var keys = redisTemplate.keys(AUTH_CODE_DATA_KEY + "*");
        if (keys != null) {
            for (String dataKey : keys) {
                String authCodeData = redisTemplate.opsForValue().get(dataKey);
                if (authCodeData != null && authCodeData.startsWith(userId + ":")) {
                    String authCode = dataKey.replace(AUTH_CODE_DATA_KEY, "");
                    String key = AUTH_CODE_KEY + authCode;
                    
                    redisTemplate.delete(key);
                    redisTemplate.delete(dataKey);
                    log.info("Revoked authorization code for user: {}", userId);
                }
            }
        }
    }
    
    /**
     * 클라이언트의 모든 Authorization Code 무효화
     */
    public void revokeClientAuthorizationCodes(String clientId) {
        var keys = redisTemplate.keys(AUTH_CODE_DATA_KEY + "*");
        if (keys != null) {
            for (String dataKey : keys) {
                String authCodeData = redisTemplate.opsForValue().get(dataKey);
                if (authCodeData != null) {
                    String[] parts = authCodeData.split(":");
                    if (parts.length >= 2 && clientId.equals(parts[1])) {
                        String authCode = dataKey.replace(AUTH_CODE_DATA_KEY, "");
                        String key = AUTH_CODE_KEY + authCode;
                        
                        redisTemplate.delete(key);
                        redisTemplate.delete(dataKey);
                        log.info("Revoked authorization code for client: {}", clientId);
                    }
                }
            }
        }
    }
    
    /**
     * Authorization Code 통계 조회 (관리자용)
     */
    public Map<String, Object> getAuthorizationCodeStats() {
        var codeKeys = redisTemplate.keys(AUTH_CODE_KEY + "*");
        var dataKeys = redisTemplate.keys(AUTH_CODE_DATA_KEY + "*");
        
        return Map.of(
            "total_active_codes", codeKeys != null ? codeKeys.size() : 0,
            "total_data_entries", dataKeys != null ? dataKeys.size() : 0,
            "expiration_minutes", AUTH_CODE_EXPIRATION_MINUTES
        );
    }
    
    /**
     * Authorization Code 데이터 클래스
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthorizationCodeData {
        private String userId;
        private String clientId;
        private String redirectUri;
        private String scope;
        private String state;
        private String codeChallenge;      // PKCE
        private String codeChallengeMethod; // PKCE
        private LocalDateTime createdAt;
    }
}