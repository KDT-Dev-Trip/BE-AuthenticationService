package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.config.OAuth2Properties;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthorizationCodeService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import ac.su.kdt.beauthenticationservice.service.SocialLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    private final JwtService jwtService;
    private final OAuth2Properties oauth2Properties;
    
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    
    @GetMapping("/google/auth")
    @Operation(
        summary = "Google 소셜 로그인 URL 생성",
        description = """
                Google OAuth2 인증을 위한 인증 URL을 생성합니다.
                사용자를 이 URL로 리다이렉트하여 Google 로그인을 시작합니다.
                """
    )
    public ResponseEntity<Map<String, Object>> getGoogleAuthUrl(
            @Parameter(description = "로그인 성공 후 돌아갈 URL", example = "http://localhost:3000/auth/callback")
            @RequestParam(defaultValue = "") String redirect_uri,
            @Parameter(description = "CSRF 방지를 위한 상태 값", example = "random-state-string")
            @RequestParam(defaultValue = "") String state) {
        
        try {
            OAuth2Properties.Google google = oauth2Properties.getSocial().getGoogle();
            String actualState = state.isEmpty() ? UUID.randomUUID().toString() : state;
            
            String authUrl = google.getAuthUrl() + "?"
                    + "client_id=" + URLEncoder.encode(google.getClientId(), StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(google.getRedirectUri(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(google.getScope(), StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&state=" + URLEncoder.encode(actualState, StandardCharsets.UTF_8);
            
            Map<String, Object> response = new HashMap<>();
            response.put("auth_url", authUrl);
            response.put("provider", "google");
            response.put("state", actualState);
            response.put("callback_uri", google.getRedirectUri()); // 실제 OAuth 콜백 URI
            response.put("frontend_redirect", !redirect_uri.isEmpty() ? redirect_uri : frontendUrl + "/auth/callback"); // 프론트엔드용
            
            log.info("Google auth URL generated for callback_uri: {}", google.getRedirectUri());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to generate Google auth URL", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "AUTH_URL_GENERATION_FAILED",
                "message", "Google 인증 URL 생성에 실패했습니다"
            ));
        }
    }
    
    @GetMapping("/kakao/auth")
    @Operation(
        summary = "Kakao 소셜 로그인 URL 생성",
        description = """
                Kakao OAuth2 인증을 위한 인증 URL을 생성합니다.
                사용자를 이 URL로 리다이렉트하여 Kakao 로그인을 시작합니다.
                """
    )
    public ResponseEntity<Map<String, Object>> getKakaoAuthUrl(
            @Parameter(description = "로그인 성공 후 돌아갈 URL", example = "http://localhost:3000/auth/callback")
            @RequestParam(defaultValue = "") String redirect_uri,
            @Parameter(description = "CSRF 방지를 위한 상태 값", example = "random-state-string")
            @RequestParam(defaultValue = "") String state) {
        
        try {
            OAuth2Properties.Kakao kakao = oauth2Properties.getSocial().getKakao();
            String actualState = state.isEmpty() ? UUID.randomUUID().toString() : state;
            
            String authUrl = kakao.getAuthUrl() + "?"
                    + "client_id=" + URLEncoder.encode(kakao.getClientId(), StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(kakao.getRedirectUri(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(kakao.getScope(), StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&state=" + URLEncoder.encode(actualState, StandardCharsets.UTF_8);
            
            Map<String, Object> response = new HashMap<>();
            response.put("auth_url", authUrl);
            response.put("provider", "kakao");
            response.put("state", actualState);
            response.put("callback_uri", kakao.getRedirectUri()); // 실제 OAuth 콜백 URI
            response.put("frontend_redirect", !redirect_uri.isEmpty() ? redirect_uri : frontendUrl + "/auth/callback"); // 프론트엔드용
            
            log.info("Kakao auth URL generated for callback_uri: {}", kakao.getRedirectUri());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to generate Kakao auth URL", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "AUTH_URL_GENERATION_FAILED",
                "message", "Kakao 인증 URL 생성에 실패했습니다"
            ));
        }
    }
    
    @GetMapping("/google/callback")
    @Operation(
        summary = "Google 소셜 로그인 콜백",
        description = """
                Google OAuth2 인증 콜백을 처리하고 JWT 토큰을 발급합니다.
                사용자를 프론트엔드로 리다이렉트하며, 토큰을 URL 파라미터로 전달합니다.
                """
    )
    public Mono<Void> handleGoogleCallback(
            @Parameter(description = "Google에서 발급한 Authorization Code", required = true)
            @RequestParam String code,
            @Parameter(description = "CSRF 방지를 위한 상태 값")
            @RequestParam(required = false) String state,
            @Parameter(description = "오류 정보")
            @RequestParam(required = false) String error,
            ServerWebExchange exchange) {
        
        String ipAddress = getClientIpAddress(exchange);
        ServerHttpResponse response = exchange.getResponse();
        
        try {
            // 오류 처리
            if (error != null && !error.isEmpty()) {
                log.warn("Google OAuth error from IP {}: {}", ipAddress, error);
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=google&error=" + 
                    URLEncoder.encode(error, StandardCharsets.UTF_8));
            }
            
            if (code == null || code.isEmpty()) {
                log.warn("Google OAuth callback missing code from IP: {}", ipAddress);
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=google&error=missing_code");
            }
            
            log.info("Processing Google OAuth callback from IP: {}", ipAddress);
            
            // Google 소셜 로그인 처리
            SocialLoginService.SocialLoginResult loginResult = socialLoginService.processGoogleLogin(code);
            
            if (!loginResult.isSuccess()) {
                log.error("Google login failed from IP {}: {}", ipAddress, loginResult.getErrorMessage());
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=google&error=" + 
                    URLEncoder.encode("login_failed", StandardCharsets.UTF_8));
            }
            
            User user = loginResult.getUser();
            
            // JWT 토큰 생성
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
            String refreshToken = jwtService.generateRefreshToken(user.getId());
            
            log.info("Google login successful for user: {} from IP: {}", user.getEmail(), ipAddress);
            
            // 프론트엔드로 리다이렉트 (토큰 포함)
            String redirectUrl = frontendUrl + "/auth/success?" + 
                "access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
                "&provider=google" +
                (state != null ? "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) : "");
            
            return redirectToFrontend(response, redirectUrl);
            
        } catch (Exception e) {
            log.error("Google OAuth callback error from IP: {}", ipAddress, e);
            return redirectToFrontend(response, frontendUrl + "/auth/error?provider=google&error=" + 
                URLEncoder.encode("internal_error", StandardCharsets.UTF_8));
        }
    }
    
    @GetMapping("/kakao/callback")
    @Operation(
        summary = "Kakao 소셜 로그인 콜백",
        description = """
                Kakao OAuth2 인증 콜백을 처리하고 JWT 토큰을 발급합니다.
                사용자를 프론트엔드로 리다이렉트하며, 토큰을 URL 파라미터로 전달합니다.
                """
    )
    public Mono<Void> handleKakaoCallback(
            @Parameter(description = "Kakao에서 발급한 Authorization Code", required = true)
            @RequestParam String code,
            @Parameter(description = "CSRF 방지를 위한 상태 값")
            @RequestParam(required = false) String state,
            @Parameter(description = "오류 정보")
            @RequestParam(required = false) String error,
            ServerWebExchange exchange) {
        
        String ipAddress = getClientIpAddress(exchange);
        ServerHttpResponse response = exchange.getResponse();
        
        try {
            // 오류 처리
            if (error != null && !error.isEmpty()) {
                log.warn("Kakao OAuth error from IP {}: {}", ipAddress, error);
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=kakao&error=" + 
                    URLEncoder.encode(error, StandardCharsets.UTF_8));
            }
            
            if (code == null || code.isEmpty()) {
                log.warn("Kakao OAuth callback missing code from IP: {}", ipAddress);
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=kakao&error=missing_code");
            }
            
            log.info("Processing Kakao OAuth callback from IP: {}", ipAddress);
            
            // Kakao 소셜 로그인 처리
            SocialLoginService.SocialLoginResult loginResult = socialLoginService.processKakaoLogin(code);
            
            if (!loginResult.isSuccess()) {
                log.error("Kakao login failed from IP {}: {}", ipAddress, loginResult.getErrorMessage());
                return redirectToFrontend(response, frontendUrl + "/auth/error?provider=kakao&error=" + 
                    URLEncoder.encode("login_failed", StandardCharsets.UTF_8));
            }
            
            User user = loginResult.getUser();
            
            // JWT 토큰 생성
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString());
            String refreshToken = jwtService.generateRefreshToken(user.getId());
            
            log.info("Kakao login successful for user: {} from IP: {}", user.getEmail(), ipAddress);
            
            // 프론트엔드로 리다이렉트 (토큰 포함)
            String redirectUrl = frontendUrl + "/auth/success?" + 
                "access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
                "&provider=kakao" +
                (state != null ? "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) : "");
            
            return redirectToFrontend(response, redirectUrl);
            
        } catch (Exception e) {
            log.error("Kakao OAuth callback error from IP: {}", ipAddress, e);
            return redirectToFrontend(response, frontendUrl + "/auth/error?provider=kakao&error=" + 
                URLEncoder.encode("internal_error", StandardCharsets.UTF_8));
        }
    }
    
    @GetMapping("/status")
    @Operation(
        summary = "소셜 로그인 서비스 상태",
        description = "소셜 로그인 서비스의 상태와 지원 기능을 확인합니다."
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "social-login-service");
        status.put("status", "healthy");
        status.put("providers", Map.of(
            "google", Map.of(
                "enabled", oauth2Properties.getSocial().getGoogle().getClientId() != null,
                "auth_url", "/oauth/social/google/auth",
                "callback_url", "/oauth/social/google/callback"
            ),
            "kakao", Map.of(
                "enabled", oauth2Properties.getSocial().getKakao().getClientId() != null,
                "auth_url", "/oauth/social/kakao/auth",
                "callback_url", "/oauth/social/kakao/callback"
            )
        ));
        status.put("frontend_url", frontendUrl);
        
        return ResponseEntity.ok(status);
    }
    
    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null 
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
            : "unknown";
    }
    
    private Mono<Void> redirectToFrontend(ServerHttpResponse response, String url) {
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url));
        return response.setComplete();
    }
}