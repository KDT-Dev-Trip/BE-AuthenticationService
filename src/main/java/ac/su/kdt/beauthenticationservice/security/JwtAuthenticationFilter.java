package ac.su.kdt.beauthenticationservice.security;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * OAuth 2.0 JWT 토큰 검증을 위한 Spring WebFlux Security 필터
 * Authorization 헤더에서 Bearer 토큰을 추출하고 검증합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = HttpHeaders.AUTHORIZATION;
    
    private final JwtService jwtService;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().toString();
        log.info("JWT Filter - Processing {} {} request", method, path);
        
        try {
            String token = extractTokenFromRequest(exchange);
            
            if (token != null) {
                log.info("JWT Filter - Token found for {} {}", method, path);
                return authenticateWithJwt(exchange, chain, token)
                    .doOnError(e -> log.error("JWT Filter - Authentication failed for {} {}: {}", method, path, e.getMessage()))
                    .onErrorResume(e -> {
                        log.error("JWT Filter - Authentication error for {} {}, continuing without auth: {}", method, path, e.getMessage());
                        return chain.filter(exchange);
                    });
            } else {
                log.info("JWT Filter - No token found for {} {}", method, path);
            }
        } catch (Exception e) {
            log.error("JWT Filter - Exception for {} {}: {}", method, path, e.getMessage());
        }
        
        return chain.filter(exchange);
    }
    
    /**
     * HTTP 요청에서 JWT 토큰을 추출합니다.
     */
    private String extractTokenFromRequest(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length());
            log.debug("Extracted JWT token from request: {}...{}", 
                     token.substring(0, Math.min(20, token.length())),
                     token.length() > 20 ? "..." : "");
            return token;
        }
        
        return null;
    }
    
    /**
     * JWT 토큰을 사용하여 Spring Security 인증을 설정합니다.
     */
    private Mono<Void> authenticateWithJwt(ServerWebExchange exchange, WebFilterChain chain, String token) {
        try {
            log.debug("Attempting to authenticate JWT token");
            
            // 테스트 토큰 처리
            if (token.startsWith("test-token-")) {
                log.debug("Processing test token");
                return authenticateTestToken(exchange, chain, token);
            }
            
            // JWT 토큰 유효성 검증
            log.debug("Validating JWT token");
            if (!jwtService.isTokenValid(token)) {
                log.warn("Invalid JWT token provided");
                return Mono.error(new RuntimeException("Invalid JWT token"));
            }
            log.debug("JWT token is valid");
            
            // JWT에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);
            
            log.debug("Extracted user info - userId: {}, email: {}, role: {}", userId, email, role);
            
            // Spring Security 인증 객체 생성
            List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER"))
            );
            
            UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(
                            new JwtUserDetails(userId, email, token), 
                            null, 
                            authorities
                    );
            
            log.debug("Successfully authenticated user: {} ({})", email, userId);
            
            return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .build())
                .build())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authToken));
            
        } catch (Exception e) {
            log.error("JWT verification failed: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * 테스트용 토큰 인증 처리
     */
    private Mono<Void> authenticateTestToken(ServerWebExchange exchange, WebFilterChain chain, String testToken) {
        String userId = testToken.replace("test-token-", "");
        
        UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                        new JwtUserDetails(userId, "test-user@example.com", testToken),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
        
        log.debug("Successfully authenticated test user: {}", userId);
        
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", "test-user@example.com")
                .header("X-User-Role", "USER")
                .build())
            .build())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authToken));
    }
    
    /**
     * 인증이 필요 없는 경로들을 확인합니다.
     */
    private boolean shouldNotFilter(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // 인증이 필요 없는 경로들
        return path.startsWith("/test-") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/static/") ||
               path.startsWith("/error") ||
               path.startsWith("/oauth/") || // OAuth 엔드포인트들
               path.startsWith("/auth/") || // 인증 관련 엔드포인트들
               path.equals("/");
    }
}