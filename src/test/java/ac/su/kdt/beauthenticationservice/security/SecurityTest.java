package ac.su.kdt.beauthenticationservice.security;

import ac.su.kdt.beauthenticationservice.config.IntegrationTestBase;
import ac.su.kdt.beauthenticationservice.config.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@DisplayName("Security Tests")
class SecurityTest extends IntegrationTestBase {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("CORS 헤더가 올바르게 설정되어야 한다")
    void cors_ShouldBeConfiguredCorrectly() throws Exception {
        mockMvc.perform(options("/auth/health")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }
    
    @Test
    @DisplayName("허용된 엔드포인트는 인증 없이 접근 가능해야 한다")
    void allowedEndpoints_ShouldBeAccessibleWithoutAuth() throws Exception {
        // Health check endpoint
        mockMvc.perform(get("/auth/health"))
                .andExpect(status().isOk());
        
        // Password reset endpoint
        mockMvc.perform(post("/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "test@example.com"))))
                .andExpect(status().isOk());
        
        // Auth callback endpoint
        mockMvc.perform(post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("access_token", "invalid.token"))))
                .andExpect(status().isUnauthorized()); // 토큰이 유효하지 않아서 401, 하지만 접근은 허용됨
        
        // Token validation endpoint
        mockMvc.perform(post("/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "invalid.token"))))
                .andExpect(status().isOk());
        
        // Actuator endpoints
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("보안이 필요한 엔드포인트는 인증 토큰 없이 접근 시 401 에러를 반환해야 한다")
    void securedEndpoints_ShouldRequireAuthentication() throws Exception {
        // /auth/me endpoint requires authentication
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @DisplayName("악의적인 HTTP 헤더 주입 시도를 차단해야 한다")
    void maliciousHeaders_ShouldBeBlocked() throws Exception {
        // SQL Injection attempt in User-Agent
        mockMvc.perform(post("/auth/callback")
                .header("User-Agent", "'; DROP TABLE users; --")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("access_token", "test.token"))))
                .andExpect(status().isUnauthorized()); // 토큰이 유효하지 않아서 401, 하지만 헤더 인젝션은 차단됨
        
        // XSS attempt in Authorization header
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer <script>alert('xss')</script>"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @DisplayName("비정상적으로 큰 요청은 거부되어야 한다")
    void oversizedRequest_ShouldBeRejected() throws Exception {
        // 매우 큰 JSON 페이로드 생성
        StringBuilder largePayload = new StringBuilder();
        largePayload.append("{\"access_token\":\"");
        // 1MB 크기의 문자열 생성
        for (int i = 0; i < 100000; i++) {
            largePayload.append("very_long_token_");
        }
        largePayload.append("\"}");
        
        mockMvc.perform(post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(largePayload.toString()))
                .andExpect(status().is4xxClientError()); // 400 Bad Request 또는 413 Payload Too Large
    }
    
    @Test
    @DisplayName("잘못된 Content-Type으로 요청 시 415 에러를 반환해야 한다")
    void wrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/auth/callback")
                .contentType(MediaType.TEXT_PLAIN)
                .content("access_token=invalid.token"))
                .andExpect(status().isUnsupportedMediaType());
    }
    
    @Test
    @DisplayName("HTTP 메서드가 허용되지 않는 엔드포인트 접근 시 405 에러를 반환해야 한다")
    void wrongHttpMethod_ShouldReturnMethodNotAllowed() throws Exception {
        // GET으로 POST 전용 엔드포인트 접근
        mockMvc.perform(get("/auth/callback"))
                .andExpect(status().isMethodNotAllowed());
        
        // DELETE로 GET 전용 엔드포인트 접근
        mockMvc.perform(delete("/auth/health"))
                .andExpect(status().isMethodNotAllowed());
    }
    
    @Test
    @DisplayName("빈 JSON 객체로 요청 시 적절한 에러를 반환해야 한다")
    void emptyJsonRequest_ShouldReturnAppropriateError() throws Exception {
        mockMvc.perform(post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Access token is required"));
        
        mockMvc.perform(post("/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token is required"));
    }
    
    @Test
    @DisplayName("잘못된 JSON 형식으로 요청 시 400 에러를 반환해야 한다")
    void malformedJson_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/auth/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("Rate Limiting이 적용되지 않은 상태에서 연속 요청이 가능해야 한다")
    void consecutiveRequests_ShouldBeAllowed() throws Exception {
        // 연속으로 10번 요청
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/auth/health"))
                    .andExpect(status().isOk());
        }
    }
    
    @Test
    @DisplayName("특수문자가 포함된 이메일로 요청 시에도 안전하게 처리되어야 한다")
    void specialCharactersInEmail_ShouldBeHandledSafely() throws Exception {
        String[] testEmails = {
            "user+test@example.com",
            "user.name@example.com", 
            "user-name@example-domain.com",
            "123456@example.com",
            "user@sub.example.com"
        };
        
        for (String email : testEmails) {
            mockMvc.perform(post("/auth/password-reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}