package ac.su.kdt.beauthenticationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionEventListener {
    
    private final AuthSecurityService authSecurityService;
    private final EmailService emailService;
    
    /**
     * 미션 이벤트 처리 - 인증/보안 서비스 관점
     */
    @KafkaListener(topics = "mission-events", groupId = "auth-service-mission-group")
    public void handleMissionEvent(Map<String, Object> eventData) {
        String eventType = (String) eventData.get("eventType");
        
        try {
            switch (eventType) {
                case "mission.paused":
                    handleMissionPaused(eventData);
                    break;
                case "mission.resumed":
                    handleMissionResumed(eventData);
                    break;
                case "mission.resource-provisioning-failed":
                    handleResourceProvisioningFailed(eventData);
                    break;
                case "mission.resource-cleanup-completed":
                    handleResourceCleanupCompleted(eventData);
                    break;
                default:
                    log.debug("Unknown mission event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error handling mission event: {}", eventType, e);
        }
    }
    
    /**
     * 미션 일시정지 이벤트 처리 - 보안 관점
     */
    private void handleMissionPaused(Map<String, Object> eventData) {
        try {
            Long userId = Long.valueOf(eventData.get("userId").toString());
            String authUserId = (String) eventData.get("authUserId");
            String email = (String) eventData.get("email");
            String missionTitle = (String) eventData.get("missionTitle");
            String attemptId = (String) eventData.get("attemptId");
            String pauseReason = (String) eventData.get("pauseReason");
            
            log.info("🔄 [AUTH_SERVICE] Mission paused - Security processing: authUserId={}, mission={}", 
                    authUserId, missionTitle);
            
            // 1. 미션 세션 상태 업데이트 (보안 로그용)
            authSecurityService.setUserFlag(authUserId, "mission_paused", true);
            authSecurityService.setUserFlag(authUserId, "mission_paused", true);
            
            // 2. 장시간 일시정지시 보안 알림
            if ("장시간_비활성".equals(pauseReason) || "TIMEOUT".equals(pauseReason)) {
                log.warn("📧 [AUTH_SERVICE] Would send security alert for mission pause: user={}, mission={}, reason={}", 
                    email, missionTitle, pauseReason);
                
                // 보안 세션 타임아웃 설정
                log.info("🔒 [AUTH_SERVICE] Would reduce session timeout for frequent pause: user={}", authUserId);
            }
            
            // 3. 미션 일시정지 이력 기록 (보안 감사용)
            Map<String, Object> auditData = Map.of(
                "event", "mission_paused",
                "missionTitle", missionTitle,
                "attemptId", attemptId,
                "reason", pauseReason,
                "userId", userId
            );
            authSecurityService.logSecurityEvent(authUserId, email, "MISSION_PAUSE", auditData);
            
            // 4. 의심스러운 패턴 감지 (빈번한 일시정지)
            if (isFrequentPausePattern(authUserId)) {
                log.warn("📧 [AUTH_SERVICE] Would send suspicious activity alert: user={}, mission={}", email, missionTitle);
                authSecurityService.setUserFlag(authUserId, "suspicious_activity", true);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle mission paused event in auth service", e);
        }
    }
    
    /**
     * 미션 재개 이벤트 처리 - 보안 관점
     */
    private void handleMissionResumed(Map<String, Object> eventData) {
        try {
            Long userId = Long.valueOf(eventData.get("userId").toString());
            String authUserId = (String) eventData.get("authUserId");
            String email = (String) eventData.get("email");
            String missionTitle = (String) eventData.get("missionTitle");
            String attemptId = (String) eventData.get("attemptId");
            Long pauseDurationMinutes = Long.valueOf(eventData.get("pauseDurationMinutes").toString());
            
            log.info("▶️ [AUTH_SERVICE] Mission resumed - Security processing: authUserId={}, pauseTime={}min", 
                    authUserId, pauseDurationMinutes);
            
            // 1. 미션 세션 상태 복원
            authSecurityService.setUserFlag(authUserId, "mission_paused", false);
            authSecurityService.setUserFlag(authUserId, "mission_active", true);
            
            // 2. 보안 검증 수행 (긴 일시정지 후 재개시)
            if (pauseDurationMinutes > 60) { // 1시간 이상 일시정지
                // 추가 보안 검증 요구
                authSecurityService.setUserFlag(authUserId, "requires_verification", true);
                log.info("📧 [AUTH_SERVICE] Would send extended pause notice: user={}, mission={}, duration={}min", 
                    email, missionTitle, pauseDurationMinutes);
            }
            
            // 3. 세션 보안 레벨 복원
            authSecurityService.setUserFlag(authUserId, "mission_paused", false);
            
            // 4. 미션 재개 보안 로그 기록
            Map<String, Object> auditData = Map.of(
                "event", "mission_resumed",
                "missionTitle", missionTitle,
                "attemptId", attemptId,
                "pauseDurationMinutes", pauseDurationMinutes,
                "userId", userId
            );
            authSecurityService.logSecurityEvent(authUserId, email, "MISSION_RESUME", auditData);
            
        } catch (Exception e) {
            log.error("Failed to handle mission resumed event in auth service", e);
        }
    }
    
    /**
     * 리소스 프로비저닝 실패 이벤트 처리 - 보안 관점
     */
    private void handleResourceProvisioningFailed(Map<String, Object> eventData) {
        try {
            Long userId = Long.valueOf(eventData.get("userId").toString());
            String authUserId = (String) eventData.get("authUserId");
            String email = (String) eventData.get("email");
            String missionTitle = (String) eventData.get("missionTitle");
            String resourceType = (String) eventData.get("resourceType");
            String failureReason = (String) eventData.get("failureReason");
            Integer retryAttempt = (Integer) eventData.get("retryAttempt");
            
            log.warn("🚨 [AUTH_SERVICE] Resource provisioning failed - Security implications: authUserId={}, resource={}", 
                    authUserId, resourceType);
            
            // 1. 보안 이벤트 로깅
            Map<String, Object> auditData = Map.of(
                "event", "resource_provisioning_failed",
                "missionTitle", missionTitle,
                "resourceType", resourceType,
                "failureReason", failureReason,
                "retryAttempt", retryAttempt != null ? retryAttempt : 1,
                "userId", userId
            );
            authSecurityService.logSecurityEvent(authUserId, email, "RESOURCE_FAILURE", auditData);
            
            // 2. 사용자 계정 상태 임시 제한 (연속 실패시)
            if (retryAttempt != null && retryAttempt >= 3) {
                log.warn("🚫 [AUTH_SERVICE] Would apply temporary restriction for user: {}", authUserId);
                log.warn("📧 [AUTH_SERVICE] Would send account restriction notice: user={}, mission={}", email, missionTitle);
            }
            
            // 3. 시스템 관리자 알림 (심각한 장애)
            if ("QUOTA_EXCEEDED".equals(failureReason) || "CLUSTER_UNAVAILABLE".equals(failureReason)) {
                log.error("🚨 [AUTH_SERVICE] Critical resource failure - admin alert: user={}, mission={}, reason={}", 
                    authUserId, missionTitle, failureReason);
            }
            
            // 4. 보안 모니터링 - 리소스 공격 패턴 감지
            if (isPotentialResourceAttack(authUserId, failureReason)) {
                authSecurityService.setUserFlag(authUserId, "potential_attack", true);
                log.warn("📧 [AUTH_SERVICE] Would send security alert: user={}, mission={}", email, missionTitle);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle resource provisioning failed event in auth service", e);
        }
    }
    
    /**
     * 리소스 정리 완료 이벤트 처리 - 보안 관점
     */
    private void handleResourceCleanupCompleted(Map<String, Object> eventData) {
        try {
            Long userId = Long.valueOf(eventData.get("userId").toString());
            String authUserId = (String) eventData.get("authUserId");
            String email = (String) eventData.get("email");
            String missionTitle = (String) eventData.get("missionTitle");
            String cleanupTrigger = (String) eventData.get("cleanupTrigger");
            Integer totalResourcesCleaned = (Integer) eventData.get("totalResourcesCleaned");
            Boolean dataBackupCreated = (Boolean) eventData.get("dataBackupCreated");
            
            log.info("🧹 [AUTH_SERVICE] Resource cleanup completed - Security cleanup: authUserId={}, trigger={}", 
                    authUserId, cleanupTrigger);
            
            // 1. 미션 관련 임시 권한 모두 제거
            log.info("🔓 [AUTH_SERVICE] Would revoke mission permissions: user={}, mission={}", authUserId, missionTitle);
            
            // 2. 세션 플래그 정리
            authSecurityService.setUserFlag(authUserId, "mission_active", false);
            
            // 3. 보안 감사 로그 기록
            Map<String, Object> auditData = Map.of(
                "event", "resource_cleanup_completed",
                "missionTitle", missionTitle,
                "cleanupTrigger", cleanupTrigger,
                "resourcesCleaned", totalResourcesCleaned,
                "dataBackupCreated", dataBackupCreated != null ? dataBackupCreated : false,
                "userId", userId
            );
            authSecurityService.logSecurityEvent(authUserId, email, "RESOURCE_CLEANUP", auditData);
            
            // 4. 미션 완료시 성과 인증서 발행 준비
            if ("MISSION_COMPLETED".equals(cleanupTrigger)) {
                log.info("🏆 [AUTH_SERVICE] Would prepare certificate issuance: user={}, mission={}", authUserId, missionTitle);
                log.info("📧 [AUTH_SERVICE] Would send mission completion confirmation: user={}, mission={}", email, missionTitle);
            }
            
            // 5. 정리 완료 보안 확인 메일
            log.info("📧 [AUTH_SERVICE] Would send cleanup confirmation: user={}, mission={}, resources={}", 
                email, missionTitle, totalResourcesCleaned);
            
        } catch (Exception e) {
            log.error("Failed to handle resource cleanup completed event in auth service", e);
        }
    }
    
    /**
     * 빈번한 일시정지 패턴 감지
     */
    private boolean isFrequentPausePattern(String authUserId) {
        // 실제 구현에서는 Redis나 DB에서 최근 일시정지 이력 조회
        // 예: 1시간 내 3회 이상 일시정지
        return false; // 임시로 false 반환
    }
    
    /**
     * 리소스 공격 패턴 감지
     */
    private boolean isPotentialResourceAttack(String authUserId, String failureReason) {
        // 실제 구현에서는 다음 패턴들 감지:
        // 1. 짧은 시간 내 많은 리소스 요청
        // 2. 의도적으로 큰 리소스 요청
        // 3. 비정상적인 실패 패턴
        return "RESOURCE_LIMIT_EXCEEDED".equals(failureReason) || 
               "MALFORMED_REQUEST".equals(failureReason);
    }
}