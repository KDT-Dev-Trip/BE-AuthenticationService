package ac.su.kdt.beauthenticationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Gateway 설정 - 통합 API Gateway + Auth Service
 * 
 * ✅ WebFlux 기반 완전 통합
 * ✅ Gateway 라우팅 + 인증 기능 동시 제공
 * ✅ Profile 분리 없이 단일 서비스로 동작
 */
@Slf4j
@Configuration
public class GatewayConfig {

    @Value("${app.services.mission.url:http://localhost:8083}")
    private String missionServiceUrl;
    
    @Value("${app.services.user.url:http://localhost:8082}")
    private String userServiceUrl;
    
    @Value("${app.services.payment.url:http://localhost:8081}")
    private String paymentServiceUrl;
    
    @Value("${app.services.ai.url:http://localhost:8084}")
    private String aiServiceUrl;

    /**
     * Gateway 라우팅 설정
     * 기존 GatewayController의 HTTP 프록시 + WebSocket 라우팅 통합
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("🚀 Configuring Spring Cloud Gateway routes...");
        log.info("📍 Service URLs:");
        log.info("   User Service: {}", userServiceUrl);
        log.info("   Payment Service: {}", paymentServiceUrl);
        log.info("   Mission Service: {}", missionServiceUrl);
        log.info("   AI Service: {}", aiServiceUrl);
        
        log.info("📋 Expected routing patterns:");
        log.info("   /user/api/health → User Service /api/health"); 
        log.info("   /payment/api/health → Payment Service /api/health");
        log.info("   /mission/api/health → Mission Service /api/health");
        log.info("   /ai/api/health → AI Service /api/health");
        
        return builder.routes()
            // 주의: /auth/** 경로는 라우팅하지 않음 - AuthController에서 직접 처리
            // 인증 관련 요청: /auth/login, /auth/register, /auth/token 등
            
            // WebSocket 터미널 라우팅 - 미션서비스로 프록시
            .route("terminal-websocket", r -> r
                .path("/terminal/**")
                .and()
                .header("Upgrade", "websocket")
                .filters(f -> f
                    .addRequestHeader("X-Gateway-Auth", "true")
                    .addRequestHeader("X-Service-Route", "mission")
                )
                .uri(missionServiceUrl.replace("http://", "ws://"))
            )
            
            // Mock 터미널 WebSocket 라우팅
            .route("mock-terminal-websocket", r -> r
                .path("/mock-terminal/**")
                .and()
                .header("Upgrade", "websocket")
                .filters(f -> f
                    .addRequestHeader("X-Gateway-Auth", "true")
                    .addRequestHeader("X-Service-Route", "mission")
                )
                .uri(missionServiceUrl.replace("http://", "ws://"))
            )
            
            // kubectl 터미널 WebSocket 라우팅
            .route("kubectl-terminal-websocket", r -> r
                .path("/kubectl-terminal/**")
                .and()
                .header("Upgrade", "websocket")
                .filters(f -> f
                    .addRequestHeader("X-Gateway-Auth", "true")
                    .addRequestHeader("X-Service-Route", "mission")
                )
                .uri(missionServiceUrl.replace("http://", "ws://"))
            )
            
            // Pod 메트릭 WebSocket 라우팅
            .route("metrics-websocket", r -> r
                .path("/metrics/**")
                .and()
                .header("Upgrade", "websocket")
                .filters(f -> f
                    .addRequestHeader("X-Gateway-Auth", "true")
                    .addRequestHeader("X-Service-Route", "mission")
                )
                .uri(missionServiceUrl.replace("http://", "ws://"))
            )
            
            // HTTP API 라우팅 - MSA 서비스들로 프록시
            
            // Gateway prefix를 통한 서비스 라우팅 (JWT 인증 필요)
            .route("gateway-user-service", r -> r
                .path("/gateway/user/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway-Route", "gateway-user-service")
                    .addRequestHeader("X-Authenticated", "true")
                )
                .uri(userServiceUrl)
            )
            
            .route("gateway-payment-service", r -> r
                .path("/gateway/payment/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway-Route", "gateway-payment-service")
                    .addRequestHeader("X-Authenticated", "true")
                )
                .uri(paymentServiceUrl)
            )
            
            .route("gateway-mission-service", r -> r
                .path("/gateway/mission/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway-Route", "gateway-mission-service")
                    .addRequestHeader("X-Authenticated", "true")
                )
                .uri(missionServiceUrl)
            )
            
            .route("gateway-ai-service", r -> r
                .path("/gateway/ai/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway-Route", "gateway-ai-service")
                    .addRequestHeader("X-Authenticated", "true")
                )
                .uri(aiServiceUrl)
            )
            
            // User Service 라우팅 (인증 없이 접근 가능)
            .route("user-service", r -> r
                .path("/user/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Route", "user-service")
                )
                .uri(userServiceUrl)
            )
            
            // Payment Service 라우팅  
            .route("payment-service", r -> r
                .path("/payment/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Route", "payment-service")
                )
                .uri(paymentServiceUrl)
            )
            
            // Mission Service 라우팅
            .route("mission-service", r -> r
                .path("/mission/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Route", "mission-service")
                )
                .uri(missionServiceUrl)
            )
            
            // AI Evaluation Service 라우팅
            .route("ai-service", r -> r
                .path("/ai/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Route", "ai-service")
                )
                .uri(aiServiceUrl)
            )
            
            // 정적 리소스 라우팅 (미션서비스의 터미널 HTML)
            .route("static-terminal", r -> r
                .path("/kubectl-terminal.html")
                .uri(missionServiceUrl)
            )
            
            
            .build();
    }
    
    public GatewayConfig() {
        log.info("🚀 Auth Service with Spring Cloud Gateway initialized");
        log.info("🔐 Features enabled:");
        log.info("   ✅ Spring Cloud Gateway routing");
        log.info("   ✅ WebSocket support");
        log.info("   ✅ JWT authentication");
        log.info("   ✅ OAuth 2.0 / Social login");
        log.info("   ✅ MSA service routing");
    }
}