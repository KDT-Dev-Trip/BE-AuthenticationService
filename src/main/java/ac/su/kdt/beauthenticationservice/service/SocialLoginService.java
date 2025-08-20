package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.config.OAuth2Properties;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 로그인 통합 서비스
 * Google, Kakao 등의 소셜 로그인을 처리합니다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SocialLoginService {
    
    private final OAuth2Properties oauth2Properties;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 구글 소셜 로그인 처리
     */
    public SocialLoginResult processGoogleLogin(String authorizationCode) {
        try {
            // 1. Google에서 Access Token 교환
            String accessToken = exchangeGoogleCodeForToken(authorizationCode);
            
            // 2. Google에서 사용자 정보 조회
            GoogleUserInfo googleUserInfo = getGoogleUserInfo(accessToken);
            
            // 3. 사용자 계정 생성 또는 조회
            User user = findOrCreateUser(
                googleUserInfo.getEmail(),
                googleUserInfo.getName(),
                googleUserInfo.getPicture(),
                "google",
                googleUserInfo.getId()
            );
            
            log.info("Google login successful for user: {}", user.getEmail());
            
            return SocialLoginResult.builder()
                    .success(true)
                    .user(user)
                    .socialProvider("google")
                    .socialUserId(googleUserInfo.getId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Google login failed: {}", e.getMessage(), e);
            return SocialLoginResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * 카카오 소셜 로그인 처리
     */
    public SocialLoginResult processKakaoLogin(String authorizationCode) {
        try {
            // 1. Kakao에서 Access Token 교환
            String accessToken = exchangeKakaoCodeForToken(authorizationCode);
            
            // 2. Kakao에서 사용자 정보 조회
            KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(accessToken);
            
            // 3. 사용자 계정 생성 또는 조회
            User user = findOrCreateUser(
                kakaoUserInfo.getEmail(),
                kakaoUserInfo.getNickname(),
                kakaoUserInfo.getProfileImage(),
                "kakao",
                kakaoUserInfo.getId().toString()
            );
            
            log.info("Kakao login successful for user: {}", user.getEmail());
            
            return SocialLoginResult.builder()
                    .success(true)
                    .user(user)
                    .socialProvider("kakao")
                    .socialUserId(kakaoUserInfo.getId().toString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Kakao login failed: {}", e.getMessage(), e);
            return SocialLoginResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    // === Google API 호출 메서드들 ===
    
    /**
     * Google Authorization Code를 Access Token으로 교환
     */
    private String exchangeGoogleCodeForToken(String authorizationCode) throws Exception {
        OAuth2Properties.Google google = oauth2Properties.getSocial().getGoogle();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", google.getClientId());
        params.add("client_secret", google.getClientSecret());
        params.add("redirect_uri", google.getRedirectUri());
        params.add("grant_type", "authorization_code");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            google.getTokenUrl(), request, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to exchange Google code for token: " + response.getBody());
        }
        
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("access_token").asText();
    }
    
    /**
     * Google Access Token으로 사용자 정보 조회
     */
    private GoogleUserInfo getGoogleUserInfo(String accessToken) throws Exception {
        OAuth2Properties.Google google = oauth2Properties.getSocial().getGoogle();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            google.getUserInfoUrl(), HttpMethod.GET, entity, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to get Google user info: " + response.getBody());
        }
        
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        
        return GoogleUserInfo.builder()
                .id(jsonNode.get("id").asText())
                .email(jsonNode.get("email").asText())
                .name(jsonNode.get("name").asText())
                .picture(jsonNode.has("picture") ? jsonNode.get("picture").asText() : null)
                .emailVerified(jsonNode.has("verified_email") ? jsonNode.get("verified_email").asBoolean() : false)
                .build();
    }
    
    // === Kakao API 호출 메서드들 ===
    
    /**
     * Kakao Authorization Code를 Access Token으로 교환
     */
    private String exchangeKakaoCodeForToken(String authorizationCode) throws Exception {
        OAuth2Properties.Kakao kakao = oauth2Properties.getSocial().getKakao();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakao.getClientId());
        params.add("client_secret", kakao.getClientSecret());
        params.add("redirect_uri", kakao.getRedirectUri());
        params.add("code", authorizationCode);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            kakao.getTokenUrl(), request, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to exchange Kakao code for token: " + response.getBody());
        }
        
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("access_token").asText();
    }
    
    /**
     * Kakao Access Token으로 사용자 정보 조회
     */
    private KakaoUserInfo getKakaoUserInfo(String accessToken) throws Exception {
        OAuth2Properties.Kakao kakao = oauth2Properties.getSocial().getKakao();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            kakao.getUserInfoUrl(), HttpMethod.GET, entity, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to get Kakao user info: " + response.getBody());
        }
        
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        JsonNode kakaoAccount = jsonNode.get("kakao_account");
        JsonNode profile = kakaoAccount.has("profile") ? kakaoAccount.get("profile") : null;
        
        return KakaoUserInfo.builder()
                .id(jsonNode.get("id").asLong())
                .email(kakaoAccount.has("email") ? kakaoAccount.get("email").asText() : null)
                .nickname(profile != null && profile.has("nickname") ? profile.get("nickname").asText() : "User")
                .profileImage(profile != null && profile.has("profile_image_url") ? profile.get("profile_image_url").asText() : null)
                .emailVerified(kakaoAccount.has("is_email_verified") ? kakaoAccount.get("is_email_verified").asBoolean() : false)
                .build();
    }
    
    // === 공통 메서드들 ===
    
    /**
     * 사용자 계정 찾기 또는 생성
     */
    private User findOrCreateUser(String email, String name, String pictureUrl, String socialProvider, String socialUserId) {
        // 1. 소셜 ID로 기존 사용자 찾기
        Optional<User> existingUser = userRepository.findBySocialProviderAndSocialUserId(socialProvider, socialUserId);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // 사용자 정보 업데이트 (이름, 프로필 사진 등)
            user.setName(name);
            user.setPictureUrl(pictureUrl);
            user.setEmailVerified(true); // 소셜 로그인은 이메일 인증됨으로 간주
            return userRepository.save(user);
        }
        
        // 2. 이메일로 기존 사용자 찾기
        if (email != null) {
            Optional<User> emailUser = userRepository.findByEmail(email);
            if (emailUser.isPresent()) {
                User user = emailUser.get();
                // 소셜 계정 연결
                user.setSocialProvider(socialProvider);
                user.setSocialUserId(socialUserId);
                user.setName(name);
                user.setPictureUrl(pictureUrl);
                user.setEmailVerified(true);
                return userRepository.save(user);
            }
        }
        
        // 3. 새 사용자 생성
        User newUser = User.builder()
                .id(UUID.randomUUID().toString())
                .email(email != null ? email : socialProvider + "_" + socialUserId + "@social.local")
                .name(name)
                .pictureUrl(pictureUrl)
                .socialProvider(socialProvider)
                .socialUserId(socialUserId)
                .emailVerified(true)
                .role(User.UserRole.USER)
                .isActive(true)
                .currentTickets(100) // 신규 사용자 초기 티켓
                .build();
        
        return userRepository.save(newUser);
    }
    
    // === DTO 클래스들 ===
    
    @lombok.Data
    @lombok.Builder
    public static class SocialLoginResult {
        private boolean success;
        private User user;
        private String socialProvider;
        private String socialUserId;
        private String errorMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class GoogleUserInfo {
        private String id;
        private String email;
        private String name;
        private String picture;
        private boolean emailVerified;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class KakaoUserInfo {
        private Long id;
        private String email;
        private String nickname;
        private String profileImage;
        private boolean emailVerified;
    }
}