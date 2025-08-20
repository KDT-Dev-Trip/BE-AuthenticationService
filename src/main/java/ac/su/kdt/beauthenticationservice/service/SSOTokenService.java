package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO (Single Sign-On) 토큰 관리 서비스
 * 여러 애플리케이션 간 단일 인증을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SSOTokenService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    private static final String SSO_TOKEN_PREFIX = "sso:token:";
    private static final String SSO_SESSION_PREFIX = "sso:session:";
    private static final Duration SSO_TOKEN_EXPIRY = Duration.ofHours(8); // 8시간
    
    /**
     * SSO 토큰 생성
     */
    public String generateSSOToken(User user) {
        String ssoToken = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        
        // SSO 토큰 정보 저장
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("userId", user.getId());
        tokenInfo.put("email", user.getEmail());
        tokenInfo.put("name", user.getName());
        tokenInfo.put("role", user.getRole().name());
        tokenInfo.put("sessionId", sessionId);
        tokenInfo.put("createdAt", LocalDateTime.now().toString());
        tokenInfo.put("expiresAt", LocalDateTime.now().plus(SSO_TOKEN_EXPIRY).toString());
        
        redisTemplate.opsForValue().set(
            SSO_TOKEN_PREFIX + ssoToken, 
            tokenInfo, 
            SSO_TOKEN_EXPIRY
        );
        
        // 세션 정보 저장
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("userId", user.getId());
        sessionInfo.put("ssoToken", ssoToken);
        sessionInfo.put("applications", new HashMap<String, String>());
        
        redisTemplate.opsForValue().set(
            SSO_SESSION_PREFIX + sessionId,
            sessionInfo,
            SSO_TOKEN_EXPIRY
        );
        
        log.info("SSO token generated for user: {} with session: {}", user.getEmail(), sessionId);
        return ssoToken;
    }
    
    /**
     * SSO 토큰 검증
     */
    public Optional<User> validateSSOToken(String ssoToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_TOKEN_PREFIX + ssoToken);
            
            if (tokenInfo == null) {
                log.warn("SSO token not found or expired: {}", ssoToken);
                return Optional.empty();
            }
            
            String userId = (String) tokenInfo.get("userId");
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent()) {
                log.info("SSO token validation successful for user: {}", userOpt.get().getEmail());
                // 토큰 사용 시 TTL 갱신
                redisTemplate.expire(SSO_TOKEN_PREFIX + ssoToken, SSO_TOKEN_EXPIRY);
                return userOpt;
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error validating SSO token: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 애플리케이션 등록 (SSO 세션에 애플리케이션 추가)
     */
    public boolean registerApplication(String ssoToken, String applicationId, String applicationName) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_TOKEN_PREFIX + ssoToken);
            
            if (tokenInfo == null) {
                return false;
            }
            
            String sessionId = (String) tokenInfo.get("sessionId");
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_SESSION_PREFIX + sessionId);
            
            if (sessionInfo != null) {
                @SuppressWarnings("unchecked")
                Map<String, String> applications = (Map<String, String>) sessionInfo.get("applications");
                applications.put(applicationId, applicationName);
                
                redisTemplate.opsForValue().set(
                    SSO_SESSION_PREFIX + sessionId,
                    sessionInfo,
                    SSO_TOKEN_EXPIRY
                );
                
                log.info("Application {} registered to SSO session: {}", applicationName, sessionId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error registering application to SSO session: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * SSO 로그아웃 (모든 애플리케이션에서 로그아웃)
     */
    public boolean logout(String ssoToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_TOKEN_PREFIX + ssoToken);
            
            if (tokenInfo == null) {
                return false;
            }
            
            String sessionId = (String) tokenInfo.get("sessionId");
            String userId = (String) tokenInfo.get("userId");
            
            // SSO 토큰 삭제
            redisTemplate.delete(SSO_TOKEN_PREFIX + ssoToken);
            
            // 세션 정보 삭제
            if (sessionId != null) {
                redisTemplate.delete(SSO_SESSION_PREFIX + sessionId);
            }
            
            log.info("SSO logout completed for user: {}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error during SSO logout: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * SSO 세션 정보 조회
     */
    public Map<String, Object> getSessionInfo(String ssoToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_TOKEN_PREFIX + ssoToken);
            
            if (tokenInfo == null) {
                return new HashMap<>();
            }
            
            String sessionId = (String) tokenInfo.get("sessionId");
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionInfo = (Map<String, Object>) redisTemplate.opsForValue()
                .get(SSO_SESSION_PREFIX + sessionId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("tokenInfo", tokenInfo);
            result.put("sessionInfo", sessionInfo != null ? sessionInfo : new HashMap<>());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting SSO session info: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * JWT에서 SSO 토큰으로 업그레이드
     */
    public String upgradeToSSO(String jwtToken) {
        try {
            if (!jwtService.isTokenValid(jwtToken)) {
                return null;
            }
            
            String userId = jwtService.extractUserId(jwtToken);
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent()) {
                return generateSSOToken(userOpt.get());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error upgrading JWT to SSO token: {}", e.getMessage());
            return null;
        }
    }
}