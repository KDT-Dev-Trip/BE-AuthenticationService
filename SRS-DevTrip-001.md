# 소프트웨어 요구사항 명세서 (SRS)
## DevTrip - DevOps 교육 플랫폼

**Document ID:** SRS-DevTrip-001

**Revision:** 1.0

**Date:** 2025-01-28

**Standard:** ISO/IEC/IEEE 29148:2018

---

## 1. Introduction

### 1.1 목적 (Purpose)

이 문서는 DevOps 실습 교육을 위한 클라우드 기반 통합 플랫폼 DevTrip의 요구사항을 정의한다. 본 플랫폼은 Kubernetes 환경 기반의 실제 인프라 실습, AI 기반 자동 평가, 구독형 티켓 관리, 실시간 터미널 접근 등의 핵심 기능을 제공한다.

### 1.2 범위 (Scope)

- DevOps 미션 기반 실습 환경 제공 (Docker, Kubernetes, CI/CD)
- Kubernetes Pod 기반 격리된 개발 환경 동적 생성
- WebSocket 기반 실시간 터미널 접근
- Google Gemini AI 기반 실습 결과 자동 평가
- 구독 플랜별 티켓 시스템 및 결제 관리 (Stripe, Toss Payments)
- OAuth 2.0 및 소셜 로그인 인증 시스템
- 팀 기반 협업 및 권한 관리
- 실시간 모니터링 및 리소스 관리

### 1.3 용어 정의

| 용어 | 정의 |
| --- | --- |
| DevTrip | DevOps 실습 교육 플랫폼 |
| Mission | 개별 DevOps 실습 과제 (Docker, Kubernetes, CI/CD 등) |
| MissionAttempt | 사용자의 미션 시도 기록 및 실행 환경 |
| Pod | Kubernetes 기반 격리된 개발 환경 |
| Ticket | 미션 실행을 위한 소모성 크레딧 |
| Subscription | 구독 플랜 (Economy, Business, First Class) |
| AI Evaluation | Google Gemini 기반 실습 결과 자동 평가 시스템 |
| Terminal | WebSocket 기반 실시간 터미널 접근 인터페이스 |
| S3 Storage | AWS S3 기반 실습 데이터 및 로그 저장소 |
| OAuth SSO | Single Sign-On 인증 시스템 |

## 2. Stakeholders

| 역할 | 이름 | 책임 |
| --- | --- |
| 팀 리더 | 석예은 | 프로젝트 총괄, 아키텍처 설계 |
| 백엔드 개발자 | 석예은, 김지원, 윤정수, 정준호, 최도영 | 마이크로서비스 아키텍처 구현, API 개발, 데이터베이스 설계 |
| 프론트엔드 개발자 | 석예은, 김지원, 정준호 | 사용자 인터페이스 구현, API 연동 |
| 인프라 엔지니어 | 윤정수, 최도영 | Kubernetes 클러스터 구축, AWS 인프라 관리, CI/CD 파이프라인 |
| AI 연동 개발자 | 김지원, 정준호 | Google Gemini API 연동, 평가 로직 구현 |
| 결제 시스템 개발자 | 전체 팀 | Stripe/Toss Payments 연동, 구독 관리 |

## 3. System Context and Interfaces

### 3.1 전체 시스템 아키텍처 (Logical View)

```
[사용자/팀]
   ↓ (웹 브라우저)
[React 프론트엔드]
   ↓ HTTPS/REST API
[API Gateway (Spring Cloud Gateway)]
   ↓
┌─────────────────────────────────────────────────────────────┐
│                   마이크로서비스 계층                        │
├─────────────────┬─────────────────┬─────────────────────────┤
│ Authentication  │ Mission Mgmt    │ AI Evaluation           │
│ Service         │ Service         │ Service                 │
│ - JWT/OAuth     │ - K8s Pod Mgmt  │ - Gemini API            │
│ - Social Login  │ - Terminal WS   │ - Code Analysis         │
├─────────────────┼─────────────────┼─────────────────────────┤
│ User Mgmt       │ Payment         │ Monitoring              │
│ Service         │ Service         │ Service                 │
│ - Profile Mgmt  │ - Subscriptions │ - Metrics Collection    │
│ - Team Mgmt     │ - Ticket Mgmt   │ - Log Aggregation       │
└─────────────────┴─────────────────┴─────────────────────────┘
   ↓
┌─────────────────────────────────────────────────────────────┐
│                     데이터 계층                             │
├─────────────────┬─────────────────┬─────────────────────────┤
│ MySQL           │ Redis           │ Apache Kafka            │
│ - User Data     │ - Session Mgmt  │ - Event Streaming       │
│ - Mission Data  │ - Rate Limiting │ - Service Communication │
│ - Payment Data  │ - Caching       │                         │
└─────────────────┴─────────────────┴─────────────────────────┘
   ↓
┌─────────────────────────────────────────────────────────────┐
│                   외부 서비스 계층                          │
├─────────────────┬─────────────────┬─────────────────────────┤
│ Google Gemini   │ AWS S3          │ Payment Gateways        │
│ - AI Evaluation │ - File Storage  │ - Stripe                │
│                 │ - Log Storage   │ - Toss Payments         │
├─────────────────┼─────────────────┼─────────────────────────┤
│ OAuth Providers │ Kubernetes      │ Monitoring Stack        │
│ - Google OAuth  │ - Pod Runtime   │ - Prometheus            │
│ - Kakao OAuth   │ - Resource Mgmt │ - Grafana               │
└─────────────────┴─────────────────┴─────────────────────────┘
```

### 3.2 주요 컴포넌트 인터페이스

| 컴포넌트 | 설명 | 기술 스택 |
| --- | --- | --- |
| **프론트엔드** | React 기반 SPA 웹 애플리케이션 | React 18, TypeScript |
| **API Gateway** | 라우팅, 로드밸런싱, 인증 처리 | Spring Cloud Gateway |
| **Authentication Service** | JWT 기반 인증, OAuth 2.0, SSO | Spring Boot, Redis, MySQL |
| **Mission Management Service** | 미션 관리, K8s Pod 생성, WebSocket 터미널 | Spring Boot, Kubernetes Client |
| **AI Evaluation Service** | Google Gemini 기반 코드 평가 | Spring Boot, Google Gemini API |
| **User Management Service** | 사용자 프로필, 팀 관리 | Spring Boot, MySQL |
| **Payment Service** | 구독 관리, 결제 처리, 티켓 시스템 | Spring Boot, Stripe, Toss |
| **Monitoring Service** | 시스템 모니터링, 로그 수집 | ELK Stack, Prometheus |

## 4. Specific Requirements

### 4.1 기능 요구사항

#### 4.1.1 사용자 인증 및 권한 관리

| ID | 기능명 | 설명 | 입력 | 출력 |
| --- | --- | --- | --- | --- |
| FR-001 | 사용자 회원가입 | 이메일 기반 자체 회원가입 | 이메일, 비밀번호, 이름 | JWT 토큰 |
| FR-002 | 사용자 로그인 | 이메일/비밀번호 로그인 | 이메일, 비밀번호 | JWT 토큰 |
| FR-003 | 소셜 로그인 | Google, Kakao OAuth 연동 로그인 | OAuth 인증 코드 | JWT 토큰 |
| FR-004 | SSO 인증 | Single Sign-On 토큰 관리 | JWT 토큰 | SSO 세션 |
| FR-005 | 사용자 로그아웃 | 토큰 무효화 및 세션 종료 | JWT 토큰 | 로그아웃 응답 |
| FR-006 | 팀 생성 및 관리 | 팀 생성, 멤버 초대, 역할 관리 | 팀 정보, 멤버 정보 | 팀 생성 응답 |

#### 4.1.2 미션 및 실습 환경 관리

| ID | 기능명 | 설명 | 입력 | 출력 |
| --- | --- | --- | --- | --- |
| FR-007 | 미션 조회 | 카테고리, 난이도별 미션 목록 조회 | 필터 조건 | 미션 목록 |
| FR-008 | 미션 시작 | Kubernetes Pod 기반 실습 환경 생성 | 미션 ID, 사용자 ID | Pod 연결 정보 |
| FR-009 | 터미널 접근 | WebSocket 기반 실시간 터미널 연결 | Pod 정보 | WebSocket 연결 |
| FR-010 | 미션 일시정지/재개 | 실습 진행 상태 저장 및 복원 | 미션 시도 ID | 상태 변경 응답 |
| FR-011 | 워크스페이스 저장 | S3 기반 실습 데이터 백업 | 미션 데이터 | S3 저장 경로 |
| FR-012 | 명령어 로깅 | 실습 중 실행된 명령어 기록 | 터미널 명령어 | 로그 저장 확인 |

#### 4.1.3 AI 기반 평가 시스템

| ID | 기능명 | 설명 | 입력 | 출력 |
| --- | --- | --- | --- | --- |
| FR-013 | 실습 결과 평가 | Google Gemini 기반 코드 및 실행 결과 분석 | 코드, 로그, 미션 목표 | 평가 점수, 피드백 |
| FR-014 | 평가 기준 적용 | 미션별 체크리스트 기반 정량적 평가 | 체크리스트, 실행 결과 | 달성도 분석 |
| FR-015 | 피드백 제공 | AI 기반 개선사항 및 학습 가이드 제공 | 평가 결과 | 구체적 피드백 |
| FR-016 | 평가 이력 관리 | 사용자별 평가 기록 저장 및 조회 | 사용자 ID | 평가 이력 목록 |

#### 4.1.4 구독 및 결제 관리

| ID | 기능명 | 설명 | 입력 | 출력 |
| --- | --- | --- | --- | --- |
| FR-017 | 구독 플랜 조회 | Economy, Business, First Class 플랜 정보 | - | 플랜 목록 |
| FR-018 | 구독 결제 | Stripe/Toss 기반 구독 결제 처리 | 결제 정보, 플랜 ID | 결제 완료 응답 |
| FR-019 | 티켓 관리 | 미션 실행용 티켓 사용/충전 | 사용자 ID, 티켓 수량 | 티켓 잔량 |
| FR-020 | 자동 티켓 충전 | 구독 플랜별 주기적 티켓 보충 | 구독 정보 | 충전 완료 응답 |
| FR-021 | 구독 관리 | 구독 변경, 취소, 갱신 처리 | 구독 ID, 변경 사항 | 구독 상태 |

#### 4.1.5 시스템 모니터링

| ID | 기능명 | 설명 | 입력 | 출력 |
| --- | --- | --- | --- | --- |
| FR-022 | 서비스 상태 모니터링 | 각 마이크로서비스 Health Check | - | 서비스 상태 |
| FR-023 | 리소스 사용량 추적 | CPU, 메모리, 스토리지 사용량 모니터링 | - | 리소스 메트릭 |
| FR-024 | 사용자 활동 로그 | 사용자 행동 패턴 분석용 로그 수집 | 사용자 활동 | 로그 저장 확인 |
| FR-025 | 알림 시스템 | 시스템 장애 및 임계치 초과 알림 | 임계치 설정 | 알림 발송 |

### 4.2 비기능 요구사항

| ID | 항목 | 설명 | 검증 방식 | 수용 기준 |
| --- | --- | --- | --- | --- |
| NFR-001 | 성능 | API 응답 시간 | 부하 테스트 | 95% 요청 1초 이내 응답 |
| NFR-002 | 확장성 | 동시 사용자 처리 능력 | 성능 테스트 | 1000명 동시 접속 지원 |
| NFR-003 | 가용성 | 시스템 가동률 | 모니터링 | 99.5% 이상 가동률 |
| NFR-004 | 보안 | 데이터 암호화 및 인증 | 보안 감사 | HTTPS, JWT 토큰 인증 |
| NFR-005 | 데이터 무결성 | 트랜잭션 일관성 | 데이터베이스 테스트 | ACID 트랜잭션 보장 |
| NFR-006 | 호환성 | 브라우저 호환성 | UI 테스트 | Chrome, Safari, Firefox 지원 |
| NFR-007 | 복구 가능성 | 장애 복구 시간 | 재해 복구 테스트 | RTO 30분 이내, RPO 1시간 이내 |

### 4.3 시스템 인터페이스 요구사항

#### 4.3.1 외부 API 연동

| API | 목적 | 연동 방식 | 응답 시간 요구사항 |
| --- | --- | --- | --- |
| Google Gemini | AI 기반 코드 평가 | REST API | 30초 이내 |
| Google OAuth | 소셜 로그인 인증 | OAuth 2.0 | 5초 이내 |
| Kakao OAuth | 소셜 로그인 인증 | OAuth 2.0 | 5초 이내 |
| Stripe | 결제 처리 | REST API + Webhook | 10초 이내 |
| Toss Payments | 결제 처리 | REST API + Webhook | 10초 이내 |
| AWS S3 | 파일 저장소 | REST API | 5초 이내 |
| Kubernetes API | Pod 관리 | Kubernetes Client | 30초 이내 |

#### 4.3.2 내부 서비스 통신

| 서비스 간 통신 | 방식 | 목적 |
| --- | --- | --- |
| Authentication ↔ All Services | JWT 토큰 | 인증 정보 전달 |
| Mission ↔ AI Evaluation | Kafka Event | 미션 완료 시 평가 요청 |
| Mission ↔ User | REST API | 티켓 차감/복원 |
| Payment ↔ User | Kafka Event | 구독 상태 변경 알림 |
| All Services ↔ Monitoring | Metrics/Logs | 시스템 모니터링 |

## 5. 시스템 모델

### 5.1 데이터 모델 (핵심 엔티티)

#### 5.1.1 사용자 관련 엔티티
```
User
├── id: BIGINT (PK)
├── email: VARCHAR(255) (UNIQUE)
├── password: VARCHAR(255)
├── name: VARCHAR(100)
├── social_provider: VARCHAR(50)
├── created_at: TIMESTAMP
└── updated_at: TIMESTAMP

Team
├── id: BIGINT (PK)
├── name: VARCHAR(100)
├── invite_code: VARCHAR(20) (UNIQUE)
├── max_members: INT
└── created_by: BIGINT (FK → User)

TeamMember
├── id: BIGINT (PK)
├── team_id: BIGINT (FK → Team)
├── user_id: BIGINT (FK → User)
├── role: ENUM(OWNER, ADMIN, MEMBER)
└── joined_at: TIMESTAMP
```

#### 5.1.2 미션 관련 엔티티
```
Mission
├── id: BIGINT (PK)
├── title: VARCHAR(200)
├── description: TEXT
├── category: ENUM(DOCKER, KUBERNETES, CI_CD)
├── difficulty: ENUM(BEGINNER, INTERMEDIATE, ADVANCED)
├── container_image: VARCHAR(200)
├── cpu_limit: VARCHAR(20)
├── memory_limit: VARCHAR(20)
└── evaluation_criteria: JSON

MissionAttempt
├── id: VARCHAR(36) (PK, UUID)
├── user_id: BIGINT (FK → User)
├── mission_id: BIGINT (FK → Mission)
├── status: ENUM(STARTED, IN_PROGRESS, COMPLETED, FAILED)
├── pod_name: VARCHAR(100)
├── websocket_url: VARCHAR(500)
└── started_at: TIMESTAMP
```

#### 5.1.3 결제 및 구독 엔티티
```
SubscriptionPlan
├── id: BIGINT (PK)
├── name: VARCHAR(50) (Economy/Business/First)
├── price_monthly: DECIMAL(10,2)
├── price_yearly: DECIMAL(10,2)
├── max_team_members: INT
├── monthly_attempts: INT
├── ticket_limit: INT
└── ticket_refill_hours: INT

Subscription
├── id: BIGINT (PK)
├── user_id: BIGINT (FK → User)
├── plan_id: BIGINT (FK → SubscriptionPlan)
├── stripe_subscription_id: VARCHAR(255)
├── status: ENUM(ACTIVE, CANCELLED, EXPIRED)
├── current_period_start: TIMESTAMP
└── current_period_end: TIMESTAMP

UserTicket
├── id: BIGINT (PK)
├── user_id: BIGINT (FK → User)
├── ticket_count: INT
├── last_refilled_at: TIMESTAMP
└── updated_at: TIMESTAMP
```

### 5.2 API 아키텍처

#### 5.2.1 RESTful API 설계 원칙
- **리소스 중심 설계**: `/api/v1/{resource}/{id}`
- **HTTP 메서드 활용**: GET(조회), POST(생성), PUT(전체 수정), PATCH(부분 수정), DELETE(삭제)
- **상태 코드 표준**: 200(성공), 201(생성), 400(클라이언트 오류), 401(인증 오류), 403(권한 오류), 404(리소스 없음), 500(서버 오류)
- **JSON 응답 형식**: 일관된 응답 구조 사용

#### 5.2.2 주요 API 엔드포인트

**Authentication Service**
```
POST   /api/v1/auth/signup              # 회원가입
POST   /api/v1/auth/login               # 로그인
POST   /api/v1/auth/refresh             # 토큰 갱신
GET    /api/v1/auth/me                  # 현재 사용자 정보
POST   /api/v1/auth/logout              # 로그아웃
GET    /api/v1/auth/oauth/google/login  # Google OAuth 로그인
GET    /api/v1/auth/oauth/kakao/login   # Kakao OAuth 로그인
```

**Mission Management Service**
```
GET    /api/v1/missions                 # 미션 목록 조회
GET    /api/v1/missions/{id}            # 미션 상세 조회
POST   /api/v1/missions/{id}/attempts   # 미션 시작
POST   /api/v1/missions/{id}/pause      # 미션 일시정지
POST   /api/v1/missions/{id}/resume     # 미션 재개
WS     /terminal/{attemptId}            # WebSocket 터미널 연결
```

**Payment Service**
```
GET    /api/v1/subscription-plans       # 구독 플랜 목록
POST   /api/v1/subscriptions/checkout   # Stripe 결제 페이지 생성
GET    /api/v1/tickets/users/{userId}   # 사용자 티켓 조회
POST   /api/v1/tickets/users/{userId}/use # 티켓 사용
```

**AI Evaluation Service**
```
GET    /api/v1/evaluation/{attemptId}   # 평가 결과 조회
GET    /api/v1/evaluation/history       # 평가 이력 조회
```

### 5.3 이벤트 기반 아키텍처 (Kafka)

#### 5.3.1 주요 이벤트 토픽

| 토픽명 | Producer | Consumer | 목적 |
| --- | --- | --- | --- |
| `mission.completed` | Mission Service | AI Evaluation Service | 미션 완료 시 평가 트리거 |
| `evaluation.completed` | AI Evaluation Service | User Service | 평가 완료 시 점수 업데이트 |
| `subscription.created` | Payment Service | User Service | 구독 생성 시 티켓 초기화 |
| `subscription.cancelled` | Payment Service | User Service | 구독 취소 시 권한 변경 |
| `ticket.used` | Mission Service | Payment Service | 티켓 사용 기록 |
| `user.registered` | Auth Service | User Service | 사용자 등록 시 프로필 생성 |

#### 5.3.2 이벤트 스키마 예시

```json
// mission.completed 이벤트
{
  "eventId": "uuid",
  "eventType": "MISSION_COMPLETED",
  "timestamp": "2025-01-28T10:30:00Z",
  "data": {
    "attemptId": "uuid",
    "userId": 12345,
    "missionId": 67890,
    "missionType": "KUBERNETES",
    "submissionCode": "kubectl apply -f deployment.yaml",
    "s3LogPath": "s3://bucket/logs/attempt-uuid/",
    "completedAt": "2025-01-28T10:30:00Z"
  }
}

// subscription.created 이벤트
{
  "eventId": "uuid",
  "eventType": "SUBSCRIPTION_CREATED",
  "timestamp": "2025-01-28T10:30:00Z",
  "data": {
    "userId": 12345,
    "subscriptionId": "sub_uuid",
    "planId": 1,
    "planName": "Business Class",
    "ticketLimit": 8,
    "ticketRefillHours": 12
  }
}
```

## 6. 품질 속성

### 6.1 성능 (Performance)

#### 6.1.1 응답시간 요구사항
- **일반 API**: 평균 응답시간 500ms 이내, 95% 1초 이내
- **AI 평가 API**: 평균 응답시간 30초 이내, 최대 2분
- **WebSocket 연결**: 초기 연결 3초 이내
- **Pod 생성**: 평균 30초 이내, 최대 2분

#### 6.1.2 처리량 요구사항
- **동시 사용자**: 1000명
- **API 요청**: 1000 RPS
- **미션 동시 실행**: 100개
- **AI 평가 동시 처리**: 10개

### 6.2 확장성 (Scalability)

#### 6.2.1 수평 확장
- **서비스 인스턴스**: Auto Scaling 지원
- **데이터베이스**: Read Replica 구성
- **캐시**: Redis Cluster 구성
- **메시지 큐**: Kafka 파티셔닝

#### 6.2.2 리소스 확장
- **CPU**: Pod별 최대 2 Core
- **메모리**: Pod별 최대 4GB
- **스토리지**: Pod별 최대 10GB
- **네트워크**: 제한 없음

### 6.3 가용성 (Availability)

#### 6.3.1 목표 가용성
- **전체 시스템**: 99.5% (월 3.6시간 다운타임)
- **핵심 서비스**: 99.9% (월 43분 다운타임)
- **데이터베이스**: 99.95%
- **외부 API 의존성**: 개별 서비스 SLA 준수

#### 6.3.2 장애 복구
- **RTO (Recovery Time Objective)**: 30분
- **RPO (Recovery Point Objective)**: 1시간
- **백업 주기**: 일 1회 전체, 1시간 간격 증분
- **재해 복구**: Multi-AZ 구성

### 6.4 보안 (Security)

#### 6.4.1 인증 및 인가
- **JWT 토큰**: 1시간 만료, RS256 알고리즘
- **Refresh 토큰**: 30일 만료, 안전한 저장소 보관
- **OAuth 2.0**: PKCE 적용
- **역할 기반 접근 제어**: RBAC 구현

#### 6.4.2 데이터 보안
- **전송 암호화**: TLS 1.3
- **저장 암호화**: AES-256
- **비밀번호**: BCrypt (strength 12)
- **API 키**: 환경변수로 관리

#### 6.4.3 네트워크 보안
- **CORS**: 허용된 도메인만 접근
- **Rate Limiting**: IP별 요청 제한
- **DDoS 보호**: CloudFlare 적용
- **방화벽**: VPC 보안 그룹 설정

### 6.5 모니터링 및 관찰성

#### 6.5.1 메트릭 수집
```
# 비즈니스 메트릭
mission_attempts_total{status="completed"} 1250
mission_attempts_total{status="failed"} 23
ai_evaluation_duration_seconds{quantile="0.5"} 25.3
ai_evaluation_duration_seconds{quantile="0.95"} 45.7
subscription_revenue_total{plan="business"} 79000

# 시스템 메트릭
http_requests_total{method="GET",endpoint="/api/v1/missions",status="200"} 15420
http_request_duration_seconds{quantile="0.95"} 0.8
kubernetes_pods_running{namespace="devtrip"} 45
database_connections_active 12
redis_memory_usage_bytes 536870912
```

#### 6.5.2 로깅 전략
```json
// 구조화된 로그 형식
{
  "timestamp": "2025-01-28T10:30:00.123Z",
  "level": "INFO",
  "service": "mission-management",
  "traceId": "trace-uuid",
  "spanId": "span-uuid",
  "userId": 12345,
  "missionId": 67890,
  "attemptId": "attempt-uuid",
  "event": "MISSION_STARTED",
  "message": "User started mission successfully",
  "context": {
    "podName": "mission-pod-12345",
    "containerImage": "ubuntu:22.04",
    "resourceLimits": {
      "cpu": "500m",
      "memory": "1Gi"
    }
  }
}
```

#### 6.5.3 알림 설정
- **Critical**: 서비스 다운, 데이터베이스 연결 실패
- **Warning**: 응답시간 임계치 초과, 리소스 사용량 높음
- **Info**: 배포 완료, 스케일링 이벤트

## 7. 제약사항 (Constraints)

### 7.1 기술적 제약사항
- **Java Version**: Java 17 이상 사용
- **Spring Boot**: 3.x 버전 사용
- **Database**: MySQL 8.0 이상
- **Container Runtime**: Kubernetes 1.24 이상
- **Cloud Provider**: AWS 우선, Multi-cloud 고려

### 7.2 비즈니스 제약사항
- **예산**: 월 $10,000 인프라 비용 한도
- **개발 기간**: 2025년 3월 1일 정식 출시
- **팀 규모**: 5명 개발팀
- **언어**: 한국어 우선, 영어 다국어 지원 고려

### 7.3 규정 및 표준
- **개인정보보호법**: 사용자 데이터 처리 규정 준수
- **PCI DSS**: 결제 정보 보안 표준 준수
- **GDPR**: 유럽 사용자 데이터 처리 규정 준수 (향후 대응)
- **ISO 27001**: 정보보안 관리체계 구축

## 8. 부록

### 8.1 주요 API 목록

#### Authentication Service
| 기능 | 메서드 | 엔드포인트 | 인증 필요 |
| --- | --- | --- | --- |
| 회원가입 | POST | `/api/v1/auth/signup` | ❌ |
| 로그인 | POST | `/api/v1/auth/login` | ❌ |
| 토큰 갱신 | POST | `/api/v1/auth/refresh` | ❌ |
| 현재 사용자 조회 | GET | `/api/v1/auth/me` | ✅ |
| 로그아웃 | POST | `/api/v1/auth/logout` | ✅ |
| Google OAuth | GET | `/api/v1/auth/oauth/google/login` | ❌ |
| Kakao OAuth | GET | `/api/v1/auth/oauth/kakao/login` | ❌ |

#### Mission Management Service
| 기능 | 메서드 | 엔드포인트 | 인증 필요 |
| --- | --- | --- | --- |
| 미션 목록 조회 | GET | `/api/v1/missions` | ✅ |
| 미션 상세 조회 | GET | `/api/v1/missions/{id}` | ✅ |
| 미션 시작 | POST | `/api/v1/missions/{id}/attempts` | ✅ |
| 미션 일시정지 | POST | `/api/v1/missions/{id}/pause` | ✅ |
| 미션 재개 | POST | `/api/v1/missions/{id}/resume` | ✅ |
| 터미널 연결 | WS | `/terminal/{attemptId}` | ✅ |

#### Payment Service
| 기능 | 메서드 | 엔드포인트 | 인증 필요 |
| --- | --- | --- | --- |
| 구독 플랜 조회 | GET | `/api/v1/subscription-plans` | ❌ |
| 구독 생성 | POST | `/api/v1/subscriptions` | ✅ |
| Stripe 결제 페이지 | POST | `/api/v1/subscriptions/checkout` | ✅ |
| 사용자 구독 조회 | GET | `/api/v1/subscriptions/users/{userId}` | ✅ |
| 티켓 조회 | GET | `/api/v1/tickets/users/{userId}` | ✅ |
| 티켓 사용 | POST | `/api/v1/tickets/users/{userId}/use` | ✅ |

### 8.2 환경별 설정

#### 개발 환경 (Development)
```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/devtrip_dev
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092

gemini:
  api:
    key: ${GEMINI_API_KEY_DEV}
    url: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent

oauth:
  jwt:
    secret: ${JWT_SECRET_DEV}
    access-token-expiration: 3600
  social:
    google:
      client-id: ${GOOGLE_CLIENT_ID_DEV}
      client-secret: ${GOOGLE_CLIENT_SECRET_DEV}
```

#### 프로덕션 환경 (Production)
```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://prod-mysql.cluster-xxx.amazonaws.com:3306/devtrip
    hikari:
      maximum-pool-size: 20
  redis:
    cluster:
      nodes: prod-redis.cluster.amazonaws.com:6379
  kafka:
    bootstrap-servers: prod-kafka.cluster.amazonaws.com:9092

gemini:
  api:
    key: ${GEMINI_API_KEY_PROD}
    timeout:
      connect-seconds: 30
      read-seconds: 120

oauth:
  jwt:
    secret: ${JWT_SECRET_PROD}
    access-token-expiration: 1800
  social:
    google:
      client-id: ${GOOGLE_CLIENT_ID_PROD}
      client-secret: ${GOOGLE_CLIENT_SECRET_PROD}

monitoring:
  prometheus:
    enabled: true
  tracing:
    enabled: true
    jaeger:
      endpoint: https://jaeger.devtrip.com
```

### 8.3 배포 전략

#### Blue-Green 배포
```yaml
# ArgoCD Application
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: devtrip-production
spec:
  project: devtrip
  source:
    repoURL: https://github.com/devtrip-project/devtrip-backend
    targetRevision: main
    path: k8s/overlays/production
  destination:
    server: https://kubernetes.default.svc
    namespace: devtrip-production
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
    - CreateNamespace=true
```

#### 롤링 업데이트 전략
```yaml
# Deployment
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    spec:
      containers:
      - name: devtrip-backend
        image: devtrip/backend:v1.0.0
        resources:
          requests:
            cpu: 500m
            memory: 1Gi
          limits:
            cpu: 2000m
            memory: 2Gi
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
```

---

## 검토 및 승인

**작성자:** DevTrip 개발팀

**검토자:** [검토자명 기재]

**승인자:** [승인자명 기재]

**문서 버전:** 1.0

**최종 수정일:** 2025-01-28

---

**이 문서는 DevTrip 프로젝트의 공식 요구사항 명세서입니다. 모든 개발 활동은 본 문서에 정의된 요구사항을 기반으로 수행되어야 합니다.**