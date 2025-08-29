// package ac.su.kdt.beauthenticationservice.security;

// import lombok.RequiredArgsConstructor;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
// import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
// import org.springframework.security.config.web.server.ServerHttpSecurity;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.security.web.server.SecurityWebFilterChain;
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.reactive.CorsConfigurationSource;
// import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

// import java.util.Arrays;
// import java.util.List;

// @Configuration
// @EnableWebFluxSecurity
// @RequiredArgsConstructor
// public class SecurityConfig {
    
//     private final JwtServerAuthenticationConverter jwtAuthenticationConverter;
    
//     @Bean
//     public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
//         return http
//             .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//             .csrf(ServerHttpSecurity.CsrfSpec::disable)
//             .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
//             .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
//             .securityContextRepository(org.springframework.security.web.server.context.NoOpServerSecurityContextRepository.getInstance())
//             .authorizeExchange(exchanges -> exchanges
//                 .pathMatchers("/auth/**").permitAll()
//                 .pathMatchers("/actuator/**").permitAll()
//                 .pathMatchers("/health").permitAll()
//                 .pathMatchers("/", "/api/**").permitAll()
//                 .pathMatchers("/test-login.html", "/static/**").permitAll()
//                 .pathMatchers("/favicon.ico").permitAll()
//                 .pathMatchers("/gateway/**").authenticated() // Gateway 요청은 JWT 인증 필수
//                 .pathMatchers("/api/protected/**").authenticated() // JWT 인증이 필요한 보호된 API
//                 .anyExchange().permitAll() // 개발 중이므로 일시적으로 permitAll
//             )
//             // 인증 실패 시 JSON 응답 설정
//             .exceptionHandling(exceptions -> exceptions
//                 .authenticationEntryPoint((exchange, ex) -> {
//                     var response = exchange.getResponse();
//                     response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
//                     response.getHeaders().add("Content-Type", "application/json");
                    
//                     String body = "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"status\":401}";
//                     var buffer = response.bufferFactory().wrap(body.getBytes());
//                     return response.writeWith(reactor.core.publisher.Mono.just(buffer));
//                 })
//                 .accessDeniedHandler((exchange, denied) -> {
//                     var response = exchange.getResponse();
//                     response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
//                     response.getHeaders().add("Content-Type", "application/json");
                    
//                     String body = "{\"error\":\"Forbidden\",\"message\":\"Access denied\",\"status\":403}";
//                     var buffer = response.bufferFactory().wrap(body.getBytes());
//                     return response.writeWith(reactor.core.publisher.Mono.just(buffer));
//                 })
//             )
//             // JWT 인증 필터 추가
//             .addFilterAt(createJwtAuthenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
//             .build();
//     }
    
//     private org.springframework.security.web.server.authentication.AuthenticationWebFilter createJwtAuthenticationWebFilter() {
//         org.springframework.security.web.server.authentication.AuthenticationWebFilter authenticationWebFilter = 
//             new org.springframework.security.web.server.authentication.AuthenticationWebFilter(new JwtReactiveAuthenticationManager());
        
//         authenticationWebFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);
        
//         return authenticationWebFilter;
//     }
    
//     @Bean
//     public CorsConfigurationSource corsConfigurationSource() {
//         CorsConfiguration configuration = new CorsConfiguration();
        
//         // 허용할 오리진 설정 (개발 환경)
//         configuration.setAllowedOriginPatterns(List.of("*"));
        
//         // 허용할 HTTP 메서드
//         configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
//         // 허용할 헤더
//         configuration.setAllowedHeaders(Arrays.asList("*"));
        
//         // 인증 정보 포함 허용
//         configuration.setAllowCredentials(true);
        
//         // 프리플라이트 요청 캐시 시간 (초)
//         configuration.setMaxAge(3600L);
        
//         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//         source.registerCorsConfiguration("/**", configuration);
        
//         return source;
//     }
    
//     @Bean
//     public PasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }
// }

package ac.su.kdt.beauthenticationservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtServerAuthenticationConverter jwtAuthenticationConverter;
    private final JwtReactiveAuthenticationManager jwtReactiveAuthenticationManager;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange(exchanges -> exchanges
                // 1. Authenticated paths first
                .pathMatchers("/gateway/**").authenticated() // Gateway requests require JWT authentication
                .pathMatchers("/api/protected/**").authenticated() // Protected APIs require JWT authentication
                
                // 2. Public paths that do not require authentication
                .pathMatchers("/auth/**").permitAll() // Authentication endpoints (login, register)
                .pathMatchers("/actuator/**").permitAll() // Actuator endpoints for health checks
                .pathMatchers("/health").permitAll()
                .pathMatchers("/", "/api/**").permitAll() // Other public API endpoints
                .pathMatchers("/test-login.html", "/static/**").permitAll() // Static resources
                .pathMatchers("/favicon.ico").permitAll()
                
                // 3. A catch-all rule for all other paths. 
                // For security, it's best to require authentication for all other routes by default.
                .anyExchange().authenticated() 
            )
            // Configure custom exception handling for authentication and access denied
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, ex) -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    response.getHeaders().add("Content-Type", "application/json");
                    String body = "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"status\":401}";
                    var buffer = response.bufferFactory().wrap(body.getBytes());
                    return response.writeWith(reactor.core.publisher.Mono.just(buffer));
                })
                .accessDeniedHandler((exchange, denied) -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    response.getHeaders().add("Content-Type", "application/json");
                    String body = "{\"error\":\"Forbidden\",\"message\":\"Access denied\",\"status\":403}";
                    var buffer = response.bufferFactory().wrap(body.getBytes());
                    return response.writeWith(reactor.core.publisher.Mono.just(buffer));
                })
            )
            // Add the JWT authentication filter at the correct position in the filter chain
            .addFilterAt(createJwtAuthenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    private AuthenticationWebFilter createJwtAuthenticationWebFilter() {
        AuthenticationWebFilter authenticationWebFilter =
            new AuthenticationWebFilter(jwtReactiveAuthenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);
        return authenticationWebFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}