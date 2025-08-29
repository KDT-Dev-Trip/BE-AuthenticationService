package ac.su.kdt.beauthenticationservice.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {
    
    @Value("${oauth.jwt.secret}")
    private String secret;
    
    @Value("${oauth.jwt.access-token-expiration:3600}")
    private long accessTokenExpiration; // 1시간
    
    @Value("${oauth.jwt.refresh-token-expiration:2592000}")
    private long refreshTokenExpiration; // 30일
    
    @Value("${oauth.issuer}")
    private String issuer;
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Access Token 생성 (간단한 버전)
     */
    public String generateAccessToken(String userId, String email, String role) {
        return generateAccessToken(email, userId, role, null);
    }
    
    /**
     * Access Token 생성
     */
    public String generateAccessToken(String email, String userId, String role, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration * 1000);
        
        JwtBuilder builder = Jwts.builder()
                .setSubject(userId)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setId(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("role", role)
                .claim("token_type", "access_token")
                .signWith(getSigningKey());
        
        // 추가 클레임 설정
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        
        return builder.compact();
    }
    
    /**
     * Refresh Token 생성 (간단한 버전)
     */
    public String generateRefreshToken(String userId) {
        return generateRefreshToken("", userId);
    }
    
    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(String email, String userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration * 1000);
        
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setId(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("token_type", "refresh_token")
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }
    
    /**
     * ID Token 생성 (OpenID Connect용)
     */
    public String generateIdToken(String email, String userId, String name, String picture, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration * 1000); // Access Token과 동일한 만료시간
        
        JwtBuilder builder = Jwts.builder()
                .setSubject(userId)
                .setIssuer(issuer)
                .setAudience("devops-platform-client")
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setId(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("name", name)
                .claim("picture", picture)
                .claim("email_verified", true)
                .claim("token_type", "id_token")
                .signWith(getSigningKey());
        
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        
        return builder.compact();
    }
    
    /**
     * 토큰에서 클레임 추출
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * 토큰에서 사용자 ID 추출
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * 토큰에서 이메일 추출
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }
    
    /**
     * 토큰에서 역할 추출
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    
    /**
     * 토큰에서 토큰 타입 추출
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("token_type", String.class));
    }
    
    /**
     * 토큰 만료일 추출
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * 모든 클레임 추출
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 토큰 만료 여부 확인
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
    
    /**
     * 토큰 유효성 검증
     */
    public Boolean isTokenValid(String token) {
        try {
            log.info("JWT Validation - Starting token validation");
            Claims claims = extractAllClaims(token);
            log.info("JWT Validation - Token claims extracted successfully");
            log.info("JWT Validation - Subject: {}", claims.getSubject());
            log.info("JWT Validation - Email: {}", claims.get("email"));
            log.info("JWT Validation - Role: {}", claims.get("role"));
            log.info("JWT Validation - Expiration: {}", claims.getExpiration());
            log.info("JWT Validation - Current time: {}", new Date());
            
            boolean expired = isTokenExpired(token);
            log.info("JWT Validation - Token expired: {}", expired);
            
            return !expired;
        } catch (JwtException e) {
            log.error("JWT Validation - Token validation failed: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("JWT Validation - Unexpected error: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Refresh Token 유효성 검증 (타입까지 확인)
     */
    public Boolean isRefreshTokenValid(String refreshToken) {
        try {
            if (!isTokenValid(refreshToken)) {
                return false;
            }
            
            String tokenType = extractTokenType(refreshToken);
            return "refresh_token".equals(tokenType);
        } catch (Exception e) {
            log.debug("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Access Token 유효성 검증 (타입까지 확인)
     */
    public Boolean isAccessTokenValid(String accessToken) {
        try {
            if (!isTokenValid(accessToken)) {
                return false;
            }
            
            String tokenType = extractTokenType(accessToken);
            return "access_token".equals(tokenType);
        } catch (Exception e) {
            log.debug("Access token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 토큰 정보 요약
     */
    public Map<String, Object> getTokenInfo(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return Map.of(
                "sub", claims.getSubject(),
                "email", claims.get("email", String.class),
                "role", claims.get("role", String.class),
                "token_type", claims.get("token_type", String.class),
                "iss", claims.getIssuer(),
                "iat", claims.getIssuedAt(),
                "exp", claims.getExpiration(),
                "jti", claims.getId()
            );
        } catch (JwtException e) {
            log.warn("Failed to extract token info: {}", e.getMessage());
            return Map.of("error", "Invalid token");
        }
    }
    
    /**
     * 토큰 JTI (JWT ID) 추출
     */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }
    
    /**
     * Access Token 만료 시간 반환 (초)
     */
    public long getAccessTokenExpirationTime() {
        return accessTokenExpiration;
    }
    
    /**
     * 비밀번호 재설정 토큰 생성
     */
    public String generatePasswordResetToken(String userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600 * 1000); // 1시간
        
        return Jwts.builder()
                .subject(userId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString())
                .claim("type", "password_reset")
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * 비밀번호 재설정 토큰 검증
     */
    public boolean validatePasswordResetToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get("type", String.class);
            return "password_reset".equals(tokenType) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("Invalid password reset token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 로컬 토큰 검증 (validateLocalToken 대체)
     */
    public Claims validateLocalToken(String token) {
        try {
            if (isTokenValid(token)) {
                return extractAllClaims(token);
            }
        } catch (JwtException e) {
            log.warn("Local token validation failed: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 테스트용: 커스텀 만료 시간으로 토큰 생성
     */
    public String generateTokenWithCustomExpiration(String userId, String email, String role, long expirationSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationSeconds * 1000);
        
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setId(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("role", role)
                .claim("token_type", "access_token")
                .signWith(getSigningKey())
                .compact();
    }
}