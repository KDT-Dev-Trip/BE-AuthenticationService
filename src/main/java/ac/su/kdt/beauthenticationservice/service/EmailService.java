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
}