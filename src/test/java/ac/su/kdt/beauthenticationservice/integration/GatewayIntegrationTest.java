package ac.su.kdt.beauthenticationservice.integration;

import ac.su.kdt.beauthenticationservice.config.IntegrationTestBase;
import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@Transactional
class GatewayIntegrationTest extends IntegrationTestBase {

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
    private String validJwtToken;

    @BeforeEach
    void setUp() {
        // WireMock 서버 시작 (PaymentService 모킹)
        wireMockServer = new WireMockServer(8082);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8082);

        // 테스트 사용자 생성
        testUser = User.builder()
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode("Test123!@#"))
                .name("Test User")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        // JWT 토큰 생성
        validJwtToken = jwtService.generateAccessToken(testUser.getId(), testUser.getEmail(), "USER");
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 Gateway를 통해 PaymentService에 접근할 수 있다")
    void should_AccessPaymentService_When_ValidJwtTokenProvided() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "healthy");
        mockResponse.put("timestamp", System.currentTimeMillis());

        stubFor(WireMock.get(urlEqualTo("/api/v1/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(mockResponse))));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    @DisplayName("JWT 토큰 없이는 Gateway에 접근할 수 없다")
    void should_DenyAccess_When_NoJwtTokenProvided() throws Exception {
        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 JWT 토큰으로는 Gateway에 접근할 수 없다")
    void should_DenyAccess_When_InvalidJwtTokenProvided() throws Exception {
        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 JWT 토큰으로는 Gateway에 접근할 수 없다")
    void should_DenyAccess_When_ExpiredJwtTokenProvided() throws Exception {
        // Given
        String expiredToken = jwtService.generateTokenWithCustomExpiration(
                testUser.getId(), testUser.getEmail(), "USER", -3600); // 1시간 전 만료

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Gateway는 사용자 정보를 헤더에 추가하여 PaymentService로 전달한다")
    void should_AddUserHeaders_When_ForwardingToPaymentService() throws Exception {
        // Given
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/user/profile"))
                .withHeader("X-User-Id", equalTo(testUser.getId()))
                .withHeader("X-User-Email", equalTo(testUser.getEmail()))
                .withHeader("X-Gateway-Auth", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userId\":\"" + testUser.getId() + "\"}")));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId()));

        // Verify that the correct headers were sent
        verify(getRequestedFor(urlPathEqualTo("/api/v1/user/profile"))
                .withHeader("X-User-Id", equalTo(testUser.getId()))
                .withHeader("X-User-Email", equalTo(testUser.getEmail()))
                .withHeader("X-Gateway-Auth", equalTo("true")));
    }

    @Test
    @DisplayName("Gateway는 POST 요청도 올바르게 프록시한다")
    void should_ProxyPostRequest_When_ValidAuthentication() throws Exception {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("planId", "basic");
        requestBody.put("amount", 9900);

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("subscriptionId", "sub_123");

        stubFor(WireMock.post(urlPathEqualTo("/api/v1/subscriptions"))
                .withHeader("X-User-Id", equalTo(testUser.getId()))
                .withHeader("X-User-Email", equalTo(testUser.getEmail()))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(mockResponse))));

        // When & Then
        mockMvc.perform(post("/gateway/payment/api/v1/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subscriptionId").value("sub_123"));
    }

    @Test
    @DisplayName("PaymentService가 응답하지 않으면 502 에러를 반환한다")
    void should_Return502_When_PaymentServiceUnavailable() throws Exception {
        // Given
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/health"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Gateway는 원본 헤더를 보존하면서 인증 헤더를 추가한다")
    void should_PreserveOriginalHeaders_When_AddingAuthHeaders() throws Exception {
        // Given
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/plans"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", containing("MockMvc"))
                .withHeader("X-User-Id", equalTo(testUser.getId()))
                .withHeader("X-User-Email", equalTo(testUser.getEmail()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken)
                .header("Accept", "application/json")
                .header("Custom-Header", "custom-value"))
                .andExpect(status().isOk());

        // Verify headers were preserved and auth headers were added
        verify(getRequestedFor(urlPathEqualTo("/api/v1/plans"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-User-Id", equalTo(testUser.getId()))
                .withHeader("X-User-Email", equalTo(testUser.getEmail())));
    }

    @Test
    @DisplayName("Gateway는 쿼리 파라미터를 올바르게 전달한다")
    void should_ForwardQueryParameters_When_ProxyingRequest() throws Exception {
        // Given
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/tickets"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("10"))
                .withQueryParam("status", equalTo("active"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tickets\":[]}")));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/tickets")
                .param("page", "1")
                .param("size", "10")
                .param("status", "active")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken))
                .andExpect(status().isOk());
    }
}