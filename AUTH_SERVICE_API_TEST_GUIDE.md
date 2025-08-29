# Auth Service API 테스트 가이드

## 🔧 테스트 환경 설정

### Base URL
```
http://localhost:8080
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
**응답 예시:**
```json
{
  "message": "Authentication Service is running",
  "status": "active"
}
```

#### 1.2 헬스체크
```http
GET /health
```
**응답 예시:**
```json
{
  "status": "UP",
  "timestamp": "2024-08-28T21:00:00Z"
}
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
  "authUserId": "user123",
  "password": "SecurePass123!",
  "email": "user@example.com",
  "nickname": "TestUser",
  "birthDate": "1990-01-01",
  "preferTripType": "ADVENTURE"
}
```

#### 2.2 로그인
```http
POST /auth/login
```
**요청 본문:**
```json
{
  "authUserId": "user123",
  "password": "SecurePass123!"
}
```
**응답 예시:**
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "authUserId": "user123",
  "userId": 1,
  "email": "user@example.com"
}
```

#### 2.3 토큰 갱신
```http
POST /auth/refresh
```
**요청 본문:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 2.4 비밀번호 재설정
```http
POST /auth/password-reset
```
**요청 본문:**
```json
{
  "authUserId": "user123",
  "email": "user@example.com"
}
```

#### 2.5 토큰 검증
```http
POST /auth/validate
```
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
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

#### 2.8 사용자 동기화
```http
POST /auth/sync-users
```
**요청 본문:**
```json
{
  "userIds": [1, 2, 3],
  "authUserIds": ["user123", "user456", "user789"]
}
```

#### 2.9 Auth 테스트
```http
GET /auth/test
```

#### 2.10 Auth 헬스체크
```http
GET /auth/health
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
**헤더:**
```json
{
  "Authorization": "Bearer {JWT_TOKEN}"
}
```
**요청 본문:**
```json
{
  "appId": "travel-app-001"
}
```

#### 4.2 SSO 토큰 검증
```http
POST /sso/validate
```
**요청 본문:**
```json
{
  "ssoToken": "SSO_TOKEN_STRING",
  "appId": "travel-app-001"
}
```

#### 4.3 앱 등록
```http
POST /sso/register-app
```
**요청 본문:**
```json
{
  "appId": "travel-app-002",
  "appName": "Travel Planning Service",
  "redirectUri": "https://travel-app.com/callback",
  "description": "여행 계획 서비스"
}
```

#### 4.4 SSO 세션 조회
```http
GET /sso/session
```
**헤더:**
```json
{
  "X-SSO-Token": "SSO_TOKEN_STRING"
}
```

#### 4.5 SSO 로그아웃
```http
POST /sso/logout
```
**요청 본문:**
```json
{
  "ssoToken": "SSO_TOKEN_STRING"
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
  "ticketCount": 1,
  "usageType": "MISSION_ATTEMPT",
  "missionId": "mission-001"
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

---

### 6. 관리자 API (관리자 권한 필요)

#### 6.1 로그인 시도 통계
```http
GET /admin/login-attempts/stats
```
**헤더:**
```json
{
  "Authorization": "Bearer {ADMIN_JWT_TOKEN}"
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

#### 6.3 계정 잠금 해제
```http
POST /admin/account/{email}/unlock
```
**예시:**
```http
POST /admin/account/user@example.com/unlock
```
**요청 본문:**
```json
{
  "reason": "관리자 수동 해제",
  "adminNote": "사용자 요청에 의한 해제"
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

#### 6.10 사용자 재동기화
```http
POST /admin/users/resync
```
**요청 본문:**
```json
{
  "authUserIds": ["user123", "user456"],
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
GET /test/jwt-token?authUserId=testuser&email=test@example.com
```

---

## 🔐 인증 플로우 테스트 시나리오

### 시나리오 1: 일반 회원가입 및 로그인
1. `POST /auth/signup` - 회원가입
2. `POST /auth/login` - 로그인
3. `GET /auth/me` - 내 정보 확인
4. `POST /auth/logout` - 로그아웃

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
  -d '{"authUserId":"user123","password":"SecurePass123!"}'

# 헤더와 함께 프로필 조회
curl -X GET http://localhost:8081/api/protected/profile \
  -H "Authorization: Bearer {JWT_TOKEN}"
```

### HTTPie 예시
```bash
# 로그인
http POST localhost:8081/auth/login \
  authUserId=user123 \
  password=SecurePass123!

# 프로필 조회
http GET localhost:8081/api/protected/profile \
  Authorization:"Bearer {JWT_TOKEN}"
```

---

## ⚠️ 주의사항

1. **JWT 토큰 만료**: Access Token은 15분, Refresh Token은 7일 유효
2. **로그인 시도 제한**: 5회 실패 시 계정 잠금 (30분)
3. **관리자 API**: ADMIN 권한이 있는 계정만 접근 가능
4. **소셜 로그인**: Google/Kakao 개발자 콘솔 설정 필요
5. **SSO**: 앱 등록 후 사용 가능
6. **개발 환경**: `/test` 엔드포인트는 프로덕션에서 비활성화

---

## 🐛 문제 해결

### 401 Unauthorized
- JWT 토큰 만료 확인
- Authorization 헤더 형식 확인 (`Bearer ` 접두사 필요)

### 403 Forbidden
- 계정 권한 확인 (일반 사용자 vs 관리자)
- 계정 잠금 상태 확인

### 429 Too Many Requests
- Rate Limiting 적용됨 (분당 60회)
- 잠시 후 재시도

### 500 Internal Server Error
- 서버 로그 확인
- 요청 본문 형식 검증