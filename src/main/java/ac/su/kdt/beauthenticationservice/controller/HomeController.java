package ac.su.kdt.beauthenticationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HomeController {
    
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
            "service", "DevOps 교육 플랫폼 인증 서비스",
            "status", "UP",
            "version", "1.0.0",
            "timestamp", LocalDateTime.now(),
            "endpoints", Map.of(
                "health", "/actuator/health",
                "metrics", "/actuator/metrics", 
                "prometheus", "/actuator/prometheus",
                "auth_callback", "/auth/callback",
                "auth_validate", "/auth/validate"
            )
        ));
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> simpleHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Authentication Service"
        ));
    }
}