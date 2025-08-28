package ac.su.kdt.beauthenticationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    
    private final AuthSecurityService authSecurityService;
    private final EmailService emailService;
    
    @KafkaListener(topics = "payment-events", groupId = "auth-service-payment-group")
    public void handlePaymentEvent(@Payload Map<String, Object> eventData) {
        try {
            String eventType = (String) eventData.get("event_type");
            
            switch (eventType) {
                case "payment.subscription-renewal-failed" -> handleSubscriptionRenewalFailed(eventData);
                case "payment.subscription-changed" -> handleSubscriptionChanged(eventData);
                case "payment.ticket-balance-low" -> handleTicketBalanceLow(eventData);
                default -> log.debug("Unknown payment event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing payment event: {}", eventData, e);
        }
    }
    
    private void handleSubscriptionRenewalFailed(Map<String, Object> eventData) {
        String authUserId = (String) eventData.get("auth_user_id");
        String email = (String) eventData.get("email");
        String failureReason = (String) eventData.get("failureReason");
        Integer retryCount = (Integer) eventData.get("retryAttemptCount");
        String subscriptionPlan = (String) eventData.get("subscriptionPlan");
        Double failedAmount = ((Number) eventData.get("failedAmount")).doubleValue();
        
        log.warn("Handling subscription renewal failed for auth user: {}, email: {}, reason: {}, retry: {}", 
                authUserId, email, failureReason, retryCount);
        
        try {
            // 1. 사용자 인증/권한 상태 업데이트
            authSecurityService.updateUserPaymentStatus(authUserId, "PAYMENT_FAILED", retryCount);
            
            // 2. 결제 실패 보안 이메일 발송
            emailService.sendPaymentFailureSecurityEmail(email, failureReason, retryCount, failedAmount);
            
            // 3. 최대 재시도 횟수 초과 시 계정 제한
            if (retryCount >= 3) {
                authSecurityService.suspendUserAccess(authUserId, "PAYMENT_FAILURE_EXCEEDED");
                emailService.sendAccountSuspensionEmail(email, "결제 실패로 인한 계정 제한");
                
                log.warn("User account suspended due to payment failure: authUserId={}, email={}", authUserId, email);
            }
            
            // 4. 보안 로그 기록
            authSecurityService.logSecurityEvent(authUserId, email, "SUBSCRIPTION_PAYMENT_FAILED", 
                Map.of("reason", failureReason, "retryCount", retryCount, "amount", failedAmount));
                
        } catch (Exception e) {
            log.error("Error processing subscription renewal failed event for auth user {}", authUserId, e);
        }
    }
    
    private void handleSubscriptionChanged(Map<String, Object> eventData) {
        String authUserId = (String) eventData.get("auth_user_id");
        String email = (String) eventData.get("email");
        String oldPlan = (String) eventData.get("oldPlan");
        String newPlan = (String) eventData.get("newPlan");
        String changeReason = (String) eventData.get("changeReason");
        Boolean isProratedRefund = (Boolean) eventData.get("isProratedRefund");
        Double proratedAmount = eventData.get("proratedAmount") != null ? 
            ((Number) eventData.get("proratedAmount")).doubleValue() : null;
        
        log.info("Handling subscription changed for auth user: {}, email: {}, {} -> {}, reason: {}", 
                authUserId, email, oldPlan, newPlan, changeReason);
        
        try {
            // 1. 사용자 역할/권한 업데이트
            authSecurityService.updateUserRole(authUserId, newPlan);
            
            // 2. 업그레이드/다운그레이드에 따른 권한 조정
            boolean isUpgrade = isUpgrade(oldPlan, newPlan);
            if (isUpgrade) {
                authSecurityService.grantUpgradePermissions(authUserId, newPlan);
                emailService.sendUpgradeWelcomeEmail(email, newPlan);
            } else {
                authSecurityService.restrictDowngradePermissions(authUserId, newPlan);
            }
            
            // 3. 결제 복구 (이전에 실패했다면)
            authSecurityService.restoreUserAccess(authUserId, "SUBSCRIPTION_UPDATED");
            
            // 4. 보안 로그 기록
            authSecurityService.logSecurityEvent(authUserId, email, "SUBSCRIPTION_PLAN_CHANGED", 
                Map.of(
                    "oldPlan", oldPlan, 
                    "newPlan", newPlan, 
                    "reason", changeReason,
                    "isUpgrade", isUpgrade
                ));
                
            log.info("Successfully updated user permissions for plan change: authUserId={}, {} -> {}", 
                    authUserId, oldPlan, newPlan);
                    
        } catch (Exception e) {
            log.error("Error processing subscription changed event for auth user {}", authUserId, e);
        }
    }
    
    private void handleTicketBalanceLow(Map<String, Object> eventData) {
        String authUserId = (String) eventData.get("auth_user_id");
        String email = (String) eventData.get("email");
        Integer currentBalance = (Integer) eventData.get("currentBalance");
        Integer thresholdLimit = (Integer) eventData.get("thresholdLimit");
        String subscriptionPlan = (String) eventData.get("subscriptionPlan");
        Integer suggestedRechargeAmount = (Integer) eventData.get("suggestedRechargeAmount");
        
        log.warn("Handling ticket balance low for auth user: {}, email: {}, balance: {}/{}, plan: {}", 
                authUserId, email, currentBalance, thresholdLimit, subscriptionPlan);
        
        try {
            // 1. 사용자 세션에 잔액 부족 플래그 설정 (다음 로그인 시 알림용)
            authSecurityService.setUserFlag(authUserId, "LOW_BALANCE_WARNING", true);
            
            // 2. 잔액 부족 마케팅 이메일 발송
            emailService.sendLowBalanceMarketingEmail(email, currentBalance, subscriptionPlan, suggestedRechargeAmount);
            
            // 3. 기본 플랜 사용자에게 업그레이드 캠페인 트리거
            if (isBasicPlan(subscriptionPlan)) {
                authSecurityService.triggerUpgradeReminder(authUserId, subscriptionPlan);
            }
            
            // 4. 보안 로그 기록 (사용 패턴 분석용)
            authSecurityService.logSecurityEvent(authUserId, email, "TICKET_BALANCE_LOW", 
                Map.of(
                    "currentBalance", currentBalance, 
                    "thresholdLimit", thresholdLimit,
                    "subscriptionPlan", subscriptionPlan
                ));
                
        } catch (Exception e) {
            log.error("Error processing ticket balance low event for auth user {}", authUserId, e);
        }
    }
    
    /**
     * 플랜 업그레이드 여부 판단
     */
    private boolean isUpgrade(String oldPlan, String newPlan) {
        int oldRank = getPlanRank(oldPlan);
        int newRank = getPlanRank(newPlan);
        return newRank > oldRank;
    }
    
    /**
     * 플랜 등급 반환
     */
    private int getPlanRank(String planName) {
        if (planName == null) return 0;
        return switch (planName.toUpperCase()) {
            case "ECONOMY_CLASS", "ECONOMY" -> 1;
            case "BUSINESS_CLASS", "BUSINESS" -> 2;
            case "FIRST_CLASS", "FIRST" -> 3;
            case "ENTERPRISE_CLASS", "ENTERPRISE" -> 4;
            default -> 0;
        };
    }
    
    /**
     * 기본 플랜 여부 판단
     */
    private boolean isBasicPlan(String planName) {
        return "ECONOMY_CLASS".equalsIgnoreCase(planName) || "ECONOMY".equalsIgnoreCase(planName);
    }
}