# Auth0 Actions 설정 가이드

Auth0 Actions를 통해 사용자 인증 이벤트를 백엔드 서버로 전송하고 Kafka 이벤트를 발행하는 방법입니다.

## 📋 개요

Auth0 Actions는 사용자 인증 플로우의 특정 시점에서 사용자 정의 코드를 실행할 수 있게 해주는 기능입니다. 이를 통해 로그인/회원가입 시점에 백엔드로 실시간 이벤트를 전송할 수 있습니다.

## 🔧 설정 방법

### 1. Post-Login Action 설정

사용자가 로그인을 완료한 후 실행되는 Action입니다.

#### 단계별 설정:

1. **Auth0 Dashboard 접속**
   - [Auth0 Dashboard](https://manage.auth0.com) 로그인
   - 해당 테넌트 선택

2. **Actions 메뉴로 이동**
   - 왼쪽 메뉴에서 `Actions` > `Library` 클릭

3. **새 Action 생성**
   - `+ Build Custom` 버튼 클릭
   - Name: `Post Login Webhook`
   - Trigger: `Login / Post Login` 선택
   - Runtime: `Node.js 18` (최신 버전)

4. **코드 입력**
   - `post-login-action.js` 파일의 내용을 복사하여 붙여넣기

5. **Dependencies 추가**
   - Dependencies 탭으로 이동
   - `axios` 패키지 추가 (버전: `latest`)

6. **Secrets 설정**
   - Secrets 탭으로 이동
   - Key: `BACKEND_WEBHOOK_URL`
   - Value: `http://localhost:8080/webhook/auth0/user-login` (로컬 개발용)

7. **Deploy**
   - `Deploy` 버튼 클릭하여 Action 배포

8. **Flow에 추가**
   - `Actions` > `Flows` > `Login` 으로 이동
   - 방금 생성한 Action을 Flow에 드래그하여 추가
   - `Apply` 클릭하여 적용

### 2. Post-User Registration Action 설정

신규 사용자 회원가입 후 실행되는 Action입니다.

#### 단계별 설정:

1. **새 Action 생성**
   - Name: `Post User Registration Webhook`
   - Trigger: `Login / Post User Registration` 선택

2. **코드 및 설정**
   - `post-user-registration-action.js` 파일 내용 사용
   - Dependencies: `axios` 추가
   - Secrets: `BACKEND_WEBHOOK_URL` = `http://localhost:8080/webhook/auth0/user-signup`

3. **Deploy 및 Flow 추가**
   - Deploy 후 Login Flow에 추가

## 🌐 웹훅 엔드포인트

백엔드에서 다음 엔드포인트들이 Auth0 Action으로부터 이벤트를 수신합니다:

### Login 웹훅
- **URL**: `POST /webhook/auth0/user-login`
- **설명**: 사용자 로그인 시 호출
- **Kafka 이벤트**: `UserLoggedInEvent` 발행

### Signup 웹훅  
- **URL**: `POST /webhook/auth0/user-signup`
- **설명**: 신규 사용자 가입 시 호출
- **Kafka 이벤트**: `UserSignedUpEvent` 발행

### 헬스체크
- **URL**: `GET /webhook/auth0/health`
- **설명**: 웹훅 엔드포인트 상태 확인

## 📊 이벤트 플로우

```
1. 사용자 로그인/회원가입
        ↓
2. Auth0 인증 처리
        ↓
3. Auth0 Action 실행
        ↓
4. 백엔드 웹훅 호출 (HTTP POST)
        ↓
5. Kafka 이벤트 발행
        ↓
6. 다른 마이크로서비스에서 이벤트 수신
```

## 🔍 테스트 방법

### 1. 로컬 테스트

1. **ngrok 설정** (Auth0에서 로컬 서버 접근용)
   ```bash
   # ngrok 설치 후
   ngrok http 8080
   ```

2. **웹훅 URL 업데이트**
   - Auth0 Action의 Secret에서 `BACKEND_WEBHOOK_URL`을 ngrok URL로 변경
   - 예: `https://abc123.ngrok.io/webhook/auth0/user-login`

3. **테스트 실행**
   - 브라우저에서 `http://localhost:8080/test-login.html` 접속
   - Auth0 로그인 수행
   - 백엔드 로그에서 웹훅 수신 확인

### 2. 웹훅 직접 테스트

```bash
# 로그인 웹훅 테스트
curl -X POST http://localhost:8080/webhook/auth0/user-login \
  -H "Content-Type: application/json" \
  -d '{
    "user": {
      "user_id": "auth0|test123",
      "email": "test@example.com",
      "name": "Test User"
    },
    "connection": {
      "name": "Username-Password-Authentication"
    },
    "request": {
      "ip": "127.0.0.1",
      "user_agent": "Test Agent"
    },
    "event_type": "user_login"
  }'

# 회원가입 웹훅 테스트  
curl -X POST http://localhost:8080/webhook/auth0/user-signup \
  -H "Content-Type: application/json" \
  -d '{
    "user": {
      "user_id": "auth0|newuser123", 
      "email": "newuser@example.com",
      "name": "New User",
      "email_verified": true
    },
    "connection": {
      "name": "google-oauth2"
    },
    "event_type": "user_signup"
  }'
```

## 🚨 주의사항

1. **타임아웃 설정**: Action은 최대 실행 시간 제한이 있으므로 웹훅 호출 시 적절한 타임아웃 설정

2. **에러 처리**: 웹훅 호출 실패 시에도 사용자 인증은 계속 진행되도록 구현

3. **보안**: 운영 환경에서는 HTTPS 엔드포인트 사용 필수

4. **Rate Limiting**: Auth0 Action 실행 횟수 제한 고려

5. **로깅**: 디버깅을 위한 적절한 로깅 구현

## 📈 모니터링

- Auth0 Dashboard > Monitoring > Logs에서 Action 실행 로그 확인
- 백엔드 애플리케이션 로그에서 웹훅 수신 확인  
- Kafka 토픽에서 이벤트 발행 확인

## 🔧 트러블슈팅

### 웹훅 호출되지 않음
- Action이 올바른 Flow에 추가되었는지 확인
- Secret 값이 올바르게 설정되었는지 확인
- ngrok URL이 활성 상태인지 확인

### Kafka 이벤트 발행되지 않음
- EventPublisher 빈이 정상적으로 등록되었는지 확인
- Kafka 브로커 연결 상태 확인
- application.properties의 Kafka 설정 확인

이제 Auth0 Actions를 통해 실시간으로 사용자 인증 이벤트를 백엔드로 전송하고 Kafka를 통해 다른 마이크로서비스들과 연동할 수 있습니다! 🎉