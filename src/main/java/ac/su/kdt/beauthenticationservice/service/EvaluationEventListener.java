package ac.su.kdt.beauthenticationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class EvaluationEventListener {
    
    /**
     * AI 평가 이벤트 처리 - 인증 서비스 관점
     * AI 평가 이벤트는 AI 평가 시스템에서 발생하는 이벤트입니다.
     * 이벤트는 평가 시작, 실패, 재시도 요청, 재시도 완료 등의 정보를 포함합니다.
     * 이벤트는 평가 ID, 사용자 ID, 평가 유형, 평가 결과 등의 정보를 포함합니다.
     */
    @KafkaListener(topics = "evaluation-events", groupId = "auth-service-evaluation-group")
    public void handleEvaluationEvent(Map<String, Object> eventData) {
        String eventType = (String) eventData.get("eventType");
        
        try {
            switch (eventType) {
                case "evaluation.started":
                    handleEvaluationStarted(eventData);
                    break;
                case "evaluation.failed":
                    handleEvaluationFailed(eventData);
                    break;
                case "evaluation.retry-requested":
                    handleEvaluationRetryRequested(eventData);
                    break;
                case "evaluation.retry-completed":
                    handleEvaluationRetryCompleted(eventData);
                    break;
                default:
                    log.debug("Unknown evaluation event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error handling evaluation event: {}", eventType, e);
        }
    }
    
    /**
     * 평가 시작 이벤트 처리
     * - 사용자 활동 기록 업데이트
     * - 사용자 평가 이력 추적
     */
    private void handleEvaluationStarted(Map<String, Object> eventData) {
        String evaluationId = (String) eventData.get("evaluationId");
        String missionId = (String) eventData.get("missionId");
        String missionTitle = (String) eventData.get("missionTitle");
        Long userId = Long.valueOf(eventData.get("userId").toString());
        String evaluationType = (String) eventData.get("evaluationType");
        
        log.info("🚀 [AUTH_SERVICE] Processing evaluation started event: evaluationId={}, userId={}, evaluationType={}", 
            evaluationId, userId, evaluationType);
        
        // Log evaluation started event for audit purposes
        log.info("✅ [AUTH_SERVICE] Evaluation started recorded: userId={}, evaluationId={}, missionTitle={}", 
            userId, evaluationId, missionTitle);
    }
    
    /**
     * 평가 실패 이벤트 처리
     * - 사용자 실패 이력 기록
     * - 실패 패턴 분석을 위한 데이터 수집
     */
    private void handleEvaluationFailed(Map<String, Object> eventData) {
        String evaluationId = (String) eventData.get("evaluationId");
        String missionId = (String) eventData.get("missionId");
        Long userId = Long.valueOf(eventData.get("userId").toString());
        String failureReason = (String) eventData.get("failureReason");
        String errorCode = (String) eventData.get("errorCode");
        Integer retryAttempt = (Integer) eventData.get("retryAttempt");
        Boolean isRetryable = (Boolean) eventData.get("isRetryable");
        
        log.warn("🚨 [AUTH_SERVICE] Processing evaluation failed event: evaluationId={}, userId={}, reason={}, retryable={}", 
            evaluationId, userId, failureReason, isRetryable);
        
        // Log evaluation failure event for audit purposes
        log.warn("❌ [AUTH_SERVICE] Evaluation failed: userId={}, evaluationId={}, reason={}, errorCode={}", 
            userId, evaluationId, failureReason, errorCode);
        
        // Alert for repeated failures
        if (retryAttempt != null && retryAttempt >= 3) {
            log.error("🚨 [AUTH_SERVICE] Repeated evaluation failures detected: userId={}, attempts={}", 
                userId, retryAttempt);
        }
    }
    
    /**
     * 평가 재시도 요청 이벤트 처리
     * - 재시도 요청 이력 기록
     */
    private void handleEvaluationRetryRequested(Map<String, Object> eventData) {
        String evaluationId = (String) eventData.get("evaluationId");
        String originalEvaluationId = (String) eventData.get("originalEvaluationId");
        String missionId = (String) eventData.get("missionId");
        Long userId = Long.valueOf(eventData.get("userId").toString());
        Integer retryAttempt = (Integer) eventData.get("retryAttempt");
        String retryTrigger = (String) eventData.get("retryTrigger");
        String retryStrategy = (String) eventData.get("retryStrategy");
        
        log.info("🔄 [AUTH_SERVICE] Processing evaluation retry requested event: evaluationId={}, userId={}, retryAttempt={}, trigger={}", 
            evaluationId, userId, retryAttempt, retryTrigger);
        
        // Log retry request for audit purposes
        log.info("🔄 [AUTH_SERVICE] Evaluation retry requested: userId={}, evaluationId={}, attempt={}, trigger={}", 
            userId, evaluationId, retryAttempt, retryTrigger);
    }
    
    /**
     * 평가 재시도 완료 이벤트 처리
     * - 재시도 결과 기록
     * - 사용자 성과 통계 업데이트
     */
    private void handleEvaluationRetryCompleted(Map<String, Object> eventData) {
        String evaluationId = (String) eventData.get("evaluationId");
        String originalEvaluationId = (String) eventData.get("originalEvaluationId");
        String missionId = (String) eventData.get("missionId");
        Long userId = Long.valueOf(eventData.get("userId").toString());
        Integer retryAttempt = (Integer) eventData.get("retryAttempt");
        String retryStatus = (String) eventData.get("retryStatus");
        Integer finalScore = (Integer) eventData.get("finalScore");
        Integer retryDurationMinutes = (Integer) eventData.get("retryDurationMinutes");
        Boolean needsHumanReview = (Boolean) eventData.get("needsHumanReview");
        
        log.info("✅ [AUTH_SERVICE] Processing evaluation retry completed event: evaluationId={}, userId={}, status={}, finalScore={}", 
            evaluationId, userId, retryStatus, finalScore);
        
        // Log retry completion for audit purposes
        log.info("✅ [AUTH_SERVICE] Evaluation retry completed: userId={}, evaluationId={}, status={}, score={}", 
            userId, evaluationId, retryStatus, finalScore);
        
        if ("SUCCESS".equals(retryStatus)) {
            log.info("🎉 [AUTH_SERVICE] Evaluation retry succeeded: userId={}, score={}, attempts={}", 
                userId, finalScore, retryAttempt);
        } else if (Boolean.TRUE.equals(needsHumanReview)) {
            log.warn("👤 [AUTH_SERVICE] Manual review required for evaluation: userId={}, evaluationId={}", 
                userId, evaluationId);
        }
    }
}