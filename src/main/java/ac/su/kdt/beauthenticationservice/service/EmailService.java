package ac.su.kdt.beauthenticationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 이메일 발송 서비스
 * 현재는 모의 구현체이며, 실제 환경에서는 SMTP, SES, SendGrid 등을 연동합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    /**
     * 비밀번호 재설정 이메일을 발송합니다.
     */
    public void sendPasswordResetEmail(String toEmail, String userName, String resetToken) {
        try {
            String resetLink = generateResetLink(resetToken);
            
            String subject = "비밀번호 재설정 요청 - DevOps 교육 플랫폼";
            String htmlContent = buildPasswordResetEmailContent(userName, resetLink, resetToken);
            
            // 실제 환경에서는 여기서 이메일 발송 로직 구현
            sendEmail(toEmail, subject, htmlContent);
            
            log.info("Password reset email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }
    
    /**
     * 회원가입 환영 이메일을 발송합니다.
     */
    public void sendWelcomeEmail(String toEmail, String userName) {
        try {
            String subject = "가입을 환영합니다! - DevOps 교육 플랫폼";
            String htmlContent = buildWelcomeEmailContent(userName);
            
            sendEmail(toEmail, subject, htmlContent);
            
            log.info("Welcome email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", toEmail, e);
            // 환영 이메일 실패는 비즈니스 로직에 영향을 주지 않음
        }
    }
    
    /**
     * 로그인 알림 이메일을 발송합니다.
     */
    public void sendLoginNotificationEmail(String toEmail, String userName, String ipAddress, String userAgent) {
        try {
            String subject = "새로운 로그인이 감지되었습니다 - DevOps 교육 플랫폼";
            String htmlContent = buildLoginNotificationEmailContent(userName, ipAddress, userAgent);
            
            sendEmail(toEmail, subject, htmlContent);
            
            log.info("Login notification email sent successfully to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send login notification email to: {}", toEmail, e);
            // 로그인 알림 이메일 실패는 비즈니스 로직에 영향을 주지 않음
        }
    }
    
    /**
     * 실제 이메일 발송 로직 (현재는 모의 구현)
     */
    private void sendEmail(String toEmail, String subject, String htmlContent) {
        // 현재는 로그로만 출력 (개발 환경)
        log.info("=== EMAIL SENT ===");
        log.info("To: {}", toEmail);
        log.info("Subject: {}", subject);
        log.info("Content: {}", htmlContent);
        log.info("Sent at: {}", LocalDateTime.now());
        log.info("==================");
        
        // 실제 환경에서는 아래와 같은 구현 사용:
        /*
        // SMTP 사용 예시
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(htmlContent);
        mailSender.send(message);
        
        // 또는 AWS SES, SendGrid 등의 API 호출
        */
    }
    
    /**
     * 비밀번호 재설정 링크를 생성합니다.
     */
    private String generateResetLink(String resetToken) {
        // 실제 환경에서는 프론트엔드 도메인을 사용
        String baseUrl = "http://localhost:3000"; // 또는 설정에서 읽기
        return baseUrl + "/reset-password?token=" + resetToken;
    }
    
    /**
     * 비밀번호 재설정 이메일 HTML 콘텐츠를 생성합니다.
     */
    private String buildPasswordResetEmailContent(String userName, String resetLink, String resetToken) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>비밀번호 재설정</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #007bff;">🔐 비밀번호 재설정 요청</h2>
                    
                    <p>안녕하세요, %s님!</p>
                    
                    <p>DevOps 교육 플랫폼 계정의 비밀번호 재설정을 요청하셨습니다.</p>
                    
                    <p>아래 버튼을 클릭하여 비밀번호를 재설정하세요:</p>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #007bff; color: white; padding: 12px 24px; 
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            비밀번호 재설정하기
                        </a>
                    </div>
                    
                    <p><strong>보안을 위한 안내사항:</strong></p>
                    <ul>
                        <li>이 링크는 <strong>15분</strong> 후에 만료됩니다</li>
                        <li>링크는 <strong>1회</strong>만 사용 가능합니다</li>
                        <li>본인이 요청하지 않았다면 이 이메일을 무시하세요</li>
                    </ul>
                    
                    <p>문제가 있거나 도움이 필요하시면 고객센터로 연락해 주세요.</p>
                    
                    <hr style="margin: 30px 0; border: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        이 이메일은 자동으로 발송되었습니다. 답장하지 마세요.<br>
                        DevOps 교육 플랫폼<br>
                        발송 시각: %s
                    </p>
                </div>
            </body>
            </html>
            """, userName, resetLink, LocalDateTime.now());
    }
    
    /**
     * 환영 이메일 HTML 콘텐츠를 생성합니다.
     */
    private String buildWelcomeEmailContent(String userName) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>환영합니다!</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #28a745;">🎉 환영합니다!</h2>
                    
                    <p>안녕하세요, %s님!</p>
                    
                    <p>DevOps 교육 플랫폼에 가입해 주셔서 감사합니다!</p>
                    
                    <p><strong>플랫폼 주요 기능:</strong></p>
                    <ul>
                        <li>🛠️ 실습 환경 자동 프로비저닝</li>
                        <li>🤖 AI 기반 학습 추천</li>
                        <li>📊 학습 진도 추적</li>
                        <li>👥 커뮤니티 및 멘토링</li>
                    </ul>
                    
                    <p><strong>시작하기:</strong></p>
                    <ol>
                        <li>프로필을 완성하세요</li>
                        <li>관심 있는 기술 스택을 선택하세요</li>
                        <li>첫 번째 실습을 시작하세요</li>
                    </ol>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="http://localhost:3000/dashboard" 
                           style="background-color: #28a745; color: white; padding: 12px 24px; 
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            지금 시작하기
                        </a>
                    </div>
                    
                    <p>궁금한 점이 있으시면 언제든 고객센터로 연락주세요.</p>
                    
                    <p>다시 한번 환영합니다! 🚀</p>
                    
                    <hr style="margin: 30px 0; border: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        DevOps 교육 플랫폼 팀<br>
                        발송 시각: %s
                    </p>
                </div>
            </body>
            </html>
            """, userName, LocalDateTime.now());
    }
    
    /**
     * 로그인 알림 이메일 HTML 콘텐츠를 생성합니다.
     */
    private String buildLoginNotificationEmailContent(String userName, String ipAddress, String userAgent) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>로그인 알림</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #ffc107;">🔔 새로운 로그인 감지</h2>
                    
                    <p>안녕하세요, %s님!</p>
                    
                    <p>귀하의 계정에 새로운 로그인이 감지되었습니다.</p>
                    
                    <div style="background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 20px 0;">
                        <p><strong>로그인 정보:</strong></p>
                        <ul>
                            <li><strong>시간:</strong> %s</li>
                            <li><strong>IP 주소:</strong> %s</li>
                            <li><strong>기기/브라우저:</strong> %s</li>
                        </ul>
                    </div>
                    
                    <p>본인의 로그인이 맞다면 이 이메일을 무시하셔도 됩니다.</p>
                    
                    <p><strong>본인의 로그인이 아닌 경우:</strong></p>
                    <ul>
                        <li>즉시 비밀번호를 변경하세요</li>
                        <li>의심스러운 활동이 있는지 계정을 확인하세요</li>
                        <li>고객센터에 신고해 주세요</li>
                    </ul>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="http://localhost:3000/security" 
                           style="background-color: #dc3545; color: white; padding: 12px 24px; 
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            보안 설정 확인
                        </a>
                    </div>
                    
                    <hr style="margin: 30px 0; border: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        계정 보안을 위해 발송된 자동 알림입니다.<br>
                        DevOps 교육 플랫폼
                    </p>
                </div>
            </body>
            </html>
            """, userName, LocalDateTime.now(), ipAddress, userAgent);
    }
    
    /**
     * 결제 실패 보안 이메일 발송
     */
    public void sendPaymentFailureSecurityEmail(String email, String failureReason, Integer retryCount, Double failedAmount) {
        log.info("📧 [EMAIL_SERVICE] Sending payment failure security email to: {}", maskEmail(email));
        log.info("📧 Failure details - Reason: {}, Retry: {}, Amount: {}", failureReason, retryCount, failedAmount);
        
        String subject = "결제 실패 보안 알림 - DevOps 교육 플랫폼";
        String htmlContent = buildPaymentFailureEmailContent(failureReason, retryCount, failedAmount);
        
        sendEmail(email, subject, htmlContent);
    }
    
    /**
     * 계정 정지 이메일 발송
     */
    public void sendAccountSuspensionEmail(String email, String reason) {
        log.warn("📧 [EMAIL_SERVICE] Sending account suspension email to: {}", maskEmail(email));
        log.warn("📧 Suspension reason: {}", reason);
        
        String subject = "계정 정지 안내 - DevOps 교육 플랫폼";
        String htmlContent = buildAccountSuspensionEmailContent(reason);
        
        sendEmail(email, subject, htmlContent);
    }
    
    /**
     * 업그레이드 환영 이메일 발송
     */
    public void sendUpgradeWelcomeEmail(String email, String newPlan) {
        log.info("📧 [EMAIL_SERVICE] Sending upgrade welcome email to: {}", maskEmail(email));
        log.info("📧 New plan: {}", newPlan);
        
        String subject = newPlan + " 플랜 업그레이드 축하 - DevOps 교육 플랫폼";
        String htmlContent = buildUpgradeWelcomeEmailContent(newPlan);
        
        sendEmail(email, subject, htmlContent);
    }
    
    /**
     * 잔액 부족 마케팅 이메일 발송
     */
    public void sendLowBalanceMarketingEmail(String email, Integer currentBalance, String subscriptionPlan, Integer suggestedRechargeAmount) {
        log.info("📧 [EMAIL_SERVICE] Sending low balance marketing email to: {}", maskEmail(email));
        log.info("📧 Balance: {}, Plan: {}, Suggested: {}", currentBalance, subscriptionPlan, suggestedRechargeAmount);
        
        String subject = "티켓 충전 및 플랜 업그레이드 제안 - DevOps 교육 플랫폼";
        String htmlContent = buildLowBalanceMarketingEmailContent(currentBalance, subscriptionPlan, suggestedRechargeAmount);
        
        sendEmail(email, subject, htmlContent);
    }
    
    private String buildPaymentFailureEmailContent(String failureReason, Integer retryCount, Double failedAmount) {
        return String.format("""
            <div style="max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;">
                <h2 style="color: #dc3545;">⚠️ 결제 실패 알림</h2>
                <p>구독 결제 처리 중 문제가 발생했습니다.</p>
                <div style="background-color: #f8d7da; padding: 15px; border-radius: 5px; margin: 20px 0;">
                    <p><strong>실패 정보:</strong></p>
                    <ul>
                        <li>실패 사유: %s</li>
                        <li>재시도 횟수: %d회</li>
                        <li>결제 금액: %.0f원</li>
                    </ul>
                </div>
                <p>결제 방법을 확인하고 다시 시도해 주세요.</p>
            </div>
            """, getFailureReasonKorean(failureReason), retryCount, failedAmount);
    }
    
    private String buildAccountSuspensionEmailContent(String reason) {
        return String.format("""
            <div style="max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;">
                <h2 style="color: #dc3545;">🚫 계정 정지 안내</h2>
                <p>귀하의 계정이 일시적으로 정지되었습니다.</p>
                <div style="background-color: #f8d7da; padding: 15px; border-radius: 5px; margin: 20px 0;">
                    <p><strong>정지 사유:</strong> %s</p>
                </div>
                <p>계정 복구를 위해 고객센터로 연락해 주세요.</p>
            </div>
            """, reason);
    }
    
    private String buildUpgradeWelcomeEmailContent(String newPlan) {
        return String.format("""
            <div style="max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;">
                <h2 style="color: #28a745;">🎉 플랜 업그레이드 축하</h2>
                <p>%s 플랜으로 업그레이드해 주셔서 감사합니다!</p>
                <div style="background-color: #d4edda; padding: 15px; border-radius: 5px; margin: 20px 0;">
                    <p><strong>새로운 기능들을 확인해보세요:</strong></p>
                    <ul>
                        <li>더 많은 실습 환경</li>
                        <li>우선 지원</li>
                        <li>고급 기능 액세스</li>
                    </ul>
                </div>
            </div>
            """, newPlan);
    }
    
    private String buildLowBalanceMarketingEmailContent(Integer currentBalance, String subscriptionPlan, Integer suggestedRechargeAmount) {
        return String.format("""
            <div style="max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;">
                <h2 style="color: #ffc107;">⚡ 티켓 충전 제안</h2>
                <p>현재 티켓 잔액이 부족합니다.</p>
                <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; margin: 20px 0;">
                    <ul>
                        <li>현재 잔액: %d개</li>
                        <li>현재 플랜: %s</li>
                        <li>권장 충전량: %d개</li>
                    </ul>
                </div>
                <p>상위 플랜으로 업그레이드하면 더 많은 혜택을 받으실 수 있습니다!</p>
            </div>
            """, currentBalance, subscriptionPlan, suggestedRechargeAmount);
    }
    
    private String getFailureReasonKorean(String failureReason) {
        return switch (failureReason) {
            case "PAYMENT_FAILED" -> "결제 실패";
            case "CARD_EXPIRED" -> "카드 만료";
            case "INSUFFICIENT_FUNDS" -> "잔액 부족";
            case "CARD_DECLINED" -> "카드 거부";
            default -> failureReason;
        };
    }
    
    /**
     * 이메일 마스킹
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 2) {
            return username + "@" + domain;
        }
        
        String maskedUsername = username.substring(0, 2) + "*".repeat(username.length() - 2);
        return maskedUsername + "@" + domain;
    }
}