package ac.su.kdt.beauthenticationservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    
    public JwtReactiveAuthenticationManager() {
        System.out.println("🔥 JwtReactiveAuthenticationManager Bean created successfully!");
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        log.info("JWT Authentication Manager - Processing authentication: {}", authentication);
        
        // JwtServerAuthenticationConverter에서 이미 검증된 인증 객체를 받음
        if (authentication != null && authentication.getPrincipal() != null) {
            log.info("JWT Authentication Manager - Setting authentication as authenticated");
            // 인증 성공으로 마킹
            authentication.setAuthenticated(true);
            return Mono.just(authentication);
        }
        
        log.warn("JWT Authentication Manager - No valid authentication found");
        return Mono.empty(); // 인증 실패
    }
}