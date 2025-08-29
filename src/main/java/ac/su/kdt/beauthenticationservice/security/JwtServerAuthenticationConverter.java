package ac.su.kdt.beauthenticationservice.security;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = HttpHeaders.AUTHORIZATION;
    
    private final JwtService jwtService;
    
    public JwtServerAuthenticationConverter(JwtService jwtService) {
        this.jwtService = jwtService;
        System.out.println("🔥 JwtServerAuthenticationConverter Bean created successfully!");
    }
    
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().toString();
        
        System.out.println("🔥 JWT Converter - Processing " + method + " " + path + " request");
        log.info("JWT Converter - Processing {} {} request", method, path);
        
        return extractToken(exchange)
            .flatMap(token -> {
                log.info("JWT Converter - Token found for {} {}", method, path);
                return validateAndCreateAuthentication(token, path);
            })
            .doOnSuccess(auth -> {
                if (auth != null) {
                    log.info("JWT Converter - Authentication successful for {} {}", method, path);
                } else {
                    log.info("JWT Converter - No authentication created for {} {}", method, path);
                }
            })
            .doOnError(e -> log.error("JWT Converter - Authentication failed for {} {}: {}", method, path, e.getMessage()));
    }
    
    private Mono<String> extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        
        log.info("JWT Converter - Authorization header: {}", bearerToken != null ? bearerToken.substring(0, Math.min(20, bearerToken.length())) + "..." : "null");
        
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length());
            log.info("JWT Converter - Extracted JWT token (first 20 chars): {}...", token.substring(0, Math.min(20, token.length())));
            return Mono.just(token);
        }
        
        log.warn("JWT Converter - No Bearer token found in Authorization header");
        return Mono.empty();
    }
    
    private Mono<Authentication> validateAndCreateAuthentication(String token, String path) {
        try {
            // 테스트 토큰 처리
            if (token.startsWith("test-token-")) {
                String userId = token.replace("test-token-", "");
                return Mono.just(createAuthentication(userId, "test-user@example.com", "USER", token));
            }
            
            // JWT 토큰 유효성 검증
            if (!jwtService.isTokenValid(token)) {
                log.warn("Invalid JWT token provided for path: {}", path);
                return Mono.empty();
            }
            
            // JWT에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);
            
            log.debug("Extracted user info - userId: {}, email: {}, role: {}", userId, email, role);
            
            return Mono.just(createAuthentication(userId, email, role, token));
            
        } catch (Exception e) {
            log.error("JWT validation failed for path {}: {}", path, e.getMessage());
            return Mono.empty();
        }
    }
    
    private Authentication createAuthentication(String userId, String email, String role, String token) {
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER"))
        );
        
        return new UsernamePasswordAuthenticationToken(
            new JwtUserDetails(userId, email, token),
            null,
            authorities
        );
    }
}