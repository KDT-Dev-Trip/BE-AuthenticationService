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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = GatewayController.class)
@Import({SecurityConfig.class})
class GatewayUnitSecurityTest {

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
    @DisplayName("Authorization 헤더가 없으면 403 Forbidden을 반환한다")
    void should_Return403_When_NoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/gateway/payment/api/v1/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("잘못된 인증 정보로 요청하면 401을 반환한다")
    void should_Return401_When_InvalidAuthenticationPrincipal() throws Exception {
        // Given - String principal (not JwtUserDetails)
        UsernamePasswordAuthenticationToken invalidAuth = 
            new UsernamePasswordAuthenticationToken("anonymous", null, List.of());

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .with(authentication(invalidAuth)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Authentication required"));
    }

    @Test
    @DisplayName("유효한 인증으로 Gateway 접근시 프록시 요청이 수행된다")
    void should_ProxyRequest_When_ValidAuthentication() throws Exception {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "healthy");
        
        when(restTemplate.exchange(
            ArgumentMatchers.contains("http://localhost:8081/api/v1/health"),
            eq(HttpMethod.GET),
            any(),
            eq(Object.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When & Then
        mockMvc.perform(get("/gateway/payment/api/v1/health")
                .with(authentication(createMockAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test
    @DisplayName("사용자 정보가 헤더에 올바르게 추가된다")
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
    @DisplayName("PaymentService 연결 실패시 502 에러를 반환한다")
    void should_Return502_When_PaymentServiceConnectionFails() throws Exception {
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
}