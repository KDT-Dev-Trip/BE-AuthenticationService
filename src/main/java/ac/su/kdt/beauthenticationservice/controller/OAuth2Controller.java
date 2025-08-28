package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.config.OAuth2Properties;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.service.AuthService;
import ac.su.kdt.beauthenticationservice.service.AuthorizationCodeService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2 Authorization Server 컨트롤러
 * OAuth 2.0 Authorization Code Flow를 처리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth2 Authorization Server", description = "OAuth 2.0 인증 서버 API")
public class OAuth2Controller {
    // 하이브리드 방식: 서블릿 API 사용 (WebFlux와 공존)
}