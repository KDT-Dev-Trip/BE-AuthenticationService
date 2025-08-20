package ac.su.kdt.beauthenticationservice.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class LoggingConfig {
    
    @Bean
    public Logger structuredLogger() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Console Appender 생성
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName("CONSOLE");
        
        // Pattern Layout Encoder 설정
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();
        
        // Root Logger 설정
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);
        
        return rootLogger;
    }
    
    private static final org.slf4j.Logger staticLog = LoggerFactory.getLogger(LoggingConfig.class);
    
    public static void logAuthEvent(String eventType, String userId, String email, 
                                   String ipAddress, boolean success) {
        if (success) {
            staticLog.info("auth.{}.success userId={} email={} ipAddress={}", 
                    eventType, userId, email, ipAddress);
        } else {
            staticLog.warn("auth.{}.failure email={} ipAddress={}", 
                    eventType, email, ipAddress);
        }
    }
    
    public static void logSignupEvent(String userId, String email, String source) {
        staticLog.info("auth.signup.success userId={} email={} source={}", 
                userId, email, source);
    }
    
    public static void logLoginEvent(String userId, String email, String ipAddress, String method) {
        staticLog.info("auth.login.success userId={} email={} ipAddress={} method={}", 
                userId, email, ipAddress, method);
    }
    
    public static void logPasswordResetEvent(String userId, String email) {
        staticLog.info("auth.password.reset.requested userId={} email={}", 
                userId, email);
    }
}