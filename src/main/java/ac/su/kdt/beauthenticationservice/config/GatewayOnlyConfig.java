package ac.su.kdt.beauthenticationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Gateway 전용 설정 클래스
 * 
 * Gateway 모드에서는 최소한의 빈만 로드하여 성능 최적화
 * - JPA Entity 스캔 최소화
 * - 불필요한 Service 빈 로드 방지
 * - Gateway 라우팅 기능에 집중
 */
@Slf4j
@Configuration
@Profile("gateway")
public class GatewayOnlyConfig {

    public GatewayOnlyConfig() {
        log.info("🚀 Gateway Only Configuration initialized - Auth Service running in Gateway mode");
        log.info("📝 Available features:");
        log.info("   ✅ Spring Cloud Gateway routing");
        log.info("   ✅ WebSocket support");
        log.info("   ✅ JWT token validation");
        log.info("   ✅ Basic authentication endpoints");
        log.info("   ⚠️  Full Auth features disabled for performance");
    }
}