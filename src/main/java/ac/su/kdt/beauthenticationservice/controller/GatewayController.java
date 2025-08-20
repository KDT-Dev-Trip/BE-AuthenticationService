package ac.su.kdt.beauthenticationservice.controller;

import ac.su.kdt.beauthenticationservice.jwt.JwtService;
import ac.su.kdt.beauthenticationservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@Slf4j
@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final RestTemplate restTemplate;
    private final JwtService jwtService;

    @RequestMapping(value = "/payment/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<Object> proxyToPaymentService(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxyToService(request, body, "payment", 8081);
    }

    @RequestMapping(value = "/user/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<Object> proxyToUserService(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxyToService(request, body, "user", 8082);
    }

    @RequestMapping(value = "/mission/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<Object> proxyToMissionService(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxyToService(request, body, "mission", 8083);
    }

    @RequestMapping(value = "/ai/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<Object> proxyToAiService(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxyToService(request, body, "ai", 8084);
    }

    @RequestMapping(value = "/monitoring/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<Object> proxyToMonitoringService(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxyToService(request, body, "monitoring", 8085);
    }

    private ResponseEntity<Object> proxyToService(HttpServletRequest request, Object body, String serviceName, int port) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) {
            log.warn("Unauthorized gateway request to {} service", serviceName);
            return ResponseEntity.status(401).body("Authentication required");
        }

        JwtUserDetails userDetails = (JwtUserDetails) auth.getPrincipal();
        String userId = userDetails.getUserId();
        String email = userDetails.getEmail();

        // Extract the target path by removing /gateway/{service} prefix
        String requestPath = request.getRequestURI();
        String targetPath = requestPath.replaceFirst("/gateway/" + serviceName, "");
        if (targetPath.isEmpty()) {
            targetPath = "/";
        }

        // Build target URL
        String targetUrl = "http://localhost:" + port + targetPath;
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        // Create headers with authentication info
        HttpHeaders headers = new HttpHeaders();
        
        // Copy original headers (except Authorization to avoid conflicts)
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!"authorization".equalsIgnoreCase(headerName)) {
                    String headerValue = request.getHeader(headerName);
                    headers.add(headerName, headerValue);
                }
            }
        }

        // Add gateway authentication headers
        headers.add("X-User-Id", userId);
        headers.add("X-User-Email", email);
        headers.add("X-Gateway-Auth", "true");
        headers.add("X-Service-Route", serviceName);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Proxying {} {} to {} service (port {}) with user: {}", 
                     request.getMethod(), requestPath, serviceName, port, email);
            
            ResponseEntity<Object> response = restTemplate.exchange(
                targetUrl,
                HttpMethod.valueOf(request.getMethod()),
                entity,
                Object.class
            );

            log.debug("Proxy response from {} service: {} for user: {}", 
                     serviceName, response.getStatusCode(), email);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error proxying request to {} service: {}", serviceName, e.getMessage());
            return ResponseEntity.status(502).body("Gateway error: " + e.getMessage());
        }
    }
}