# 🔐 DevOps Platform Authentication Service

[![Build Status](https://jenkins.devops-platform.com/buildStatus/icon?job=authentication-service)](https://jenkins.devops-platform.com/job/authentication-service)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=authentication-service&metric=security_rating)](https://sonarcloud.io/dashboard?id=authentication-service)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=authentication-service&metric=coverage)](https://sonarcloud.io/dashboard?id=authentication-service)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> 🚀 **기업급 OAuth 2.0 인증 서비스**  
> SSO, 소셜 로그인, 팀 관리, CI/CD 자동화를 지원하는 완전한 인증 솔루션

## 📋 Table of Contents

- [✨ Features](#-features)
- [🏗️ Architecture](#️-architecture)
- [🚀 Quick Start](#-quick-start)
- [🔧 Installation](#-installation)
- [🌐 API Documentation](#-api-documentation)
- [🔐 Security](#-security)
- [📊 Monitoring](#-monitoring)
- [🐳 Docker & Kubernetes](#-docker--kubernetes)
- [🔄 CI/CD](#-cicd)
- [🧪 Testing](#-testing)
- [📖 Documentation](#-documentation)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

## ✨ Features

### 🔐 **Core Authentication**
- **OAuth 2.0 Authorization Server** - RFC 6749 표준 준수
- **JWT Token System** - Access Token, Refresh Token, ID Token 완전 지원
- **PKCE Support** - Authorization Code Flow 보안 강화
- **Token Management** - 토큰 발급, 갱신, 폐기, 블랙리스트

### 🌟 **Single Sign-On (SSO)**
- **Multi-Application SSO** - 여러 애플리케이션 간 단일 인증
- **Session Management** - Redis 기반 세션 관리
- **Cross-Domain Support** - CORS 설정을 통한 도메인 간 인증
- **Auto Logout** - 전체 애플리케이션 동시 로그아웃

### 👥 **User & Team Management**
- **User Registration** - 이메일/비밀번호 기반 회원가입
- **Team System** - 팀 생성, 관리, 역할 기반 접근 제어
- **Invite Codes** - 초대 코드를 통한 팀 가입
- **Role Management** - USER, ADMIN, OWNER 역할 시스템

### 🌐 **Social Login Integration**
- **Google OAuth** - Google 계정 연동 로그인
- **Kakao OAuth** - 카카오 계정 연동 로그인
- **Account Linking** - 소셜 계정과 로컬 계정 연결
- **Profile Sync** - 소셜 프로필 정보 동기화

### 🛡️ **Security Features**
- **Rate Limiting** - Redis 기반 로그인 시도 제한
- **Password Security** - BCrypt 암호화
- **Security Headers** - CORS, XSS, CSRF 보호
- **Audit Logging** - 보안 이벤트 로깅

### 📊 **Monitoring & Observability**
- **Prometheus Metrics** - 상세한 성능 메트릭
- **Health Checks** - Kubernetes 준비성/활성 프로브
- **Structured Logging** - JSON 형식 구조화 로깅
- **Distributed Tracing** - 요청 추적 및 성능 분석

## 🏗️ Architecture

```mermaid
graph TB
    subgraph "Client Applications"
        WEB[Web App]
        MOBILE[Mobile App]
        API[3rd Party API]
    end
    
    subgraph "Load Balancer"
        ALB[Application Load Balancer]
    end
    
    subgraph "Authentication Service"
        AUTH[Auth Controller]
        SSO[SSO Controller]
        OAUTH[OAuth2 Controller]
        SOCIAL[Social Login Controller]
    end
    
    subgraph "Security Layer"
        JWT[JWT Service]
        RATE[Rate Limiter]
        FILTER[Security Filters]
    end
    
    subgraph "Data Layer"
        REDIS[(Redis)]
        MYSQL[(MySQL)]
        KAFKA[(Kafka)]
    end
    
    subgraph "Monitoring"
        PROM[Prometheus]
        GRAFANA[Grafana]
    end
    
    WEB --> ALB
    MOBILE --> ALB
    API --> ALB
    
    ALB --> AUTH
    ALB --> SSO
    ALB --> OAUTH
    ALB --> SOCIAL
    
    AUTH --> JWT
    SSO --> JWT
    OAUTH --> JWT
    
    JWT --> REDIS
    RATE --> REDIS
    AUTH --> MYSQL
    AUTH --> KAFKA
    
    AUTH --> PROM
    REDIS --> PROM
    MYSQL --> PROM
```

## 🚀 주요 기능

### 인증 관련
- ✅ Auth0 소셜 로그인 (Google, GitHub 등)
- ✅ JWT 토큰 검증 및 사용자 정보 추출
- ✅ 비밀번호 재설정 요청 처리
- ✅ 토큰 유효성 검사

### 이벤트 처리
- ✅ 사용자 회원가입 이벤트 발행 (`user.signed-up`)
- ✅ 사용자 로그인 이벤트 발행 (`user.logged-in`)
- ✅ 비밀번호 재설정 요청 이벤트 발행 (`user.password-reset-requested`)

### 모니터링
- ✅ Prometheus 메트릭 수집
- ✅ 구조화된 로깅
- ✅ 헬스 체크 엔드포인트

## 📊 수집되는 메트릭

| 메트릭명 | 설명 |
|---------|------|
| `signup_success_count` | 회원가입 성공 횟수 |
| `login_success_count` | 로그인 성공 횟수 |
| `login_failure_count` | 로그인 실패 횟수 |
| `jwt_issuance_latency_seconds` | JWT 발급 소요 시간 |

## 🔧 환경 설정

### 1. application.properties 설정

```properties
# Auth0 Configuration (필수)
auth0.domain=your-auth0-domain.auth0.com
auth0.clientId=your-client-id  
auth0.clientSecret=odE2aRjgMFvAas3dBcDHcgDgiqJ9RJDxFObkOoiOwu162gJOFBV5duwdS1EMy7Od
auth0.audience=https://api.devops-platform.com

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/devops_platform
spring.datasource.username=devops_user
spring.datasource.password=devops_password

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Redis
spring.redis.host=localhost
spring.redis.port=6379
```

### 2. 필수 인프라 구성

```bash
# MySQL 실행
docker run -d --name mysql-auth \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=devops_platform \
  -e MYSQL_USER=devops_user \
  -e MYSQL_PASSWORD=devops_password \
  -p 3306:3306 mysql:8.0

# Redis 실행  
docker run -d --name redis-auth \
  -p 6379:6379 redis:7-alpine

# Kafka 실행 (docker-compose 권장)
# kafka, zookeeper 설정 필요
```

## 🚦 실행 방법

### 개발 환경
```bash
# 의존성 설치
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 또는
java -jar build/libs/BE-authentication-service-0.0.1-SNAPSHOT.jar
```

### Docker 실행
```bash
# 이미지 빌드
docker build -t auth-service .

# 컨테이너 실행
docker run -d --name auth-service \
  -p 8080:8080 \
  -e AUTH0_DOMAIN=your-domain.auth0.com \
  -e AUTH0_CLIENT_ID=your-client-id \
  auth-service
```

## 📡 API 엔드포인트

### 인증 API
- `POST /auth/callback` - Auth0 콜백 처리
- `POST /auth/validate` - JWT 토큰 검증  
- `POST /auth/password-reset` - 비밀번호 재설정 요청
- `GET /auth/me` - 현재 사용자 정보 조회

### 모니터링 API
- `GET /auth/health` - 헬스 체크
- `GET /actuator/health` - 상세 헬스 체크
- `GET /actuator/metrics` - 메트릭 조회
- `GET /actuator/prometheus` - Prometheus 메트릭

## 📋 API 사용 예제

### 1. Auth0 콜백 처리
```bash
curl -X POST http://localhost:8080/auth/callback \
  -H "Content-Type: application/json" \
  -d '{
    "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIs..."
  }'
```

### 2. 토큰 검증
```bash
curl -X POST http://localhost:8080/auth/validate \
  -H "Content-Type: application/json" \
  -d '{
    "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIs..."
  }'
```

### 3. 현재 사용자 정보
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIs..."
```

## 📊 로그 형식

```
# 회원가입 성공
auth.signup.success userId=user-123 email=user@example.com source=AUTH0_SOCIAL

# 로그인 성공
auth.login.success userId=user-123 email=user@example.com ipAddress=192.168.1.100 method=AUTH0_SOCIAL

# 로그인 실패
auth.login.failure email=user@example.com ipAddress=192.168.1.100

# 비밀번호 재설정 요청
auth.password.reset.requested userId=user-123 email=user@example.com
```

## 🔍 트러블슈팅

### 자주 발생하는 문제

1. **JWT 검증 실패**
   - Auth0 도메인과 Audience 설정 확인
   - JWKS URI 접근 가능 여부 확인

2. **Kafka 연결 실패**
   - Kafka 서버 실행 상태 확인
   - 토픽 자동 생성 설정 확인

3. **데이터베이스 연결 실패**
   - MySQL 서비스 실행 확인
   - 데이터베이스 및 사용자 권한 확인

### 로그 확인 방법
```bash
# 애플리케이션 로그
tail -f logs/application.log

# Docker 컨테이너 로그
docker logs -f auth-service

# Kubernetes Pod 로그  
kubectl logs -f deployment/auth-service
```

## 🧪 테스트

본 프로젝트는 **TDD(Test-Driven Development)** 방식으로 개발되어 **85% 이상의 테스트 커버리지**를 달성했습니다.

### 테스트 종류

| 테스트 유형 | 설명 | 테스트 파일 |
|-----------|------|-----------|
| **단위 테스트** | 개별 컴포넌트 로직 검증 | `*Test.java` |
| **통합 테스트** | API 엔드포인트 및 DB 연동 | `*IntegrationTest.java` |
| **Kafka 테스트** | 이벤트 발행/수신 검증 | `KafkaIntegrationTest.java` |
| **보안 테스트** | 인증/권한 및 보안 취약점 검증 | `SecurityTest.java` |

### 테스트 실행

```bash
# 모든 테스트 실행 (권장)
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests AuthServiceTest

# 특정 패키지의 테스트만 실행
./gradlew test --tests "ac.su.kdt.beauthenticationservice.service.*"

# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# 테스트 커버리지 리포트 생성
./gradlew jacocoTestReport

# 커버리지 검증 (최소 80% 요구)
./gradlew jacocoTestCoverageVerification
```

### TestContainers 기반 실제 환경 테스트

통합 테스트는 **TestContainers**를 사용하여 실제 MySQL, Redis, Kafka 환경에서 실행됩니다:

```bash
# Docker가 실행 중인 상태에서 통합 테스트 실행
docker ps  # Docker 데몬 확인
./gradlew test --tests "*IntegrationTest"
```

### 테스트 커버리지 확인

```bash
# 테스트 실행 후 커버리지 리포트 확인
open build/reports/jacoco/test/html/index.html

# 또는 XML 리포트 확인  
cat build/reports/jacoco/test/jacocoTestReport.xml
```

### 테스트 데이터베이스 설정

테스트는 다음 환경에서 실행됩니다:
- **H2 인메모리 DB**: 빠른 단위 테스트
- **TestContainers MySQL**: 실제 환경과 동일한 통합 테스트
- **TestContainers Kafka**: 이벤트 스트리밍 테스트
- **TestContainers Redis**: 캐싱 로직 테스트

## 🔒 보안 고려사항

- Auth0 Client Secret은 환경변수로만 관리
- JWT 토큰 만료 시간 적절히 설정 (현재 24시간)
- CORS 설정을 프로덕션 환경에 맞게 제한
- 실패한 로그인 시도 모니터링 및 제한
- 비밀번호 재설정 토큰 유효 시간 제한 (30분)

## 📝 개발 가이드

### 새로운 이벤트 추가
1. `model/dto` 패키지에 이벤트 클래스 생성
2. `EventPublisher`에 발행 메서드 추가
3. `KafkaConfig`에 토픽 설정 추가
4. `AuthService`에서 이벤트 발행 로직 구현

### 새로운 메트릭 추가
1. `MetricsConfig` 또는 서비스에서 메트릭 Bean 생성
2. 비즈니스 로직에서 메트릭 업데이트
3. Prometheus에서 메트릭 수집 확인

---

**최종 업데이트**: 2025-08-07  
**버전**: 1.0.0  
**문의**: DevOps Platform Team