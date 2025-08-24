package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.service.AuthorizationCodeService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import ac.su.kdt.beauthenticationservice.service.SocialLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 소셜 로그인 콜백 처리 컨트롤러
 * Google, Kakao 등의 소셜 로그인 콜백을 처리하고 OAuth Authorization Code를 생성합니다.
 */
@Slf4j
@RestController
@RequestMapping("/oauth/social")
@RequiredArgsConstructor
@Tag(name = "Social Login", description = "소셜 로그인 콜백 처리 API")
public class SocialLoginController {
    
    private final SocialLoginService socialLoginService;
    private final AuthorizationCodeService authorizationCodeService;
    private final RedisLoginAttemptService redisLoginAttemptService;
    
    @Value("${KAKAO_CLIENT_ID}")
    private String kakaoClientId;
    
    @Value("${GOOGLE_CLIENT_ID}")
    private String googleClientId;
    
    /**
     * 카카오 로그인 시작
     */
    @GetMapping("/kakao")
    @Operation(summary = "Start Kakao Login", description = "카카오 로그인을 시작합니다")
    public void startKakaoLogin(
            @Parameter(description = "OAuth client ID") @RequestParam(value = "client_id", required = false) String clientId,
            @Parameter(description = "Redirect URI") @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @Parameter(description = "OAuth scope") @RequestParam(value = "scope", required = false) String scope,
            @Parameter(description = "State parameter") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Code challenge") @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @Parameter(description = "Code challenge method") @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletResponse response) throws IOException {
        
        // 기본값 설정
        clientId = clientId != null ? clientId : "default-client";
        redirectUri = redirectUri != null ? redirectUri : "http://localhost:8080/oauth/callback";
        scope = scope != null ? scope : "openid profile email";
        
        // State 파라미터 생성 (OAuth 파라미터 인코딩)
        String encodedState = encodeOAuthParams(state, clientId, redirectUri, scope, codeChallenge, codeChallengeMethod);
        
        // 카카오 인증 URL 생성
        String kakaoAuthUrl = "https://kauth.kakao.com/oauth/authorize" +
                "?client_id=" + kakaoClientId +
                "&redirect_uri=" + URLEncoder.encode("http://localhost:8080/oauth/social/kakao/callback", StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&state=" + URLEncoder.encode(encodedState, StandardCharsets.UTF_8);
        
        response.sendRedirect(kakaoAuthUrl);
    }
    
    /**
     * 구글 로그인 시작
     */
    @GetMapping("/google")
    @Operation(summary = "Start Google Login", description = "구글 로그인을 시작합니다")
    public void startGoogleLogin(
            @Parameter(description = "OAuth client ID") @RequestParam(value = "client_id", required = false) String clientId,
            @Parameter(description = "Redirect URI") @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @Parameter(description = "OAuth scope") @RequestParam(value = "scope", required = false) String scope,
            @Parameter(description = "State parameter") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Code challenge") @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @Parameter(description = "Code challenge method") @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletResponse response) throws IOException {
        
        // 기본값 설정
        clientId = clientId != null ? clientId : "default-client";
        redirectUri = redirectUri != null ? redirectUri : "http://localhost:8080/oauth/callback";
        scope = scope != null ? scope : "openid profile email";
        
        // State 파라미터 생성 (OAuth 파라미터 인코딩)
        String encodedState = encodeOAuthParams(state, clientId, redirectUri, scope, codeChallenge, codeChallengeMethod);
        
        // 구글 인증 URL 생성
        String googleAuthUrl = "https://accounts.google.com/oauth/authorize" +
                "?client_id=" + googleClientId +
                "&redirect_uri=" + URLEncoder.encode("http://localhost:8080/oauth/social/google/callback", StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8) +
                "&state=" + URLEncoder.encode(encodedState, StandardCharsets.UTF_8);
        
        response.sendRedirect(googleAuthUrl);
    }
    
    /**
     * 구글 소셜 로그인 콜백
     */
    @GetMapping("/google/callback")
    @Operation(summary = "Google Login Callback", description = "Google OAuth 콜백을 처리합니다")
    public void googleCallback(
            @Parameter(description = "Google authorization code") @RequestParam("code") String code,
            @Parameter(description = "State parameter containing original OAuth params") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Error parameter") @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String ipAddress = getClientIpAddress(request);
        log.info("Google login callback from IP: {}", ipAddress);
        
        // 오류 처리
        if (error != null) {
            log.warn("Google login error: {} from IP: {}", error, ipAddress);
            redirectWithError(response, "access_denied", "Google login was cancelled or failed", state);
            return;
        }
        
        if (code == null) {
            log.warn("Missing authorization code from Google callback, IP: {}", ipAddress);
            redirectWithError(response, "invalid_request", "Authorization code is missing", state);
            return;
        }
        
        try {
            // State에서 원본 OAuth 파라미터 추출
            OAuthParams oauthParams = extractOAuthParamsFromState(state);
            if (oauthParams == null) {
                log.warn("Invalid state parameter from IP: {}", ipAddress);
                redirectWithError(response, "invalid_state", "Invalid state parameter", state);
                return;
            }
            
            // Google 소셜 로그인 처리
            SocialLoginService.SocialLoginResult loginResult = socialLoginService.processGoogleLogin(code);
            
            if (!loginResult.isSuccess()) {
                log.warn("Google login failed from IP: {}: {}", ipAddress, loginResult.getErrorMessage());
                
                // Redis에 실패 시도 기록 (이메일이 있는 경우)
                if (loginResult.getUser() != null && loginResult.getUser().getEmail() != null) {
                    redisLoginAttemptService.recordFailedAttempt(loginResult.getUser().getEmail(), ipAddress);
                }
                
                redirectWithError(response, "login_failed", loginResult.getErrorMessage(), oauthParams.getOriginalState());
                return;
            }
            
            // 계정 잠금 확인
            String email = loginResult.getUser().getEmail();
            if (redisLoginAttemptService.isAccountLocked(email)) {
                log.warn("Account locked during Google login: {} from IP: {}", email, ipAddress);
                redirectWithError(response, "account_locked", "Account is temporarily locked", oauthParams.getOriginalState());
                return;
            }
            
            // OAuth Authorization Code 생성
            String authorizationCode = authorizationCodeService.generateAuthorizationCode(
                loginResult.getUser().getId(),
                oauthParams.getClientId(),
                oauthParams.getRedirectUri(),
                oauthParams.getScope(),
                oauthParams.getOriginalState(),
                oauthParams.getCodeChallenge(),
                oauthParams.getCodeChallengeMethod()
            );
            
            // 성공 시 실패 카운트 초기화
            redisLoginAttemptService.recordSuccessfulLogin(email);
            
            // 원래 애플리케이션으로 리다이렉트
            redirectWithSuccess(response, oauthParams.getRedirectUri(), authorizationCode, oauthParams.getOriginalState());
            
            log.info("Google login successful for user: {} from IP: {}", email, ipAddress);
            
        } catch (Exception e) {
            log.error("Google login callback failed from IP: {}", ipAddress, e);
            redirectWithError(response, "server_error", "Internal server error during Google login", state);
        }
    }
    
    /**
     * 카카오 소셜 로그인 콜백
     */
    @GetMapping("/kakao/callback")
    @Operation(summary = "Kakao Login Callback", description = "Kakao OAuth 콜백을 처리합니다")
    public void kakaoCallback(
            @Parameter(description = "Kakao authorization code") @RequestParam("code") String code,
            @Parameter(description = "State parameter containing original OAuth params") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Error parameter") @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String ipAddress = getClientIpAddress(request);
        log.info("Kakao login callback from IP: {}", ipAddress);
        
        // 오류 처리
        if (error != null) {
            log.warn("Kakao login error: {} from IP: {}", error, ipAddress);
            redirectWithError(response, "access_denied", "Kakao login was cancelled or failed", state);
            return;
        }
        
        if (code == null) {
            log.warn("Missing authorization code from Kakao callback, IP: {}", ipAddress);
            redirectWithError(response, "invalid_request", "Authorization code is missing", state);
            return;
        }
        
        try {
            // State에서 원본 OAuth 파라미터 추출
            OAuthParams oauthParams = extractOAuthParamsFromState(state);
            if (oauthParams == null) {
                log.warn("Invalid state parameter from IP: {}", ipAddress);
                redirectWithError(response, "invalid_state", "Invalid state parameter", state);
                return;
            }
            
            // Kakao 소셜 로그인 처리
            SocialLoginService.SocialLoginResult loginResult = socialLoginService.processKakaoLogin(code);
            
            if (!loginResult.isSuccess()) {
                log.warn("Kakao login failed from IP: {}: {}", ipAddress, loginResult.getErrorMessage());
                
                // Redis에 실패 시도 기록
                if (loginResult.getUser() != null && loginResult.getUser().getEmail() != null) {
                    redisLoginAttemptService.recordFailedAttempt(loginResult.getUser().getEmail(), ipAddress);
                }
                
                redirectWithError(response, "login_failed", loginResult.getErrorMessage(), oauthParams.getOriginalState());
                return;
            }
            
            // 계정 잠금 확인
            String email = loginResult.getUser().getEmail();
            if (redisLoginAttemptService.isAccountLocked(email)) {
                log.warn("Account locked during Kakao login: {} from IP: {}", email, ipAddress);
                redirectWithError(response, "account_locked", "Account is temporarily locked", oauthParams.getOriginalState());
                return;
            }
            
            // OAuth Authorization Code 생성
            String authorizationCode = authorizationCodeService.generateAuthorizationCode(
                loginResult.getUser().getId(),
                oauthParams.getClientId(),
                oauthParams.getRedirectUri(),
                oauthParams.getScope(),
                oauthParams.getOriginalState(),
                oauthParams.getCodeChallenge(),
                oauthParams.getCodeChallengeMethod()
            );
            
            // 성공 시 실패 카운트 초기화
            redisLoginAttemptService.recordSuccessfulLogin(email);
            
            // 원래 애플리케이션으로 리다이렉트
            redirectWithSuccess(response, oauthParams.getRedirectUri(), authorizationCode, oauthParams.getOriginalState());
            
            log.info("Kakao login successful for user: {} from IP: {}", email, ipAddress);
            
        } catch (Exception e) {
            log.error("Kakao login callback failed from IP: {}", ipAddress, e);
            redirectWithError(response, "server_error", "Internal server error during Kakao login", state);
        }
    }
    
    // === Private Helper Methods ===
    
    /**
     * OAuth 파라미터를 State로 인코딩
     */
    private String encodeOAuthParams(String originalState, String clientId, String redirectUri, String scope, String codeChallenge, String codeChallengeMethod) {
        String params = (originalState != null ? originalState : "") + "|" +
                       clientId + "|" +
                       redirectUri + "|" +
                       scope + "|" +
                       (codeChallenge != null ? codeChallenge : "") + "|" +
                       (codeChallengeMethod != null ? codeChallengeMethod : "");
        
        return Base64.getUrlEncoder().withoutPadding().encodeToString(params.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * State에서 OAuth 파라미터 추출
     */
    private OAuthParams extractOAuthParamsFromState(String state) {
        if (state == null) {
            return null;
        }
        
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(state);
            String decodedState = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decodedState.split("\\|");
            
            if (parts.length >= 4) {
                return OAuthParams.builder()
                        .originalState(parts[0].isEmpty() ? null : parts[0])
                        .clientId(parts[1])
                        .redirectUri(parts[2])
                        .scope(parts[3])
                        .codeChallenge(parts.length > 4 && !parts[4].isEmpty() ? parts[4] : null)
                        .codeChallengeMethod(parts.length > 5 && !parts[5].isEmpty() ? parts[5] : null)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to decode state parameter: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 성공 리다이렉트
     */
    private void redirectWithSuccess(HttpServletResponse response, String redirectUri, String code, String state) throws IOException {
        StringBuilder url = new StringBuilder(redirectUri);
        url.append(redirectUri.contains("?") ? "&" : "?");
        url.append("code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
        
        if (state != null) {
            url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        
        response.sendRedirect(url.toString());
    }
    
    /**
     * 오류 리다이렉트
     */
    private void redirectWithError(HttpServletResponse response, String error, String errorDescription, String state) throws IOException {
        // 기본 에러 페이지로 리다이렉트 (실제로는 클라이언트 앱의 에러 페이지)
        StringBuilder url = new StringBuilder("/login?error=");
        url.append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        url.append("&error_description=").append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
        
        if (state != null) {
            try {
                OAuthParams params = extractOAuthParamsFromState(state);
                if (params != null && params.getRedirectUri() != null) {
                    url = new StringBuilder(params.getRedirectUri());
                    url.append(params.getRedirectUri().contains("?") ? "&" : "?");
                    url.append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
                    url.append("&error_description=").append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
                    
                    if (params.getOriginalState() != null) {
                        url.append("&state=").append(URLEncoder.encode(params.getOriginalState(), StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract redirect URI from state for error redirect: {}", e.getMessage());
            }
        }
        
        response.sendRedirect(url.toString());
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
    
    /**
     * OAuth 파라미터 DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class OAuthParams {
        private String originalState;
        private String clientId;
        private String redirectUri;
        private String scope;
        private String codeChallenge;
        private String codeChallengeMethod;
    }
}