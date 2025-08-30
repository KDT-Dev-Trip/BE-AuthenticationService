package ac.su.kdt.beauthenticationservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Collections;

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
            
            // GrantedAuthority 리스트와 함께 새로운 인증 토큰 생성
            UsernamePasswordAuthenticationToken authenticatedToken = 
                new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    authentication.getCredentials(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            
            return Mono.just(authenticatedToken);
        }
        
        log.warn("JWT Authentication Manager - No valid authentication found");
        return Mono.empty(); // 인증 실패
    }
}