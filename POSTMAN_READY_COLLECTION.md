# 🚀 Postman Ready Collection - 바로 붙여넣기용

## 🔐 **Authentication APIs** - 기본 인증

### 1. 서비스 테스트
```
GET http://localhost:8080/auth/test
```

### 2. 회원가입
```
POST http://localhost:8080/auth/signup
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

### 3. 로그인 (JWT 발급)
```
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123"
}
```

### 4. 토큰 갱신
```
POST http://localhost:8080/auth/refresh
Content-Type: application/json

{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 5. JWT 토큰 검증
```
POST http://localhost:8080/auth/validate
Content-Type: application/json

{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 6. 현재 사용자 정보 조회
```
GET http://localhost:8080/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 7. 비밀번호 재설정 요청
```
POST http://localhost:8080/auth/password-reset
Content-Type: application/json

{
  "email": "test@example.com"
}
```

### 8. 인증 서비스 헬스체크
```
GET http://localhost:8080/auth/health
```

---

## 🏠 **Local Authentication APIs**

### 9. 로컬 회원가입
```
POST http://localhost:8080/auth/local/signup
Content-Type: application/json

{
  "email": "local@example.com",
  "password": "password123",
  "name": "로컬사용자",
  "phone": "010-1234-5678"
}
```

### 10. 로컬 로그인
```
POST http://localhost:8080/auth/local/login
Content-Type: application/json

{
  "email": "local@example.com",
  "password": "password123"
}
```

### 11. 로컬 비밀번호 재설정
```
POST http://localhost:8080/auth/local/password-reset
Content-Type: application/json

{
  "email": "local@example.com",
  "newPassword": "newpassword123",
  "resetToken": "reset-token-string"
}
```

---

## 🌐 **OAuth 2.0 APIs**

### 12. Authorization Code 요청
```
GET http://localhost:8080/oauth/authorize?response_type=code&client_id=your-client-id&redirect_uri=http://localhost:3000/callback&scope=read write&state=random-state
```

### 13. Access Token 교환
```
POST http://localhost:8080/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=authorization-code-here&client_id=your-client-id&client_secret=your-client-secret&redirect_uri=http://localhost:3000/callback
```

### 14. Token Introspection
```
POST http://localhost:8080/oauth/introspect
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

token=access-token-here
```

### 15. Token 폐기
```
POST http://localhost:8080/oauth/revoke
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

token=access-token-or-refresh-token&token_type_hint=access_token
```

---

## 👨‍💼 **Admin APIs**

### 16. 로그인 시도 통계 조회
```
GET http://localhost:8080/admin/login-attempts/stats
Authorization: Bearer admin-jwt-token
```

### 17. 계정 잠금 정보 조회
```
GET http://localhost:8080/admin/account/test@example.com/lock-info
Authorization: Bearer admin-jwt-token
```

### 18. 계정 잠금 해제
```
POST http://localhost:8080/admin/account/test@example.com/unlock
Authorization: Bearer admin-jwt-token
```

### 19. 계정 강제 잠금
```
POST http://localhost:8080/admin/account/test@example.com/lock
Authorization: Bearer admin-jwt-token
```

### 20. 로그인 시도 기록 초기화
```
DELETE http://localhost:8080/admin/account/test@example.com/login-attempts
Authorization: Bearer admin-jwt-token
```

---

## 📱 **Social Login APIs**

### 21. Google 소셜 로그인
```
GET http://localhost:8080/auth/social/google
```

### 22. Google 콜백 처리
```
GET http://localhost:8080/auth/social/google/callback?code=google-auth-code&state=random-state
```

### 23. 카카오 소셜 로그인
```
GET http://localhost:8080/auth/social/kakao
```

### 24. 카카오 콜백 처리
```
GET http://localhost:8080/auth/social/kakao/callback?code=kakao-auth-code&state=random-state
```

---

## 🔒 **SSO APIs**

### 25. SSO 초기화
```
GET http://localhost:8080/sso/init?service=target-service
```

### 26. SSO 토큰 검증
```
POST http://localhost:8080/sso/validate
Content-Type: application/json

{
  "ssoToken": "sso-token-string",
  "service": "target-service"
}
```

### 27. SSO 로그아웃
```
POST http://localhost:8080/sso/logout
Authorization: Bearer jwt-token
```

---

## 🏠 **Home & Protected APIs**

### 28. 홈페이지
```
GET http://localhost:8080/
```

### 29. 보호된 API
```
GET http://localhost:8080/api/protected
Authorization: Bearer jwt-token
```

### 30. 사용자 대시보드
```
GET http://localhost:8080/api/dashboard
Authorization: Bearer jwt-token
```

---

## 💰 **PaymentService APIs** - 현재 실행중 (포트 8082)

### 31. PaymentService 헬스체크
```
GET http://localhost:8082/api/v1/health
```

### 32. PaymentService 정보
```
GET http://localhost:8082/api/v1/info
```

### 33. 사용자 티켓 조회 (헤더 시뮬레이션)
```
GET http://localhost:8082/api/v1/tickets/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475
X-User-Id: ef4b8906-2ea8-4f10-b1e7-fa63dd242475
X-User-Email: test@example.com
```

### 34. 사용자 구독 조회 (헤더 시뮬레이션)
```
GET http://localhost:8082/api/v1/subscriptions/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475
X-User-Id: ef4b8906-2ea8-4f10-b1e7-fa63dd242475
X-User-Email: test@example.com
```

### 35. 구독 플랜 목록 조회
```
GET http://localhost:8082/api/v1/subscription-plans
```

### 36. 티켓 사용
```
POST http://localhost:8082/api/v1/tickets/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475/use?amount=5&reason=Test usage
X-User-Id: ef4b8906-2ea8-4f10-b1e7-fa63dd242475
X-User-Email: test@example.com
```

### 37. 구독 생성
```
POST http://localhost:8082/api/v1/subscriptions
Content-Type: application/json

{
  "userId": 507226294,
  "planId": 1,
  "paymentMethodId": "pm_test_card"
}
```

---

## 🌐 **Gateway APIs** (Spring Cloud Gateway - 포트 8080)

### 38. Gateway → PaymentService 헬스체크
```
GET http://localhost:8080/gateway/payment/api/v1/health
Authorization: Bearer jwt-token-here
```

### 39. Gateway → 사용자 티켓 조회
```
GET http://localhost:8080/gateway/payment/api/v1/tickets/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475
Authorization: Bearer jwt-token-here
```

### 40. Gateway → 사용자 구독 조회
```
GET http://localhost:8080/gateway/payment/api/v1/subscriptions/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475
Authorization: Bearer jwt-token-here
```

---

## 🟠 **Eureka Server APIs** (포트 8761)

### 41. Eureka Dashboard
```
GET http://localhost:8761
```

### 42. 등록된 서비스 목록
```
GET http://localhost:8761/eureka/apps
```

### 43. PaymentService 정보
```
GET http://localhost:8761/eureka/apps/PAYMENT-SERVICE
```

---

## 🎯 **추천 테스트 순서**

### **현재 바로 테스트 가능한 API:**
```
1. GET http://localhost:8082/api/v1/health
2. GET http://localhost:8082/api/v1/info  
3. GET http://localhost:8082/api/v1/subscription-plans
4. GET http://localhost:8082/api/v1/tickets/users/ef4b8906-2ea8-4f10-b1e7-fa63dd242475
   (X-User-Id, X-User-Email 헤더 추가)
```

### **AuthenticationService 재시작 후 테스트:**
```
1. GET http://localhost:8080/auth/test
2. POST http://localhost:8080/auth/signup (회원가입)
3. POST http://localhost:8080/auth/login (로그인 & JWT 획득)
4. GET http://localhost:8080/auth/me (사용자 정보)
```

---

## 🔧 **Postman 사용 팁**

### **Environment Variables 설정:**
```
BASE_URL: http://localhost:8080
PAYMENT_URL: http://localhost:8082
JWT_TOKEN: (로그인 후 받은 토큰)
USER_ID: ef4b8906-2ea8-4f10-b1e7-fa63dd242475
```

### **Pre-request Script (자동 토큰 설정):**
```javascript
// 로그인 API 호출 후 토큰 자동 저장
if (pm.response.json().accessToken) {
    pm.environment.set("JWT_TOKEN", pm.response.json().accessToken);
}
```

---

이제 **모든 API를 복사-붙여넣기로 바로 Postman에서 테스트**할 수 있습니다! 🚀