# Auth Service API 테스트 가이드

## 🔧 테스트 환경 설정

### Base URL
```
http://localhost:8081
```

### 기본 헤더 설정
```json
{
  "Content-Type": "application/json",
  "Accept": "application/json"
}
```

### JWT 토큰이 필요한 API 헤더
```json
{
  "Authorization": "Bearer {JWT_TOKEN}",
  "Content-Type": "application/json"
}
```

---

## 📋 API 엔드포인트 테스트 목록

### 1. 홈 및 헬스체크 API

#### 1.1 홈페이지
```http
GET /
```

#### 1.2 헬스체크
```http
GET /health
```

#### 1.3 앱 헬스체크
```http
GET /app-health
```

#### 1.4 메트릭스
```http
GET /metrics
```

#### 1.5 표준 API 헬스체크
```http
GET /api/health
```

---

### 2. 인증(Auth) API

#### 2.1 회원가입
```http
POST /auth/signup
```
**요청 본문:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "name": "홍길동"
}
```
**응답 예시:**
```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "emailVerified": false
  }
}
```

#### 2.2 로그인
```http
POST /auth/login
```
**요청 본문:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```
**응답 예시:**
```json
{
  "success": true,
  "message": "로그인이 완료되었습니다",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "tickets": 10,
    "emailVerified": false
  }
}
```

#### 2.3 토큰 갱신
```http
POST /auth/refresh
```
**요청 본문:**
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```
**응답 예시:**
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "새 Access Token이 발급되었습니다"
}
```

#### 2.4 비밀번호 재설정
```http
POST /auth/password-reset
```
**요청 본문:**
```json
{
  "email": "user@example.com"
}
```
**응답 예시:**
```json
{
  "success": true,
  "message": "If the email exists, a password reset link has been sent"
}
```

#### 2.5 토큰 검증
```http
POST /auth/validate
```
**요청 본문:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```
**응답 예시:**
```json
{
  "valid": true,
  "userId": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

#### 2.6 내 정보 조회
```http
GET /auth/me
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**응답 예시:**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER",
  "tickets": 10,
  "emailVerified": false,
  "pictureUrl": null,
  "isActive": true,
  "socialProvider": null
}
```

#### 2.7 로그아웃
```http
POST /auth/logout
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**요청 본문 (선택사항):**
```json
{
  "reason": "USER_LOGOUT"
}
```
**응답 예시:**
```json
{
  "success": true,
  "message": "로그아웃이 완료되었습니다"
}
```

#### 2.8 사용자 동기화
```http
POST /auth/sync-users
```
**응답 예시:**
```json
{
  "success": true,
  "message": "사용자 동기화가 완료되었습니다",
  "syncedCount": 42
}
```

#### 2.9 Auth 테스트
```http
GET /auth/test
```
**응답 예시:**
```json
{
  "message": "OAuth 2.0 인증 서비스가 정상 작동합니다",
  "timestamp": 1693392000000,
  "endpoints": {
    "POST /auth/signup": "이메일/비밀번호 회원가입",
    "POST /auth/login": "이메일/비밀번호 로그인",
    "POST /auth/validate": "JWT 토큰 검증",
    "POST /auth/password-reset": "비밀번호 재설정",
    "GET /oauth/authorize": "OAuth 2.0 Authorization Code 발급",
    "POST /oauth/token": "OAuth 2.0 Access Token 교환"
  }
}
```

#### 2.10 Auth 헬스체크
```http
GET /auth/health
```
**응답 예시:**
```json
{
  "status": "healthy",
  "service": "oauth2-authentication-service",
  "version": "2.0",
  "deprecated": "Use /api/health instead",
  "features": {
    "localAuth": "enabled",
    "socialLogin": "enabled",
    "oauth2": "enabled",
    "redisRateLimit": "enabled"
  },
  "timestamp": 1693392000000
}
```

---

### 3. 소셜 로그인 API

#### 3.1 Google OAuth 인증
```http
GET /oauth/social/google/auth?redirectUri=http://localhost:3000/callback
```

#### 3.2 Kakao OAuth 인증
```http
GET /oauth/social/kakao/auth?redirectUri=http://localhost:3000/callback
```

#### 3.3 Google 콜백
```http
GET /oauth/social/google/callback?code=AUTHORIZATION_CODE&state=STATE_VALUE
```

#### 3.4 Kakao 콜백
```http
GET /oauth/social/kakao/callback?code=AUTHORIZATION_CODE&state=STATE_VALUE
```

#### 3.5 소셜 로그인 상태
```http
GET /oauth/social/status
```

---

### 4. SSO (Single Sign-On) API

#### 4.1 JWT를 SSO 토큰으로 업그레이드
```http
POST /sso/upgrade
```
**요청 본문:**
```json
{
  "jwt_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```
**응답 예시:**
```json
{
  "success": true,
  "sso_token": "SSO_TOKEN_STRING",
  "message": "JWT successfully upgraded to SSO token",
  "expires_in": 28800
}
```

#### 4.2 SSO 토큰 검증
```http
POST /sso/validate
```
**요청 본문:**
```json
{
  "sso_token": "SSO_TOKEN_STRING"
}
```

#### 4.3 앱 등록
```http
POST /sso/register-app
```
**요청 본문:**
```json
{
  "app_id": "travel-app-001",
  "app_name": "Travel Planning Service",
  "redirect_uri": "https://travel-app.com/callback"
}
```

#### 4.4 SSO 세션 조회
```http
GET /sso/session?sso_token=SSO_TOKEN_STRING
```

#### 4.5 SSO 로그아웃
```http
POST /sso/logout
```
**요청 본문:**
```json
{
  "sso_token": "SSO_TOKEN_STRING"
}
```

#### 4.6 SSO 상태 확인
```http
GET /sso/status
```

---

### 5. 보호된 API (인증 필요)

#### 5.1 프로필 조회
```http
GET /api/protected/profile
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**응답 예시:**
```json
{
  "user_id": 1,
  "email": "user@example.com",
  "authenticated_at": "2024-08-28T21:00:00",
  "authorities": ["ROLE_USER"],
  "name": "홍길동",
  "current_tickets": 10,
  "role": "USER",
  "is_active": true,
  "email_verified": false
}
```

#### 5.2 대시보드 조회
```http
GET /api/protected/dashboard
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**응답 예시:**
```json
{
  "welcome_message": "안녕하세요, user@example.com님!",
  "last_login": "2024-08-28T21:00:00",
  "available_features": [
    "DevOps 워크플로우 관리",
    "실습 환경 프로비저닝",
    "AI 기반 추천 시스템",
    "학습 진도 추적"
  ],
  "system_status": "정상 운영",
  "user_permissions": ["ROLE_USER"]
}
```

#### 5.3 티켓 사용
```http
POST /api/protected/tickets/use
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**요청 본문:**
```json
{
  "purpose": "실습 환경 생성"
}
```
**응답 예시:**
```json
{
  "message": "티켓이 성공적으로 사용되었습니다",
  "purpose": "실습 환경 생성",
  "used_at": "2024-08-28T21:00:00",
  "user_email": "user@example.com",
  "remaining_tickets": "조회 중..."
}
```

#### 5.4 보호된 헬스체크
```http
GET /api/protected/health-check
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**응답 예시:**
```json
{
  "status": "authenticated",
  "timestamp": "2024-08-28T21:00:00",
  "user_id": 1,
  "user_email": "user@example.com",
  "authorities": ["ROLE_USER"]
}
```

---

### 6. 관리자 API (관리자 권한 필요)

#### 6.1 로그인 시도 통계
```http
GET /admin/login-attempts/stats
```
**응답 예시:**
```json
{
  "totalAccounts": 100,
  "lockedAccounts": 3,
  "recentFailedAttempts": 15,
  "suspiciousIPs": ["192.168.1.100", "10.0.0.5"]
}
```

#### 6.2 계정 잠금 정보 조회
```http
GET /admin/account/{email}/lock-info
```
**예시:**
```http
GET /admin/account/user@example.com/lock-info
```
**응답 예시:**
```json
{
  "email": "user@example.com",
  "isLocked": true,
  "failedAttempts": 5,
  "lockExpiresAt": "2024-08-28T22:00:00",
  "lastFailedAttempt": "2024-08-28T21:30:00"
}
```

#### 6.3 계정 잠금 해제
```http
POST /admin/account/{email}/unlock?adminUser=admin
```
**예시:**
```http
POST /admin/account/user@example.com/unlock?adminUser=admin
```
**응답 예시:**
```json
{
  "status": "success",
  "message": "계정 잠금이 해제되었습니다.",
  "email": "user@example.com",
  "admin_user": "admin"
}
```

#### 6.4 보안 대시보드
```http
GET /admin/security/dashboard
```

#### 6.5 잠긴 계정 목록
```http
GET /admin/accounts/locked
```

#### 6.6 로그인 실패 계정 목록
```http
GET /admin/accounts/failed
```

#### 6.7 의심스러운 IP 목록
```http
GET /admin/ips/suspicious
```

#### 6.8 모든 계정 목록
```http
GET /admin/accounts/all
```

#### 6.9 계정 개요
```http
GET /admin/accounts/overview
```

#### 6.10 사용자 재동기화 (전체)
```http
POST /admin/users/resync
```
**요청 본문:**
```json
{
  "forceSync": true
}
```

#### 6.11 특정 사용자 재동기화
```http
POST /admin/users/{userId}/resync
```
**예시:**
```http
POST /admin/users/123/resync
```

---

### 7. 테스트 API (개발 환경 전용)

#### 7.1 JWT 토큰 생성
```http
GET /test/jwt-token?email=test@example.com&role=USER
```

---

## 🔐 인증 플로우 테스트 시나리오

### 시나리오 1: 일반 회원가입 및 로그인
1. `POST /auth/signup` - 회원가입 (email, password, name)
2. `POST /auth/login` - 로그인 (email, password)
3. `GET /auth/me` - 내 정보 확인 (JWT 토큰 필요)
4. `POST /auth/logout` - 로그아웃 (JWT 토큰 필요)

### 시나리오 2: 소셜 로그인
1. `GET /oauth/social/google/auth` - Google 로그인 시작
2. Google 로그인 진행
3. `GET /oauth/social/google/callback` - 콜백 처리
4. `GET /oauth/social/status` - 로그인 상태 확인

### 시나리오 3: SSO 플로우
1. `POST /auth/login` - 일반 로그인
2. `POST /sso/upgrade` - SSO 토큰 업그레이드
3. `POST /sso/validate` - SSO 토큰 검증
4. `GET /sso/session` - SSO 세션 확인
5. `POST /sso/logout` - SSO 로그아웃

### 시나리오 4: 토큰 갱신
1. `POST /auth/login` - 로그인 (access & refresh 토큰 받기)
2. Access 토큰 만료 대기
3. `POST /auth/refresh` - 새로운 토큰 발급
4. `POST /auth/validate` - 새 토큰 검증

### 시나리오 5: 비밀번호 재설정
1. `POST /auth/password-reset` - 재설정 요청
2. 이메일 확인
3. 새 비밀번호로 로그인

### 시나리오 6: 관리자 기능
1. 관리자 계정으로 로그인
2. `GET /admin/security/dashboard` - 보안 대시보드 확인
3. `GET /admin/accounts/locked` - 잠긴 계정 확인
4. `POST /admin/account/{email}/unlock` - 계정 잠금 해제

---

## 📝 테스트 도구 추천

### Postman Collection
```json
{
  "info": {
    "name": "Auth Service API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8081"
    },
    {
      "key": "jwtToken",
      "value": ""
    },
    {
      "key": "refreshToken",
      "value": ""
    },
    {
      "key": "ssoToken",
      "value": ""
    }
  ]
}
```

### cURL 예시
```bash
# 로그인
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass123!"}'

# 헤더와 함께 프로필 조회
curl -X GET http://localhost:8081/api/protected/profile \
  -H "Authorization: Bearer {JWT_TOKEN}"
```

### HTTPie 예시
```bash
# 로그인
http POST localhost:8081/auth/login \
  email=user@example.com \
  password=SecurePass123!

# 프로필 조회
http GET localhost:8081/api/protected/profile \
  Authorization:"Bearer {JWT_TOKEN}"
```

---

## ⚠️ 주의사항

1. **JWT 토큰 만료**: Access Token은 15분, Refresh Token은 7일 유효
2. **로그인 시도 제한**: 5회 실패 시 계정 잠금 (1시간)
3. **관리자 API**: ADMIN 권한이 있는 계정만 접근 가능
4. **소셜 로그인**: Google/Kakao 개발자 콘솔 설정 필요
5. **SSO**: 토큰은 8시간 유효
6. **개발 환경**: `/test` 엔드포인트는 프로덕션에서 비활성화

---

## 🐛 문제 해결

### 401 Unauthorized
- JWT 토큰 만료 확인
- Authorization 헤더 형식 확인 (`Bearer ` 접두사 필요)

### 403 Forbidden
- 계정 권한 확인 (일반 사용자 vs 관리자)
- 계정 잠금 상태 확인

### 423 Locked
- 계정이 잠긴 상태 (로그인 5회 실패)
- 1시간 후 자동 해제 또는 관리자 수동 해제 필요

### 500 Internal Server Error
- 서버 로그 확인
- 요청 본문 형식 검증