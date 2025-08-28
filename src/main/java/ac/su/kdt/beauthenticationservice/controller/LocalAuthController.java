package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.model.dto.LoginRequest;
import ac.su.kdt.beauthenticationservice.model.dto.SignupRequest;
import ac.su.kdt.beauthenticationservice.model.dto.PasswordResetRequest;
import ac.su.kdt.beauthenticationservice.service.LocalAuthService;
import ac.su.kdt.beauthenticationservice.service.LoginAttemptService;
import ac.su.kdt.beauthenticationservice.service.RedisLoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로컬 인증 컨트롤러 (Legacy)
 * OAuth 2.0 표준 인증을 위해 AuthController로 이관됨
 */
@Slf4j
@RestController
@RequestMapping("/local-auth")
@RequiredArgsConstructor
@Tag(name = "Local Authentication (Legacy)", description = "로컬 인증 API - OAuth 2.0으로 이관됨")
public class LocalAuthController {
    // 하이브리드 방식: 서블릿 API 사용 (WebFlux와 공존)
}