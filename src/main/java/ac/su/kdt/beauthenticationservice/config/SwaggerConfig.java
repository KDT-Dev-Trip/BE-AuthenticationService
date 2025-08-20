package ac.su.kdt.beauthenticationservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 3.0 설정
 * API 문서화 및 테스트 도구 제공
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevOps 교육 플랫폼 - 인증 서비스 API")
                        .version("1.0.0")
                        .description("""
                                ## DevOps 교육 플랫폼 인증 서비스
                                
                                이 API는 사용자 인증, 권한 관리, 그리고 마이크로서비스 간 API Gateway 역할을 제공합니다.
                                
                                ### 주요 기능
                                - 🔐 **Auth0 기반 소셜 로그인** (Google, GitHub 등)
                                - 📧 **로컬 이메일/비밀번호 인증**
                                - 🔒 **JWT 토큰 기반 인증/인가**
                                - 🚫 **Redis 기반 로그인 시도 제한** (10회 시도 시 1시간 잠금)
                                - 🌐 **API Gateway 라우팅** (다른 마이크로서비스로 요청 전달)
                                - ⚡ **실시간 이벤트 발행** (Kafka)
                                - 📧 **이메일 알림** (회원가입, 비밀번호 재설정, 로그인 알림)
                                - 👨‍💼 **관리자 도구** (계정 잠금 해제, 보안 통계)
                                
                                ### 보안 기능
                                - 계정 잠금: 10회 로그인 실패 시 1시간 자동 잠금
                                - IP 기반 모니터링: 의심스러운 IP 추적
                                - JWT 토큰 관리: Access Token (15분) + Refresh Token (7일)
                                - 비밀번호 재설정: 시간 제한 토큰 (30분)
                                
                                ### 인증 방법
                                1. **Auth0 JWT**: Auth0에서 발급받은 JWT 토큰 사용
                                2. **로컬 JWT**: 로컬 인증 후 발급받은 JWT 토큰 사용
                                
                                Authorization 헤더에 `Bearer {token}` 형식으로 전달하세요.
                                """)
                        .termsOfService("https://devops-platform.com/terms")
                        .contact(new Contact()
                                .name("DevOps 교육 플랫폼 개발팀")
                                .email("dev@devops-platform.com")
                                .url("https://devops-platform.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("개발 서버"),
                        new Server()
                                .url("https://api.devops-platform.com")
                                .description("운영 서버")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 Authorization 헤더에 Bearer {token} 형식으로 전달")));
    }
}