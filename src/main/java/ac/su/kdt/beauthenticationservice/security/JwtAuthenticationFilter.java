package ac.su.kdt.beauthenticationservice.security;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * OAuth 2.0 JWT 토큰 검증을 위한 Spring Security 필터
 * Authorization 헤더에서 Bearer 토큰을 추출하고 검증합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = HttpHeaders.AUTHORIZATION;
    
    private final JwtService jwtService;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);
            
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateWithJwt(request, token);
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // 인증 실패 시에도 필터 체인을 계속 진행 (Spring Security가 처리)
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * HTTP 요청에서 JWT 토큰을 추출합니다.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTH_HEADER);
        
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
    private void authenticateWithJwt(HttpServletRequest request, String token) {
        try {
            // 테스트 토큰 처리
            if (token.startsWith("test-token-")) {
                authenticateTestToken(request, token);
                return;
            }
            
            // JWT 토큰 유효성 검증
            if (!jwtService.isTokenValid(token)) {
                log.warn("Invalid JWT token provided");
                throw new RuntimeException("Invalid JWT token");
            }
            
            // JWT에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);
            
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
            
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            log.debug("Successfully authenticated user: {} ({})", email, userId);
            
        } catch (Exception e) {
            log.error("JWT verification failed: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 테스트용 토큰 인증 처리
     */
    private void authenticateTestToken(HttpServletRequest request, String testToken) {
        String userId = testToken.replace("test-token-", "");
        
        UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                        new JwtUserDetails(userId, "test-user@example.com", testToken),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
        
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        
        log.debug("Successfully authenticated test user: {}", userId);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        
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