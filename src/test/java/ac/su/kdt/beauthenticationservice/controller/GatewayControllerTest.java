package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.security.JwtUserDetails;
import ac.su.kdt.beauthenticationservice.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebMvcTest(controllers = GatewayController.class)
@Import({SecurityConfig.class})
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private UsernamePasswordAuthenticationToken createMockAuthentication() {
        JwtUserDetails userDetails = new JwtUserDetails(
            "ef4b8906-2ea8-4f10-b1e7-fa63dd242475",
            "test@example.com",
            "mock-jwt-token"
        );
        return new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("인증된 사용자가 GET 요청을 PaymentService로 프록시할 수 있다")
    void should_ProxyGetRequest_When_UserIsAuthenticated() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "healthy");
        mockResponse.put("service", "payment");

        when(restTemplate.exchange(
            eq("http://localhost:8081/api/v1/health"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    @DisplayName("인증된 사용자가 POST 요청을 PaymentService로 프록시할 수 있다")
    void should_ProxyPostRequest_When_UserIsAuthenticated() throws Exception {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("planId", "basic");
        requestBody.put("userId", "ef4b8906-2ea8-4f10-b1e7-fa63dd242475");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("subscriptionId", "sub_123");

        when(restTemplate.exchange(
            eq("http://localhost:8081/api/v1/subscriptions"),
            eq(HttpMethod.POST),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.CREATED));

        // When & Then
        mockMvc.perform(post("/gateway/payment/api/v1/subscriptions")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(requestBody))
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subscriptionId").value("sub_123"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 Gateway 요청이 거부된다")
    void should_RejectRequest_When_UserIsNotAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("잘못된 인증 정보로 요청하면 거부된다")
    void should_RejectRequest_When_AuthenticationIsInvalid() throws Exception {
        // Given - 익명 사용자로 인증하지만 Controller에서 JwtUserDetails가 아니므로 401 응답
        UsernamePasswordAuthenticationToken invalidAuth = 
            new UsernamePasswordAuthenticationToken("anonymous", null, List.of());

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .with(authentication(invalidAuth)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Authentication required"));
    }

    @Test
    @DisplayName("PaymentService가 응답하지 않으면 502 에러를 반환한다")
    void should_Return502_When_PaymentServiceIsDown() throws Exception {
        // Given
        when(restTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            any(),
            eq(Object.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("Gateway error")));
    }

    @Test
    @DisplayName("사용자 정보가 헤더에 올바르게 전달된다")
    void should_AddUserHeaders_When_ProxyingRequest() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("userId", "ef4b8906-2ea8-4f10-b1e7-fa63dd242475");

        when(restTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            argThat(entity -> {
                HttpHeaders headers = entity.getHeaders();
                return "ef4b8906-2ea8-4f10-b1e7-fa63dd242475".equals(headers.getFirst("X-User-Id")) &&
                       "test@example.com".equals(headers.getFirst("X-User-Email")) &&
                       "true".equals(headers.getFirst("X-Gateway-Auth"));
            }),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/user/profile")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("경로 매핑이 올바르게 동작한다")
    void should_MapPathCorrectly_When_ProxyingRequest() throws Exception {
        // Given
        when(restTemplate.exchange(
            eq("http://localhost:8081/api/v1/tickets/users/123"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(Map.of("tickets", List.of()), HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/tickets/users/123")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("쿼리 파라미터가 올바르게 전달된다")
    void should_ForwardQueryParameters_When_ProxyingRequest() throws Exception {
        // Given
        when(restTemplate.exchange(
            ArgumentMatchers.contains("http://localhost:8081/api/v1/plans"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(Map.of("plans", List.of()), HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/plans")
                .param("category", "basic")
                .param("active", "true")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User Service로 프록시 요청이 가능하다")
    void should_ProxyToUserService_When_ValidRequest() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("userId", "ef4b8906-2ea8-4f10-b1e7-fa63dd242475");
        mockResponse.put("email", "test@example.com");

        when(restTemplate.exchange(
            eq("http://localhost:8082/api/v1/profile"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/user/api/v1/profile")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ef4b8906-2ea8-4f10-b1e7-fa63dd242475"));
    }

    @Test
    @DisplayName("Mission Service로 프록시 요청이 가능하다")
    void should_ProxyToMissionService_When_ValidRequest() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("missions", List.of("mission1", "mission2"));

        when(restTemplate.exchange(
            eq("http://localhost:8083/api/v1/missions"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/mission/api/v1/missions")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions").isArray());
    }

    @Test
    @DisplayName("AI Service로 프록시 요청이 가능하다")
    void should_ProxyToAiService_When_ValidRequest() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("recommendation", "AI recommendation result");

        when(restTemplate.exchange(
            eq("http://localhost:8084/api/v1/recommend"),
            eq(HttpMethod.POST),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(post("/gateway/ai/api/v1/recommend")
                .contentType("application/json")
                .content("{\"userId\":\"ef4b8906-2ea8-4f10-b1e7-fa63dd242475\"}")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("AI recommendation result"));
    }

    @Test
    @DisplayName("Monitoring Service로 프록시 요청이 가능하다")
    void should_ProxyToMonitoringService_When_ValidRequest() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "all systems operational");
        mockResponse.put("uptime", "99.9%");

        when(restTemplate.exchange(
            eq("http://localhost:8085/api/v1/health"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/monitoring/api/v1/health")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("all systems operational"));
    }
}