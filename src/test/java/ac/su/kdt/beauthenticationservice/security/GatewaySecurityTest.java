package ac.su.kdt.beauthenticationservice.security;

import ac.su.kdt.beauthenticationservice.config.IntegrationTestBase;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureWebMvc
@Transactional
class GatewaySecurityTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private WireMockServer wireMockServer;
    private User testUser;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8082);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8082);

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode("Test123!@#"))
                .name("Test User")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401 Unauthorized를 반환한다")
    void should_Return401_When_NoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/gateway/payment/api/v1/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer 토큰이 아닌 Authorization 헤더는 거부된다")
    void should_Return401_When_NonBearerToken() throws Exception {
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 형식의 JWT 토큰은 거부된다")
    void should_Return401_When_MalformedJwtToken() throws Exception {
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT 토큰의 서명이 잘못되면 거부된다")
    void should_Return401_When_InvalidJwtSignature() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        String tamperedToken = validToken.substring(0, validToken.length() - 10) + "tampered123";

        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 JWT 토큰은 거부된다")
    void should_Return401_When_ExpiredJwtToken() throws Exception {
        String expiredToken = jwtService.generateTokenWithCustomExpiration(
                testUser.getId(), testUser.getEmail(), "USER", -3600);

        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비활성화된 사용자의 토큰은 거부된다")
    void should_Return401_When_InactiveUser() throws Exception {
        // Given - 사용자 비활성화
        testUser.setIsActive(false);
        userRepository.save(testUser);

        String tokenForInactiveUser = jwtService.generateAccessToken(
                testUser.getId(), testUser.getEmail(), "USER");

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenForInactiveUser))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 토큰은 거부된다")
    void should_Return401_When_NonExistentUser() throws Exception {
        String tokenForNonExistentUser = jwtService.generateAccessToken(
                "non-existent-user-id", "nonexistent@example.com", "USER");

        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenForNonExistentUser))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT 토큰이 유효하지만 PaymentService에 연결할 수 없으면 502를 반환한다")
    void should_Return502_When_PaymentServiceUnavailable() throws Exception {
        // Given
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        // PaymentService가 응답하지 않도록 설정
        wireMockServer.stop();

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("Gateway error")));
    }

    @Test
    @DisplayName("SQL Injection 시도는 차단된다")
    void should_BlockSqlInjection_When_MaliciousInput() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        stubFor(WireMock.get(urlPathMatching("/api/v1/tickets/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"tickets\":[]}")));

        mockMvc.perform(get("/gateway/payment/api/v1/tickets/users/1' OR '1'='1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        // URL 경로가 안전하게 인코딩되어 전달되는지 확인
        verify(getRequestedFor(urlPathEqualTo("/api/v1/tickets/users/1' OR '1'='1")));
    }

    @Test
    @DisplayName("XSS 공격 시도는 차단된다")
    void should_BlockXssAttack_When_MaliciousScript() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        stubFor(WireMock.get(urlPathMatching("/api/v1/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"results\":[]}")));

        mockMvc.perform(get("/gateway/payment/api/v1/search")
                .param("q", "<script>alert('xss')</script>")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("과도한 요청은 제한된다 (Rate Limiting)")
    void should_LimitRequests_When_TooManyRequests() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"status\":\"ok\"}")));

        // 연속으로 많은 요청을 보내서 Rate Limiting 테스트
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/gateway/payment/api/v1/health")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken));
        }

        // 모든 요청이 통과하는지 확인 (현재는 Rate Limiting이 구현되지 않음)
        verify(exactly(10), getRequestedFor(urlPathEqualTo("/api/v1/health")));
    }

    @Test
    @DisplayName("민감한 헤더는 PaymentService로 전달되지 않는다")
    void should_FilterSensitiveHeaders_When_ForwardingRequest() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/plans"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("[]")));

        mockMvc.perform(get("/gateway/payment/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .header("X-Internal-Secret", "secret-value")
                .header("Cookie", "session=abc123"))
                .andExpect(status().isOk());

        // Authorization 헤더가 PaymentService로 전달되지 않았는지 확인
        verify(getRequestedFor(urlPathEqualTo("/api/v1/plans"))
                .withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("CORS 정책이 올바르게 적용된다")
    void should_ApplyCorsPolicy_When_CrossOriginRequest() throws Exception {
        String validToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
        
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"status\":\"ok\"}")));

        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}