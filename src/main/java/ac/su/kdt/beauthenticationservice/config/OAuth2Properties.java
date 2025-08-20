package ac.su.kdt.beauthenticationservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuth2Properties {
    
    /**
     * JWT 설정
     */
    private Jwt jwt = new Jwt();
    
    /**
     * OAuth 2.0 서버 설정
     */
    private String issuer;
    
    /**
     * Authorization Code 만료시간 (초)
     */
    private long authorizationCodeExpiration = 600; // 10분
    
    /**
     * 소셜 로그인 설정
     */
    private Social social = new Social();
    
    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpiration = 3600; // 1시간
        private long refreshTokenExpiration = 2592000; // 30일
    }
    
    @Data
    public static class Social {
        private Google google = new Google();
        private Kakao kakao = new Kakao();
    }
    
    @Data
    public static class Google {
        private String clientId;
        private String clientSecret;
        private String redirectUri = "/oauth/social/google/callback";
        private String authUrl = "https://accounts.google.com/o/oauth2/auth";
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";
        private String scope = "openid email profile";
    }
    
    @Data
    public static class Kakao {
        private String clientId;
        private String clientSecret;
        private String redirectUri = "/oauth/social/kakao/callback";
        private String authUrl = "https://kauth.kakao.com/oauth/authorize";
        private String tokenUrl = "https://kauth.kakao.com/oauth/token";
        private String userInfoUrl = "https://kapi.kakao.com/v2/user/me";
        private String scope = "profile_nickname,profile_image,account_email";
    }
}