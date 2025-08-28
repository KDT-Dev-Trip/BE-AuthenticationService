# DevTrip MSA Kafka 이벤트 테스트 가이드

## 개요
이 문서는 DevTrip MSA 서비스들의 Kafka 이벤트 플로우를 테스트하는 방법을 상세히 설명합니다.

## 서비스별 이벤트 테스트 시나리오

### 1. Authentication Service (포트: 8080)

#### 1.1 회원가입 이벤트 테스트

**시나리오**: 회원가입 → `auth-events` 토픽 발행 → User/Payment 서비스 동기화

```bash
# Step 1: 회원가입 API 호출
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@devtrip.com",
    "password": "password123",
    "name": "테스트 사용자"
  }'

# Step 2: Kafka 이벤트 확인 (Kafka UI)
# Topic: auth-events
# Event Type: UserSignedUpEvent
# Expected Payload:
{
  "eventType": "user.signed_up",
  "userId": "generated-user-id",
  "email": "test@devtrip.com",
  "timestamp": "2024-01-15T10:30:00Z",
  "ipAddress": "127.0.0.1"
}
```

**검증 포인트**:
- [ ] Auth Service: 사용자 생성 성공
- [ ] User Service: `auth-events` 이벤트 수신 및 사용자 프로필 생성
- [ ] Payment Service: `auth-events` 이벤트 수신 및 결제 계정 생성

#### 1.2 로그인 이벤트 테스트

```bash
# Step 1: 로그인 API 호출
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@devtrip.com",
    "password": "password123"
  }'

# Step 2: Kafka 이벤트 확인
# Topic: auth-events
# Event Type: UserLoggedInEvent
```

### 2. Payment Service (포트: 8081)

#### 2.1 결제 완료 이벤트 테스트

**시나리오**: 결제 진행 → `payment-events` 토픽 발행 → Auth/User 서비스 동기화

```bash
# Step 1: 결제 생성
curl -X POST http://localhost:8081/api/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "amount": 29000,
    "planType": "BASIC",
    "orderName": "DevTrip 기본 플랜",
    "successUrl": "http://localhost:3000/payment/success",
    "failUrl": "http://localhost:3000/payment/fail"
  }'

# Step 2: 결제 승인 (토스페이먼츠)
curl -X POST http://localhost:8081/api/payments/toss/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "paymentKey": "PAYMENT_KEY_FROM_TOSS",
    "orderId": "ORDER_ID",
    "amount": 29000
  }'

# Step 3: Kafka 이벤트 확인
# Topic: payment-events
# Event Type: payment.completed
{
  "eventType": "payment.completed",
  "paymentId": "payment-123",
  "userId": "user-456",
  "amount": 29000,
  "planType": "BASIC",
  "status": "COMPLETED",
  "timestamp": "2024-01-15T11:00:00Z"
}
```

**검증 포인트**:
- [ ] Payment Service: 결제 완료 처리
- [ ] Auth Service: 사용자 권한 업데이트 (BASIC 플랜)
- [ ] User Service: 사용자 구독 상태 변경

#### 2.2 구독 업그레이드 이벤트 테스트

```bash
# 플랜 업그레이드
curl -X POST http://localhost:8081/api/payments/upgrade \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "targetPlan": "PREMIUM",
    "amount": 49000
  }'

# Topic: subscription-events
# Event Type: plan.upgraded
```

### 3. Mission Management Service (포트: 8083)

#### 3.1 미션 완료 이벤트 테스트

**시나리오**: 미션 완료 → `mission-events` 토픽 발행 → AI Evaluation 서비스 평가 시작

```bash
# Step 1: 미션 시작
curl -X POST http://localhost:8083/api/missions/1/start \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Step 2: 미션 완료 제출
curl -X POST http://localhost:8083/api/missions/1/complete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "code": "console.log(\"Hello, DevTrip!\");",
    "language": "javascript",
    "executionTime": 1500
  }'

# Step 3: Kafka 이벤트 확인
# Topic: mission-events
# Event Type: mission.completed
{
  "eventType": "mission.completed",
  "missionAttemptId": "attempt-789",
  "userId": "user-456",
  "missionId": "mission-1",
  "code": "console.log(\"Hello, DevTrip!\");",
  "completedAt": "2024-01-15T12:00:00Z",
  "executionTime": 1500
}
```

**검증 포인트**:
- [ ] Mission Service: 미션 완료 상태 업데이트
- [ ] AI Evaluation Service: `mission-events` 이벤트 수신 및 평가 시작
- [ ] User Service: 사용자 미션 진행 통계 업데이트

#### 3.2 리소스 프로비저닝 이벤트 테스트

```bash
# 사용자 전용 쿠버네티스 환경 생성
curl -X POST http://localhost:8083/api/missions/1/resources \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Topic: mission-events  
# Event Type: resource.provisioning-failed (실패시)
# Event Type: resource.cleanup-completed (정리시)
```

### 4. AI Evaluation Service (포트: 8084)

#### 4.1 AI 평가 완료 이벤트 테스트

**시나리오**: AI 평가 완료 → `evaluation-events` 토픽 발행 → Mission 서비스 결과 업데이트

```bash
# AI 평가는 자동으로 진행되지만, 테스트용 직접 호출도 가능
curl -X POST http://localhost:8084/api/evaluations \
  -H "Content-Type: application/json" \
  -d '{
    "missionAttemptId": "attempt-789",
    "userId": "user-456",
    "missionId": "mission-1",
    "code": "console.log(\"Hello, DevTrip!\");",
    "language": "javascript"
  }'

# Topic: evaluation-events
# Event Type: evaluation.completed
{
  "eventType": "evaluation.completed",
  "evaluationId": "eval-999",
  "missionAttemptId": "attempt-789",
  "userId": "user-456",
  "score": 85,
  "maxScore": 100,
  "feedback": "Good implementation, consider error handling",
  "details": {
    "syntax": 95,
    "logic": 80,
    "performance": 85,
    "bestPractices": 80
  },
  "timestamp": "2024-01-15T12:05:00Z"
}
```

**검증 포인트**:
- [ ] AI Evaluation Service: 평가 완료 및 결과 저장
- [ ] Mission Service: `evaluation-events` 이벤트 수신 및 미션 결과 업데이트
- [ ] Auth Service: 사용자 평가 통계 업데이트

### 5. User Management Service (포트: 8082)

#### 5.1 사용자 정보 업데이트 이벤트 테스트

```bash
# 사용자 프로필 업데이트
curl -X PUT http://localhost:8082/api/users/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "nickname": "DevTripper",
    "bio": "코딩을 사랑하는 개발자",
    "skills": ["JavaScript", "Python", "Docker"]
  }'

# Topic: user-events
# Event Type: user.profile_updated
```

## 전체 통합 시나리오 테스트

### 시나리오: 신규 사용자 가입부터 첫 미션 완료까지

```bash
# 1. 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "newbie@devtrip.com", "password": "test123", "name": "신입개발자"}'

# 2. 로그인
LOGIN_RESPONSE=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "newbie@devtrip.com", "password": "test123"}')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

# 3. 결제 진행 (기본 플랜)
curl -X POST http://localhost:8081/api/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"amount": 29000, "planType": "BASIC", "orderName": "DevTrip 기본 플랜"}'

# 4. 첫 번째 미션 시작
curl -X POST http://localhost:8083/api/missions/1/start \
  -H "Authorization: Bearer $TOKEN"

# 5. 미션 완료 제출
curl -X POST http://localhost:8083/api/missions/1/complete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code": "function hello() { return \"Hello, DevTrip!\"; }", "language": "javascript"}'
```

**예상되는 Kafka 이벤트 시퀀스**:
1. `auth-events`: UserSignedUpEvent
2. `auth-events`: UserLoggedInEvent  
3. `payment-events`: PaymentCompletedEvent
4. `subscription-events`: SubscriptionCreatedEvent
5. `mission-events`: MissionStartedEvent
6. `mission-events`: MissionCompletedEvent
7. `evaluation-events`: EvaluationStartedEvent
8. `evaluation-events`: EvaluationCompletedEvent

## 개발자 도구를 활용한 이벤트 모니터링

### 1. Kafka UI 사용 (http://localhost:8079)
- 실시간 토픽 메시지 확인
- Consumer Group 상태 모니터링
- Message 수동 발행/확인

### 2. 서비스 로그 모니터링
```bash
# 실시간 로그 확인
docker-compose logs -f auth-service
docker-compose logs -f payment-service  
docker-compose logs -f mission-service
docker-compose logs -f ai-evaluation-service
docker-compose logs -f user-management-service

# Kafka 관련 로그만 필터링
docker-compose logs -f auth-service | grep -i kafka
```

### 3. 헬스체크를 통한 서비스 상태 확인
```bash
# 각 서비스 헬스체크
curl http://localhost:8080/actuator/health  # Auth
curl http://localhost:8081/actuator/health  # Payment  
curl http://localhost:8082/actuator/health  # User Management
curl http://localhost:8083/actuator/health  # Mission
curl http://localhost:8084/actuator/health  # AI Evaluation
```

## 자동화된 통합 테스트

### JUnit을 활용한 Kafka 이벤트 테스트
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class KafkaEventIntegrationTest {

    @Test
    void 회원가입_이벤트_플로우_테스트() {
        // Given: 신규 사용자 정보
        SignupRequest signupRequest = new SignupRequest("test@example.com", "password");
        
        // When: 회원가입 API 호출
        authService.signup(signupRequest);
        
        // Then: Kafka 이벤트 발행 확인
        await().atMost(Duration.ofSeconds(10))
               .until(() -> userService.findByEmail("test@example.com").isPresent());
        
        // And: 결제 서비스에서 계정 생성 확인  
        await().atMost(Duration.ofSeconds(10))
               .until(() -> paymentService.findAccountByEmail("test@example.com").isPresent());
    }
}
```

## 트러블슈팅 가이드

### 자주 발생하는 문제들

#### 1. 이벤트가 발행되지 않는 경우
```bash
# Kafka 브로커 상태 확인
docker ps | grep kafka
docker-compose logs kafka

# 토픽 존재 여부 확인
kafka-topics --list --bootstrap-server localhost:9092
```

#### 2. Consumer가 이벤트를 처리하지 않는 경우
```bash
# Consumer Group 상태 확인
kafka-consumer-groups --bootstrap-server localhost:9092 --list
kafka-consumer-groups --bootstrap-server localhost:9092 --group auth-service-group --describe
```

#### 3. 이벤트 처리 지연
```bash
# Consumer Lag 확인
kafka-consumer-groups --bootstrap-server localhost:9092 --group auth-service-group --describe

# 파티션 수 증가 (필요시)
kafka-topics --alter --topic auth-events --partitions 3 --bootstrap-server localhost:9092
```

## 성능 테스트

### 대용량 이벤트 처리 테스트
```bash
# 100명의 동시 회원가입 시뮬레이션
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/auth/signup \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"user$i@test.com\", \"password\": \"test123\", \"name\": \"User $i\"}" &
done

# Consumer Lag 모니터링
watch -n 1 "kafka-consumer-groups --bootstrap-server localhost:9092 --group auth-service-group --describe"
```

이 가이드를 통해 DevTrip MSA의 모든 Kafka 이벤트 플로우를 체계적으로 테스트할 수 있습니다.