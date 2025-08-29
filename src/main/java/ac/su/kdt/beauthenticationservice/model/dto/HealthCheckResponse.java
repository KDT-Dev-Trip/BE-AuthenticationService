package ac.su.kdt.beauthenticationservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 표준 Health Check 응답 DTO
 * 모든 DevTrip 서비스에서 동일한 형식으로 사용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthCheckResponse {
    
    /**
     * 서비스 상태: UP, DOWN, OUT_OF_SERVICE
     */
    private String status;
    
    /**
     * 서비스명
     */
    private String service;
    
    /**
     * 서비스 버전
     */
    private String version;
    
    /**
     * 응답 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 컴포넌트별 상태 (database, kafka, redis 등)
     */
    private Map<String, ComponentHealth> components;
    
    /**
     * 추가 상세 정보
     */
    private Map<String, Object> details;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentHealth {
        private String status;
        private Map<String, Object> details;
    }
}