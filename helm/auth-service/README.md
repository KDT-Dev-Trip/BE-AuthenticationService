# Authentication Service Helm Chart

이 Helm 차트는 **Authentication Service + API Gateway** 기능을 제공하는 Spring Boot 애플리케이션을 Kubernetes에 배포하기 위한 차트입니다.

## 🎯 기능

- ✅ JWT 인증 및 인가
- ✅ API Gateway (Payment, User, Mission, AI 서비스 라우팅)
- ✅ Redis 기반 로그인 시도 추적
- ✅ 소셜 로그인 통합
- ✅ Prometheus 메트릭 및 헬스체크
- ✅ 보안 설정 (SecurityContext, RBAC)
- ✅ HPA (Horizontal Pod Autoscaler) 지원

## 🚀 설치

### 1. 기본 설치
```bash
helm install auth-service ./helm/auth-service
```

### 2. 커스텀 values로 설치
```bash
helm install auth-service ./helm/auth-service -f custom-values.yaml
```

### 3. 개발 환경 설치
```bash
helm install auth-service ./helm/auth-service \
  --set image.tag=latest \
  --set replicaCount=1 \
  --set resources.requests.memory=256Mi
```

## 📋 필수 사전 요구사항

배포하기 전에 다음 리소스들이 생성되어 있어야 합니다:

### 1. Secret 생성
```bash
kubectl create secret generic authentication-service-secrets \
  --from-literal=database-url="jdbc:mysql://mysql:3306/auth_db" \
  --from-literal=database-username="auth_user" \
  --from-literal=database-password="your-password" \
  --from-literal=redis-password="redis-password" \
  --from-literal=jwt-secret="your-jwt-secret-key"
```

### 2. 의존 서비스 확인
- MySQL 데이터베이스
- Redis 서버 
- Kafka 클러스터

## ⚙️ 설정 옵션

### 주요 Values

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.repository` | 이미지 레포지토리 | `auth-service` |
| `image.tag` | 이미지 태그 | `0.0.1` |
| `replicaCount` | Pod 복제본 수 | `2` |
| `service.port` | 서비스 포트 | `8080` |
| `ingress.enabled` | Ingress 활성화 | `false` |
| `autoscaling.enabled` | HPA 활성화 | `false` |

### 환경별 설정

#### Development
```yaml
# values-dev.yaml
replicaCount: 1
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
env:
  springProfilesActive: "dev"
  loggingLevelRoot: "DEBUG"
```

#### Production
```yaml
# values-prod.yaml
replicaCount: 3
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
env:
  springProfilesActive: "prod"
  loggingLevelRoot: "WARN"
```

## 🔍 모니터링

### Prometheus 메트릭
```bash
kubectl port-forward svc/auth-service 8080:8080
curl http://localhost:8080/actuator/prometheus
```

### 헬스체크
```bash
curl http://localhost:8080/actuator/health
```

### 로그 확인
```bash
kubectl logs -f deployment/auth-service
```

## 🛠 운영 명령어

### 업그레이드
```bash
# 이미지 태그 변경
helm upgrade auth-service ./helm/auth-service --set image.tag=0.0.2

# values 파일로 업그레이드
helm upgrade auth-service ./helm/auth-service -f values-prod.yaml
```

### 롤백
```bash
helm rollback auth-service 1
```

### 삭제
```bash
helm uninstall auth-service
```

### 상태 확인
```bash
helm status auth-service
helm get values auth-service
```

## 🔐 보안 설정

### SecurityContext
- `runAsNonRoot: true`
- `readOnlyRootFilesystem: true`
- `capabilities.drop: ALL`

### RBAC
ServiceAccount가 자동으로 생성되며 최소 권한으로 설정됩니다.

## 🎯 API Gateway 사용법

인증된 요청만 다른 마이크로서비스로 프록시됩니다:

```bash
# JWT 토큰 발급
TOKEN=$(curl -s "http://auth-service:8080/test/jwt-token" | jq -r .access_token)

# Payment Service 접근
curl -H "Authorization: Bearer $TOKEN" \
  "http://auth-service:8080/gateway/payment/api/v1/health"

# User Service 접근  
curl -H "Authorization: Bearer $TOKEN" \
  "http://auth-service:8080/gateway/user/api/users/123/profile"
```

## 📁 차트 구조

```
helm/auth-service/
├── Chart.yaml              # 차트 메타데이터
├── values.yaml              # 기본 설정값
├── README.md               # 이 파일
└── templates/
    ├── _helpers.tpl        # 템플릿 헬퍼
    ├── NOTES.txt          # 설치 후 안내
    ├── configmap.yaml     # ConfigMap
    ├── deployment.yaml    # Deployment
    ├── hpa.yaml          # HorizontalPodAutoscaler
    ├── ingress.yaml      # Ingress
    ├── service.yaml      # Service
    └── serviceaccount.yaml # ServiceAccount
```

## 🔄 다른 서비스와의 연동

이 차트는 다음 서비스들과 연동되도록 설계되었습니다:

- **Payment Service** (port 8081)
- **User Management Service** (port 8082)  
- **Mission Service** (port 8083)
- **AI Service** (port 8084)

각 서비스도 동일한 구조의 Helm 차트를 사용하여 독립적으로 배포할 수 있습니다.

## 🆘 문제 해결

### 일반적인 문제들

1. **Pod가 시작되지 않음**
   ```bash
   kubectl describe pod <pod-name>
   kubectl logs <pod-name>
   ```

2. **Secret이 없음**
   ```bash
   kubectl get secrets
   kubectl create secret generic authentication-service-secrets ...
   ```

3. **헬스체크 실패**
   - 데이터베이스 연결 확인
   - Redis 연결 확인
   - 환경변수 확인

---

이 차트를 사용하여 **독립적이고 안전한 마이크로서비스 배포**를 구현하세요! 🚀