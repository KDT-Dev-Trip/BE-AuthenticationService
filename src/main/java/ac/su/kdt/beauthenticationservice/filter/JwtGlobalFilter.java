package ac.su.kdt.beauthenticationservice.filter;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 토큰 검증을 위한 Spring Cloud Gateway Global Filter
 * 
 * 🔍 동작 방식:
 * 1. 모든 Gateway 요청에 대해 실행됩니다 (라우팅 후, Target Service 호출 전)
 * 2. Authorization 헤더에서 JWT 토큰을 추출합니다
 * 3. JWT 토큰의 유효성을 검증합니다
 * 4. 검증 실패 시: 401 Unauthorized 즉시 반환 (Target Service 호출 안함)
 * 5. 검증 성공 시: 사용자 정보를 헤더에 추가하여 Target Service로 전달
 * 
 * 📍 실행 순서: 가장 높은 우선순위 (-100)로 설정하여 다른 필터들보다 먼저 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    
    /**
     * JWT Bearer 토큰 프리픽스
     */
    private static final String BEARER_PREFIX = "Bearer ";
    
    /**
     * Authorization 헤더명
     */
    private static final String AUTH_HEADER = HttpHeaders.AUTHORIZATION;

    /**
     * Gateway Global Filter 메인 로직
     * 
     * @param exchange Spring WebFlux의 ServerWebExchange (요청/응답 정보)
     * @param chain Gateway Filter Chain (다음 필터 호출용)
     * @return Mono<Void> 비동기 처리 결과
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().toString();

        log.info("🔐 JWT GlobalFilter - Processing {} {} request", method, path);

        // 📋 1단계: 인증이 필요 없는 경로 체크
        if (shouldSkipAuthentication(path)) {
            log.info("🚪 JWT GlobalFilter - Skipping authentication for public path: {}", path);
            return chain.filter(exchange);
        }

        // 📋 2단계: Authorization 헤더에서 JWT 토큰 추출
        String token = extractTokenFromRequest(request);
        if (token == null) {
            log.warn("🚫 JWT GlobalFilter - No JWT token found in Authorization header for {}", path);
            return createUnauthorizedResponse(exchange, "JWT token required");
        }

        // 📋 3단계: JWT 토큰 유효성 검증
        try {
            if (!jwtService.isTokenValid(token)) {
                log.warn("🚫 JWT GlobalFilter - Invalid JWT token for {}", path);
                return createUnauthorizedResponse(exchange, "Invalid JWT token");
            }

            // 📋 4단계: JWT에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);

            log.info("✅ JWT GlobalFilter - Authentication successful for {} {} - User: {}, Email: {}, Role: {}",
                    method, path, userId, email, role);

            // 📋 5단계: 사용자 정보를 헤더에 추가하여 Target Service로 전달
            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(request.mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email)
                            .header("X-User-Role", role)
                            .header("X-Gateway-Auth", "true")
                            .build())
                    .build();

            // 📋 6단계: 다음 필터 및 Target Service 호출
            return chain.filter(modifiedExchange);

        } catch (Exception e) {
            log.error("🚫 JWT GlobalFilter - JWT validation failed for {} {}: {}", method, path, e.getMessage());
            return createUnauthorizedResponse(exchange, "JWT validation failed: " + e.getMessage());
        }
    }

    /**
     * HTTP 요청에서 JWT 토큰을 추출합니다
     * 
     * @param request ServerHttpRequest 객체
     * @return JWT 토큰 문자열 (Bearer 프리픽스 제거됨), 없으면 null
     */
    private String extractTokenFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(AUTH_HEADER);
        
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length());
            log.debug("🔑 JWT GlobalFilter - Extracted JWT token (length: {})", token.length());
            return token;
        }
        
        log.debug("🔍 JWT GlobalFilter - No Bearer token found in Authorization header");
        return null;
    }

    /**
     * 인증이 필요 없는 공개 경로인지 확인합니다
     * 
     * @param path 요청 경로
     * @return 인증 스킵 여부 (true: 스킵, false: 인증 필요)
     */
    private boolean shouldSkipAuthentication(String path) {
        // 🔓 인증이 필요 없는 공개 경로들
        return path.startsWith("/auth/") ||          // 인증 관련 엔드포인트 (로그인, 회원가입 등)
               path.startsWith("/actuator/") ||      // Spring Boot Actuator (헬스체크 등)
               path.startsWith("/health") ||         // 헬스체크 엔드포인트
               path.equals("/") ||                   // 루트 경로
               path.startsWith("/api/public/") ||    // 공개 API 경로
               path.startsWith("/static/") ||        // 정적 리소스
               path.equals("/favicon.ico") ||        // 파비콘
               path.startsWith("/test-login.html");  // 테스트 페이지
        
        // 🔐 /gateway/** 경로는 모두 JWT 인증이 필요합니다
    }

    /**
     * 401 Unauthorized 응답을 생성합니다
     * 
     * @param exchange ServerWebExchange 객체
     * @param message 에러 메시지
     * @return 401 응답 Mono
     */
    private Mono<Void> createUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 📄 HTTP 상태 코드 및 헤더 설정
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        // 📝 JSON 에러 응답 본문 생성
        String jsonBody = String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"status\":401,\"timestamp\":\"%s\",\"path\":\"%s\"}",
                message,
                java.time.Instant.now().toString(),
                exchange.getRequest().getPath().value()
        );
        
        // 📤 응답 데이터 버퍼 생성 및 전송
        DataBuffer buffer = response.bufferFactory().wrap(jsonBody.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * GlobalFilter의 실행 순서를 결정합니다
     * 낮은 값일수록 먼저 실행됩니다
     * 
     * @return 실행 순서 (-100: 가장 높은 우선순위)
     */
    @Override
    public int getOrder() {
        return -100; // 🚀 가장 먼저 실행되도록 높은 우선순위 설정
    }
}