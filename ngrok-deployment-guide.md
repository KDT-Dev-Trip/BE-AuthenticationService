# 🌐 ngrok 운영 환경 배포 가이드

## 📋 개요
MSA 통합 테스트 완료 후, 로컬 환경에서 개발한 DevTrip 마이크로서비스를 ngrok을 통해 외부에서 접근 가능하도록 배포하는 가이드입니다.

## 🎯 배포 아키텍처

### 현재 서비스 구성
```
🌐 External Access (ngrok)
    ↓
🚪 Gateway: Authentication Service (8080)
    ↓
🔄 Internal Services:
    ├── Payment Service (8081)
    ├── User Management Service (8082)
    ├── Mission Management Service (8083)
    ├── AI Evaluation Service (8084)
    └── Monitoring Service (8085)
```

## 🚀 배포 단계별 가이드

### 1. 사전 준비사항

#### 1.1 ngrok 설치 및 설정
```bash
# ngrok 설치 (Windows)
# https://ngrok.com/download에서 다운로드

# ngrok 인증 토큰 설정
ngrok authtoken YOUR_AUTHTOKEN
```

#### 1.2 필요한 서비스 확인
```bash
# 모든 서비스가 실행 중인지 확인
curl http://localhost:8080/actuator/health  # Auth Service
curl http://localhost:8081/actuator/health  # Payment Service
curl http://localhost:8082/actuator/health  # User Management Service
curl http://localhost:8083/actuator/health  # Mission Management Service
curl http://localhost:8084/actuator/health  # AI Evaluation Service
```

### 2. ngrok 터널 설정

#### 2.1 단일 게이트웨이 노출 (권장)
```bash
# Authentication Service (Gateway)만 외부 노출
ngrok http 8080
```

#### 2.2 ngrok 설정 파일 활용 (다중 터널)
`ngrok.yml` 파일 생성:
```yaml
version: "2"
authtoken: YOUR_AUTHTOKEN
tunnels:
  auth-gateway:
    addr: 8080
    proto: http
    subdomain: devtrip-api
  payment:
    addr: 8081
    proto: http
    subdomain: devtrip-payment
  user:
    addr: 8082
    proto: http
    subdomain: devtrip-user
  mission:
    addr: 8083
    proto: http
    subdomain: devtrip-mission
  ai:
    addr: 8084
    proto: http
    subdomain: devtrip-ai
```

실행:
```bash
ngrok start --all
```

### 3. 서비스별 배포 확인

#### 3.1 Gateway를 통한 접근 테스트
```bash
# ngrok URL 확인 후 테스트 (예: https://abc123.ngrok.io)
NGROK_URL="https://your-ngrok-url.ngrok.io"

# 헬스체크
curl $NGROK_URL/auth/test

# 회원가입 테스트
curl -X POST $NGROK_URL/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"Test User"}'

# Gateway 라우팅 테스트
curl $NGROK_URL/gateway/payment/health
curl $NGROK_URL/gateway/user/health
curl $NGROK_URL/gateway/mission/health
curl $NGROK_URL/gateway/ai/health
```

#### 3.2 직접 서비스 접근 테스트 (다중 터널 사용시)
```bash
# 각 서비스별 접근
curl https://devtrip-payment.ngrok.io/actuator/health
curl https://devtrip-user.ngrok.io/actuator/health
curl https://devtrip-mission.ngrok.io/actuator/health
curl https://devtrip-ai.ngrok.io/actuator/health
```

### 4. 프로덕션 고려사항

#### 4.1 보안 설정
```yaml
# ngrok.yml 보안 강화
tunnels:
  auth-gateway:
    addr: 8080
    proto: http
    auth: "username:password"
    inspect: false
    bind_tls: true
```

#### 4.2 환경변수 설정
```bash
# 서비스별 환경변수 업데이트
export SPRING_PROFILES_ACTIVE=prod
export NGROK_BASE_URL=https://your-ngrok-url.ngrok.io

# 데이터베이스 연결 (프로덕션용)
export DB_HOST=your-production-db-host
export DB_USERNAME=your-db-username
export DB_PASSWORD=your-db-password
```

#### 4.3 로깅 및 모니터링
```bash
# 각 서비스의 로그 레벨 조정
export LOGGING_LEVEL_ROOT=INFO
export LOGGING_LEVEL_COM_YOUR_PACKAGE=DEBUG

# Actuator endpoints 보안
export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics
```

## 📊 성능 및 제한사항

### ngrok 무료 플랜 제한사항
- **동시 터널**: 1개
- **요청 제한**: 40 req/min
- **대역폭**: 제한 있음
- **세션 시간**: 8시간

### 권장 배포 방식
1. **개발/테스트**: 단일 Gateway(8080) 노출
2. **데모/프레젠테이션**: 다중 터널 활용
3. **프로덕션**: ngrok Pro 플랜 또는 AWS/GCP 배포 고려

## 🔧 트러블슈팅

### 일반적인 문제들

#### 1. 연결 실패
```bash
# 서비스 상태 확인
netstat -ano | findstr :8080
curl http://localhost:8080/actuator/health

# ngrok 상태 확인
curl http://127.0.0.1:4040/api/tunnels
```

#### 2. Gateway 라우팅 실패
```bash
# Spring Cloud Gateway 로그 확인
tail -f logs/application.log | grep Gateway

# 서비스 등록 확인
curl http://localhost:8080/actuator/gateway/routes
```

#### 3. CORS 오류
```yaml
# application.yml에 CORS 설정 추가
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "https://your-ngrok-url.ngrok.io"
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
```

## 📱 클라이언트 연동

### Frontend 설정 예제
```javascript
// API base URL 설정
const API_BASE_URL = 'https://your-ngrok-url.ngrok.io';

// API 호출 예제
const signup = async (userData) => {
  const response = await fetch(`${API_BASE_URL}/auth/signup`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(userData)
  });
  return response.json();
};
```

### 모바일 앱 연동
```kotlin
// Android - Retrofit 설정
class ApiConfig {
    companion object {
        const val BASE_URL = "https://your-ngrok-url.ngrok.io/"
        
        fun getApiService(): ApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
```

## 🎯 운영 체크리스트

### 배포 전 확인사항
- [ ] 모든 서비스 헬스체크 통과
- [ ] 데이터베이스 연결 확인
- [ ] Kafka 브로커 연결 확인
- [ ] 환경변수 설정 완료
- [ ] 로그 레벨 적정 설정

### 배포 후 확인사항
- [ ] ngrok 터널 정상 동작
- [ ] Gateway 라우팅 정상 동작  
- [ ] 각 서비스 API 응답 정상
- [ ] Kafka 이벤트 통신 정상
- [ ] 클라이언트 연동 정상

### 모니터링 항목
- [ ] ngrok 대시보드 모니터링
- [ ] 서비스별 헬스체크 주기적 확인
- [ ] 로그 파일 모니터링
- [ ] 메모리/CPU 사용량 확인

## 🚀 대안 배포 방식

### 1. Docker Compose + ngrok
```yaml
version: '3.8'
services:
  auth-service:
    build: ./BE-authentication-service
    ports:
      - "8080:8080"
  # ... 기타 서비스들
  
  ngrok:
    image: ngrok/ngrok:latest
    restart: unless-stopped
    command: http auth-service:8080
    volumes:
      - ./ngrok.yml:/etc/ngrok.yml
```

### 2. 클라우드 배포 (추후 고려)
- **AWS ECS/EKS**: 컨테이너 기반 배포
- **Google Cloud Run**: 서버리스 배포
- **Azure Container Instances**: 간편 배포

---
**작성일**: 2025-08-28  
**버전**: 1.0  
**상태**: ✅ MSA 통합 테스트 완료 후 배포 준비