package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.dto.HealthCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 표준 Health Check 서비스
 * 모든 DevTrip 서비스에서 동일한 로직으로 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {
    
    @Value("${spring.application.name:unknown-service}")
    private String serviceName;
    
    @Value("${app.version:1.0.0}")
    private String version;
    
    private final DataSource dataSource;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public HealthCheckResponse getHealthStatus() {
        Map<String, HealthCheckResponse.ComponentHealth> components = new HashMap<>();
        Map<String, Object> details = new HashMap<>();
        
        // Database Health Check
        components.put("database", checkDatabaseHealth());
        
        // Kafka Health Check
        components.put("kafka", checkKafkaHealth());
        
        // Memory Information
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        
        details.put("memory", Map.of(
            "used", formatBytes(usedMemory),
            "max", formatBytes(maxMemory),
            "usage", String.format("%.1f%%", (double) usedMemory / maxMemory * 100)
        ));
        
        // Uptime
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        details.put("uptime", formatUptime(uptime));
        
        // Service Features
        details.put("features", getServiceFeatures());
        
        // Overall Status
        String overallStatus = components.values().stream()
            .allMatch(c -> "UP".equals(c.getStatus())) ? "UP" : "DOWN";
        
        return HealthCheckResponse.builder()
            .status(overallStatus)
            .service(serviceName)
            .version(version)
            .timestamp(LocalDateTime.now())
            .components(components)
            .details(details)
            .build();
    }
    
    private HealthCheckResponse.ComponentHealth checkDatabaseHealth() {
        try {
            // Simple database connectivity check
            dataSource.getConnection().isValid(5);
            
            return HealthCheckResponse.ComponentHealth.builder()
                .status("UP")
                .details(Map.of(
                    "database", "MySQL",
                    "validationQuery", "isValid(5)"
                ))
                .build();
        } catch (Exception e) {
            log.warn("Database health check failed", e);
            return HealthCheckResponse.ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of(
                    "error", e.getMessage(),
                    "database", "MySQL"
                ))
                .build();
        }
    }
    
    private HealthCheckResponse.ComponentHealth checkKafkaHealth() {
        try {
            // Simple Kafka connectivity check
            kafkaTemplate.getProducerFactory().createProducer().close();
            
            return HealthCheckResponse.ComponentHealth.builder()
                .status("UP")
                .details(Map.of(
                    "brokers", kafkaTemplate.getProducerFactory().getConfigurationProperties()
                        .get("bootstrap.servers")
                ))
                .build();
        } catch (Exception e) {
            log.warn("Kafka health check failed", e);
            return HealthCheckResponse.ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of(
                    "error", e.getMessage()
                ))
                .build();
        }
    }
    
    private String[] getServiceFeatures() {
        // Override in each service to specify unique features
        return new String[]{"authentication", "oauth2", "jwt", "gateway", "kafka", "database"};
    }
    
    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return bytes + "B";
        }
    }
    
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }
}