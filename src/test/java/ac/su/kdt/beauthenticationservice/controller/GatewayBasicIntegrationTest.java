package ac.su.kdt.beauthenticationservice.controller;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.redis.host=localhost",
    "spring.redis.port=6379",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
@Transactional
class GatewayBasicIntegrationTest {

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
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("잘못된 JWT 토큰으로는 Gateway에 접근할 수 없다")
    void should_DenyAccess_When_InvalidJwtTokenProvided() throws Exception {
        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isForbidden());
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
    @DisplayName("PaymentService가 응답하지 않으면 502 에러를 반환한다")
    void should_Return502_When_PaymentServiceUnavailable() throws Exception {
        // Given - WireMock 서버 정지하여 연결 불가 상황 시뮬레이션
        wireMockServer.stop();

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwtToken))
                .andExpect(status().isBadGateway());
    }
}