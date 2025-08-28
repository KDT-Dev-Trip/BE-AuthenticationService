package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.dto.HealthCheckResponse;
import ac.su.kdt.beauthenticationservice.service.HealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 표준 Health Check 컨트롤러
 * 모든 DevTrip 서비스에서 동일한 구조로 사용
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Health Check", description = "표준 헬스체크 API")
public class StandardHealthController {

    private final HealthCheckService healthCheckService;

    @GetMapping("/health")
    @Operation(summary = "Standard Health Check", description = "표준 헬스체크 - 모든 DevTrip 서비스 공통 형식")
    public ResponseEntity<HealthCheckResponse> health() {
        try {
            HealthCheckResponse response = healthCheckService.getHealthStatus();
            
            // Log health check request
            log.debug("Health check requested - status: {}, components: {}", 
                     response.getStatus(), 
                     response.getComponents().keySet());
            
            return "UP".equals(response.getStatus()) 
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            
            HealthCheckResponse errorResponse = HealthCheckResponse.builder()
                .status("DOWN")
                .service(healthCheckService.getClass().getSimpleName())
                .version("1.0.0")
                .timestamp(java.time.LocalDateTime.now())
                .details(java.util.Map.of("error", e.getMessage()))
                .build();
                
            return ResponseEntity.status(503).body(errorResponse);
        }
    }
}