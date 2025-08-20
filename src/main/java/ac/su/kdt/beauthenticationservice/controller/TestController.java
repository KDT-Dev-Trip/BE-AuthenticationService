package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {
    
    private final JwtService jwtService;
    
    @GetMapping("/jwt-token")
    public Map<String, String> generateTestJwtToken() {
        String testUserId = UUID.randomUUID().toString();
        String testEmail = "gateway-test@example.com";
        String token = jwtService.generateAccessToken(testUserId, testEmail, "USER");
        
        return Map.of(
            "access_token", token,
            "user_id", testUserId,
            "email", testEmail,
            "message", "Test JWT token generated for API Gateway testing"
        );
    }
}