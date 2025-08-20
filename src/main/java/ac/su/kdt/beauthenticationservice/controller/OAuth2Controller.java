package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.config.OAuth2Properties;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.service.AuthorizationCodeService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 Authorization Server
 * RFC 6749 표준을 준수하는 OAuth 2.0 Authorization Code Flow 구현
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth 2.0", description = "OAuth 2.0 Authorization Server API")
public class OAuth2Controller {
    
    private final AuthorizationCodeService authorizationCodeService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final RedisLoginAttemptService redisLoginAttemptService;
    private final OAuth2Properties oauth2Properties;
    private final RedisTemplate<String, String> redisTemplate;
    
    // PKCE를 위한 SecureRandom
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * OAuth 2.0 Discovery Endpoint
     * RFC 8414: OAuth 2.0 Authorization Server Metadata
     */
    @GetMapping("/.well-known/oauth-authorization-server")
    @Operation(summary = "OAuth 2.0 Server Discovery", description = "OAuth 2.0 서버의 메타데이터를 반환합니다")
    public ResponseEntity<Map<String, Object>> getServerMetadata(HttpServletRequest request) {
        String baseUrl = getBaseUrl(request);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("issuer", oauth2Properties.getIssuer());
        metadata.put("authorization_endpoint", baseUrl + "/oauth/authorize");
        metadata.put("token_endpoint", baseUrl + "/oauth/token");
        metadata.put("revocation_endpoint", baseUrl + "/oauth/revoke");
        metadata.put("userinfo_endpoint", baseUrl + "/oauth/userinfo");
        metadata.put("jwks_uri", baseUrl + "/oauth/jwks");
        metadata.put("response_types_supported", new String[]{"code"});
        metadata.put("grant_types_supported", new String[]{"authorization_code", "refresh_token"});
        metadata.put("subject_types_supported", new String[]{"public"});
        metadata.put("id_token_signing_alg_values_supported", new String[]{"HS512"});
        metadata.put("scopes_supported", new String[]{"openid", "profile", "email"});
        metadata.put("token_endpoint_auth_methods_supported", new String[]{"client_secret_post", "client_secret_basic"});
        metadata.put("code_challenge_methods_supported", new String[]{"S256", "plain"});
        
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * OAuth 2.0 Authorization Endpoint
     * RFC 6749 Section 3.1
     */
    @GetMapping("/authorize")
    @Operation(summary = "OAuth 2.0 Authorization", description = "OAuth 2.0 Authorization Code Flow 시작점")
    public void authorize(
            @Parameter(description = "Response type (must be 'code')") @RequestParam("response_type") String responseType,
            @Parameter(description = "Client ID") @RequestParam("client_id") String clientId,
            @Parameter(description = "Redirect URI") @RequestParam("redirect_uri") String redirectUri,
            @Parameter(description = "Requested scopes") @RequestParam(value = "scope", required = false, defaultValue = "openid") String scope,
            @Parameter(description = "State parameter") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Code challenge for PKCE") @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @Parameter(description = "Code challenge method") @RequestParam(value = "code_challenge_method", required = false, defaultValue = "S256") String codeChallengeMethod,
            @Parameter(description = "Login hint") @RequestParam(value = "login_hint", required = false) String loginHint,
            @Parameter(description = "Social provider") @RequestParam(value = "social", required = false) String socialProvider,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String ipAddress = getClientIpAddress(request);
        log.info("OAuth authorize request from IP: {} for client: {}", ipAddress, clientId);
        
        // 파라미터 검증
        if (!"code".equals(responseType)) {
            redirectError(response, redirectUri, "unsupported_response_type", "Only 'code' response type is supported", state);
            return;
        }
        
        if (clientId == null || clientId.isEmpty()) {
            redirectError(response, redirectUri, "invalid_request", "client_id is required", state);
            return;
        }
        
        if (redirectUri == null || redirectUri.isEmpty()) {
            redirectError(response, redirectUri, "invalid_request", "redirect_uri is required", state);
            return;
        }
        
        // PKCE 검증 (권장사항)
        if (codeChallenge != null && !"S256".equals(codeChallengeMethod) && !"plain".equals(codeChallengeMethod)) {
            redirectError(response, redirectUri, "invalid_request", "Invalid code_challenge_method", state);
            return;
        }
        
        // 소셜 로그인인 경우 리다이렉트
        if (socialProvider != null) {
            handleSocialLogin(socialProvider, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, response);
            return;
        }
        
        // 로컬 로그인 페이지로 리다이렉트
        String loginUrl = buildLoginUrl(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, loginHint);
        response.sendRedirect(loginUrl);
    }
    
    /**
     * OAuth 2.0 Token Endpoint
     * RFC 6749 Section 3.2
     */
    @PostMapping("/token")
    @Operation(summary = "OAuth 2.0 Token", description = "Authorization Code를 Access Token으로 교환")
    public ResponseEntity<Map<String, Object>> token(
            @Parameter(description = "Grant type") @RequestParam("grant_type") String grantType,
            @Parameter(description = "Authorization code") @RequestParam(value = "code", required = false) String code,
            @Parameter(description = "Redirect URI") @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @Parameter(description = "Client ID") @RequestParam(value = "client_id", required = false) String clientId,
            @Parameter(description = "Client secret") @RequestParam(value = "client_secret", required = false) String clientSecret,
            @Parameter(description = "Code verifier for PKCE") @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @Parameter(description = "Refresh token") @RequestParam(value = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request) {
        
        String ipAddress = getClientIpAddress(request);
        log.info("OAuth token request from IP: {} for grant_type: {}", ipAddress, grantType);
        
        try {
            switch (grantType) {
                case "authorization_code":
                    return handleAuthorizationCodeGrant(code, redirectUri, clientId, clientSecret, codeVerifier, ipAddress);
                    
                case "refresh_token":
                    return handleRefreshTokenGrant(refreshToken, clientId, clientSecret, ipAddress);
                    
                default:
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "unsupported_grant_type",
                        "error_description", "Grant type '" + grantType + "' is not supported"
                    ));
            }
        } catch (Exception e) {
            log.error("Token request failed from IP: {}", ipAddress, e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_request",
                "error_description", e.getMessage()
            ));
        }
    }
    
    /**
     * OAuth 2.0 Token Revocation
     * RFC 7009
     */
    @PostMapping("/revoke")
    @Operation(summary = "Token Revocation", description = "Access Token 또는 Refresh Token 무효화")
    public ResponseEntity<Map<String, Object>> revokeToken(
            @Parameter(description = "Token to revoke") @RequestParam("token") String token,
            @Parameter(description = "Token type hint") @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint,
            @Parameter(description = "Client ID") @RequestParam(value = "client_id", required = false) String clientId,
            HttpServletRequest request) {
        
        String ipAddress = getClientIpAddress(request);
        log.info("Token revocation request from IP: {} for client: {}", ipAddress, clientId);
        
        try {
            // JWT ID를 추출하여 블랙리스트에 추가
            String jti = jwtService.extractJti(token);
            if (jti != null) {
                // Redis에 블랙리스트로 등록 (토큰 만료시간까지)
                long expiration = jwtService.extractExpiration(token).getTime();
                long ttl = (expiration - System.currentTimeMillis()) / 1000;
                
                if (ttl > 0) {
                    redisTemplate.opsForValue().set("blacklist:token:" + jti, "revoked", ttl, TimeUnit.SECONDS);
                    log.info("Token revoked: {} from IP: {}", jti, ipAddress);
                }
            }
            
            return ResponseEntity.ok(Map.of("revoked", true));
            
        } catch (Exception e) {
            log.warn("Token revocation failed from IP: {}: {}", ipAddress, e.getMessage());
            // RFC 7009: 무효한 토큰도 성공으로 응답
            return ResponseEntity.ok(Map.of("revoked", true));
        }
    }
    
    /**
     * UserInfo Endpoint (OpenID Connect)
     * OpenID Connect Core 1.0 Section 5.3
     */
    @GetMapping("/userinfo")
    @Operation(summary = "UserInfo", description = "사용자 정보 조회 (OpenID Connect)")
    public ResponseEntity<Map<String, Object>> userinfo(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_token",
                    "error_description", "Bearer token required"
                ));
            }
            
            String accessToken = authHeader.substring(7);
            
            // 토큰 유효성 검증
            if (!jwtService.isAccessTokenValid(accessToken)) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_token",
                    "error_description", "Invalid access token"
                ));
            }
            
            // 블랙리스트 확인
            String jti = jwtService.extractJti(accessToken);
            if (redisTemplate.hasKey("blacklist:token:" + jti)) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_token",
                    "error_description", "Token has been revoked"
                ));
            }
            
            // 사용자 정보 조회
            String userId = jwtService.extractUserId(accessToken);
            Optional<User> userOpt = authService.getUserById(userId);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_token",
                    "error_description", "User not found"
                ));
            }
            
            User user = userOpt.get();
            return ResponseEntity.ok(Map.of(
                "sub", user.getId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "picture", user.getPictureUrl() != null ? user.getPictureUrl() : "",
                "email_verified", user.getEmailVerified(),
                "role", user.getRole().toString()
            ));
            
        } catch (Exception e) {
            log.error("UserInfo request failed", e);
            return ResponseEntity.status(401).body(Map.of(
                "error", "invalid_token",
                "error_description", "Token validation failed"
            ));
        }
    }
    
    // === Private Helper Methods ===
    
    /**
     * Authorization Code Grant 처리
     */
    private ResponseEntity<Map<String, Object>> handleAuthorizationCodeGrant(String code, String redirectUri, 
                                                                           String clientId, String clientSecret, 
                                                                           String codeVerifier, String ipAddress) {
        if (code == null) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        
        // Authorization Code 소비 및 검증
        AuthorizationCodeService.AuthorizationCodeData authData = 
            authorizationCodeService.consumeAuthorizationCode(code);
        
        if (authData == null) {
            throw new IllegalArgumentException("Invalid or expired authorization code");
        }
        
        // Redirect URI 검증
        if (!authData.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("Redirect URI mismatch");
        }
        
        // PKCE 검증
        if (authData.getCodeChallenge() != null) {
            if (codeVerifier == null) {
                throw new IllegalArgumentException("Code verifier is required for PKCE");
            }
            
            if (!verifyPKCE(authData.getCodeChallenge(), codeVerifier, authData.getCodeChallengeMethod())) {
                throw new IllegalArgumentException("Invalid code verifier");
            }
        }
        
        // 사용자 정보 조회
        Optional<User> userOpt = authService.getUserById(authData.getUserId());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        
        // 토큰 생성
        Map<String, Object> additionalClaims = Map.of(
            "client_id", authData.getClientId(),
            "scope", authData.getScope()
        );
        
        String accessToken = jwtService.generateAccessToken(
            user.getEmail(), 
            user.getId(), 
            user.getRole().toString(), 
            additionalClaims
        );
        
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getId());
        String idToken = jwtService.generateIdToken(
            user.getEmail(), 
            user.getId(), 
            user.getName(), 
            user.getPictureUrl(),
            Map.of("client_id", authData.getClientId())
        );
        
        log.info("Issued tokens for user: {} from IP: {}", user.getEmail(), ipAddress);
        
        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "token_type", "Bearer",
            "expires_in", oauth2Properties.getJwt().getAccessTokenExpiration(),
            "refresh_token", refreshToken,
            "id_token", idToken,
            "scope", authData.getScope()
        ));
    }
    
    /**
     * Refresh Token Grant 처리
     */
    private ResponseEntity<Map<String, Object>> handleRefreshTokenGrant(String refreshToken, String clientId, 
                                                                       String clientSecret, String ipAddress) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        
        // Refresh Token 검증
        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        
        // 사용자 정보 조회
        String userId = jwtService.extractUserId(refreshToken);
        String email = jwtService.extractEmail(refreshToken);
        
        Optional<User> userOpt = authService.getUserById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        
        // 새로운 Access Token 생성
        Map<String, Object> additionalClaims = Map.of(
            "client_id", clientId != null ? clientId : "default-client",
            "scope", "openid profile email"
        );
        
        String newAccessToken = jwtService.generateAccessToken(
            user.getEmail(), 
            user.getId(), 
            user.getRole().toString(), 
            additionalClaims
        );
        
        log.info("Refreshed access token for user: {} from IP: {}", user.getEmail(), ipAddress);
        
        return ResponseEntity.ok(Map.of(
            "access_token", newAccessToken,
            "token_type", "Bearer",
            "expires_in", oauth2Properties.getJwt().getAccessTokenExpiration()
        ));
    }
    
    /**
     * 소셜 로그인 처리
     */
    private void handleSocialLogin(String provider, String clientId, String redirectUri, String scope, 
                                 String state, String codeChallenge, String codeChallengeMethod, 
                                 HttpServletResponse response) throws IOException {
        String socialLoginUrl;
        
        switch (provider.toLowerCase()) {
            case "google":
                socialLoginUrl = buildGoogleLoginUrl(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);
                break;
            case "kakao":
                socialLoginUrl = buildKakaoLoginUrl(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);
                break;
            default:
                redirectError(response, redirectUri, "unsupported_social_provider", "Social provider '" + provider + "' is not supported", state);
                return;
        }
        
        response.sendRedirect(socialLoginUrl);
    }
    
    /**
     * 구글 로그인 URL 생성
     */
    private String buildGoogleLoginUrl(String clientId, String redirectUri, String scope, String state, 
                                     String codeChallenge, String codeChallengeMethod) {
        OAuth2Properties.Google google = oauth2Properties.getSocial().getGoogle();
        
        StringBuilder url = new StringBuilder(google.getAuthUrl());
        url.append("?response_type=code");
        url.append("&client_id=").append(URLEncoder.encode(google.getClientId(), StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(google.getRedirectUri(), StandardCharsets.UTF_8));
        url.append("&scope=").append(URLEncoder.encode(google.getScope(), StandardCharsets.UTF_8));
        
        if (state != null) {
            // 원본 OAuth 파라미터들을 state에 포함
            String enhancedState = Base64.getUrlEncoder().encodeToString(
                String.format("%s|%s|%s|%s|%s|%s", 
                    state != null ? state : "", clientId, redirectUri, scope, 
                    codeChallenge != null ? codeChallenge : "", 
                    codeChallengeMethod != null ? codeChallengeMethod : ""
                ).getBytes(StandardCharsets.UTF_8)
            );
            url.append("&state=").append(enhancedState);
        }
        
        return url.toString();
    }
    
    /**
     * 카카오 로그인 URL 생성
     */
    private String buildKakaoLoginUrl(String clientId, String redirectUri, String scope, String state, 
                                    String codeChallenge, String codeChallengeMethod) {
        OAuth2Properties.Kakao kakao = oauth2Properties.getSocial().getKakao();
        
        StringBuilder url = new StringBuilder(kakao.getAuthUrl());
        url.append("?response_type=code");
        url.append("&client_id=").append(URLEncoder.encode(kakao.getClientId(), StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(kakao.getRedirectUri(), StandardCharsets.UTF_8));
        url.append("&scope=").append(URLEncoder.encode(kakao.getScope(), StandardCharsets.UTF_8));
        
        if (state != null) {
            String enhancedState = Base64.getUrlEncoder().encodeToString(
                String.format("%s|%s|%s|%s|%s|%s", 
                    state != null ? state : "", clientId, redirectUri, scope, 
                    codeChallenge != null ? codeChallenge : "", 
                    codeChallengeMethod != null ? codeChallengeMethod : ""
                ).getBytes(StandardCharsets.UTF_8)
            );
            url.append("&state=").append(enhancedState);
        }
        
        return url.toString();
    }
    
    /**
     * PKCE 검증
     */
    private boolean verifyPKCE(String codeChallenge, String codeVerifier, String method) {
        try {
            if ("plain".equals(method)) {
                return codeChallenge.equals(codeVerifier);
            } else if ("S256".equals(method)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
                String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                return codeChallenge.equals(computedChallenge);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
        }
        return false;
    }
    
    /**
     * 로컬 로그인 URL 생성
     */
    private String buildLoginUrl(String clientId, String redirectUri, String scope, String state, 
                               String codeChallenge, String codeChallengeMethod, String loginHint) {
        StringBuilder url = new StringBuilder("/login");
        url.append("?client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        url.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        
        if (state != null) {
            url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        if (codeChallenge != null) {
            url.append("&code_challenge=").append(URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8));
        }
        if (codeChallengeMethod != null) {
            url.append("&code_challenge_method=").append(URLEncoder.encode(codeChallengeMethod, StandardCharsets.UTF_8));
        }
        if (loginHint != null) {
            url.append("&login_hint=").append(URLEncoder.encode(loginHint, StandardCharsets.UTF_8));
        }
        
        return url.toString();
    }
    
    /**
     * 오류 리다이렉트
     */
    private void redirectError(HttpServletResponse response, String redirectUri, String error, 
                             String errorDescription, String state) throws IOException {
        StringBuilder url = new StringBuilder(redirectUri);
        url.append(redirectUri.contains("?") ? "&" : "?");
        url.append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        url.append("&error_description=").append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
        
        if (state != null) {
            url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        
        response.sendRedirect(url.toString());
    }
    
    /**
     * Base URL 추출
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        return url.toString();
    }
    
    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}