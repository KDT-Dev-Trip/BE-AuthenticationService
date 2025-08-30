package ac.su.kdt.beauthenticationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * User Management Service 클라이언트
 * Auth Service에서 User Management Service API를 호출하기 위한 클라이언트
 */
@Slf4j
@Component
public class UserManagementServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final boolean mockMode;

    public UserManagementServiceClient(RestTemplate restTemplate,
                                     @Value("${app.external-services.user-service.url:http://localhost:8082/api/v1}") String userServiceUrl,
                                     @Value("${app.external-services.user-service.mock-mode:false}") boolean mockMode) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
        this.mockMode = mockMode;
        log.info("UserManagementServiceClient initialized - Mock Mode: {}, URL: {}", this.mockMode, userServiceUrl);
    }

    /**
     * Auth User ID로 실제 User ID (Long) 조회
     */
    public Long getUserIdByAuthUserId(String authUserId) {
        if (mockMode) {
            log.info("[MOCK] Getting user ID for authUserId: {} - returning hash-based ID", authUserId);
            // Mock 모드에서는 authUserId의 hashCode를 사용해서 Long 타입 ID 생성
            return (long) Math.abs(authUserId.hashCode() % 1000000);
        }
        
        try {
            String url = userServiceUrl + "/users/by-auth-id/" + authUserId;
            log.info("Getting user ID for authUserId: {}", authUserId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> userData = response.getBody();
                Object userIdObj = userData.get("id");
                
                if (userIdObj instanceof Number) {
                    Long userId = ((Number) userIdObj).longValue();
                    log.info("Successfully retrieved user ID: {} for authUserId: {}", userId, authUserId);
                    return userId;
                } else {
                    log.warn("User ID is not a number: {} for authUserId: {}", userIdObj, authUserId);
                    return null;
                }
            } else {
                log.warn("Failed to get user ID for authUserId: {}, status: {}", authUserId, response.getStatusCode());
                return null;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("User Management Service 연결 실패 - authUserId: {}, error: {}", authUserId, e.getMessage());
            // 연결 실패 시 fallback: authUserId를 기반으로 ID 생성
            Long fallbackId = (long) Math.abs(authUserId.hashCode() % 1000000);
            log.warn("User Management Service 연결 실패로 인한 Fallback - generated ID: {} for authUserId: {}", fallbackId, authUserId);
            return fallbackId;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("User Management Service 클라이언트 오류 - authUserId: {}, status: {}, error: {}", 
                    authUserId, e.getStatusCode(), e.getMessage());
            if (e.getStatusCode().value() == 404) {
                log.info("User not found in User Management Service, will create user: authUserId={}", authUserId);
                return null; // 사용자가 없으면 null 반환, 호출자에서 처리
            }
            return null;
        } catch (Exception e) {
            log.error("예상치 못한 오류로 사용자 ID 조회 실패: authUserId={}", authUserId, e);
            return null;
        }
    }

    /**
     * User Management Service에 사용자 생성/동기화
     */
    public Long createOrSyncUser(String authUserId, String email, String name, String role) {
        if (mockMode) {
            log.info("[MOCK] Creating/syncing user for authUserId: {} - returning hash-based ID", authUserId);
            return (long) Math.abs(authUserId.hashCode() % 1000000);
        }
        
        try {
            String url = userServiceUrl + "/users/sync";
            log.info("Creating/syncing user for authUserId: {}, email: {}", authUserId, email);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("authUserId", authUserId);
            requestBody.put("email", email);
            requestBody.put("name", name);
            requestBody.put("role", role);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> userData = response.getBody();
                Object userIdObj = userData.get("id");
                
                if (userIdObj instanceof Number) {
                    Long userId = ((Number) userIdObj).longValue();
                    log.info("Successfully created/synced user ID: {} for authUserId: {}", userId, authUserId);
                    return userId;
                } else {
                    log.warn("User ID is not a number: {} for authUserId: {}", userIdObj, authUserId);
                    return null;
                }
            } else {
                log.warn("Failed to create/sync user for authUserId: {}, status: {}", authUserId, response.getStatusCode());
                return null;
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("User Management Service 연결 실패 - 사용자 생성/동기화 실패: authUserId={}, error={}", authUserId, e.getMessage());
            // 연결 실패 시 fallback: authUserId를 기반으로 ID 생성
            Long fallbackId = (long) Math.abs(authUserId.hashCode() % 1000000);
            log.warn("User Management Service 연결 실패로 인한 Fallback - generated ID: {} for authUserId: {}", fallbackId, authUserId);
            return fallbackId;
        } catch (Exception e) {
            log.error("예상치 못한 오류로 사용자 생성/동기화 실패: authUserId={}", authUserId, e);
            return null;
        }
    }
}