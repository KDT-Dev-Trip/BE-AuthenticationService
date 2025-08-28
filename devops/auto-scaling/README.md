# DevTrip 자동 스케일링 가이드

## 🎯 개요
DevTrip 서비스의 사용자 증가에 따른 자동 서버 확장 시스템

## 📁 디렉토리 구조
```
devops/
├── auto-scaling/
│   ├── cluster-autoscaler.yaml      # 클러스터 노드 자동 확장
│   ├── load-based-scaling.sh        # 부하 기반 Pod 스케일링 스크립트
│   └── README.md                    # 이 문서
├── monitoring/
│   └── custom-metrics.yaml         # 커스텀 메트릭 및 알람 설정
└── jenkins/
    └── auto-scaling-pipeline.yaml   # 자동 스케일링 CI/CD 파이프라인
```

## 🚀 설치 및 설정

### 1. Cluster AutoScaler 설치
```bash
# EKS 클러스터에 Cluster AutoScaler 배포
kubectl apply -f devops/auto-scaling/cluster-autoscaler.yaml

# IAM 정책 연결 (AWS EKS의 경우)
aws iam attach-role-policy \
  --role-name eks-cluster-autoscaler \
  --policy-arn arn:aws:iam::aws:policy/AutoScalingFullAccess
```

### 2. 커스텀 메트릭 설정
```bash
# Prometheus 커스텀 메트릭 적용
kubectl apply -f devops/monitoring/custom-metrics.yaml
```

### 3. 자동 스케일링 스크립트 실행
```bash
# 권한 설정
chmod +x devops/auto-scaling/load-based-scaling.sh

# 수동 실행
./devops/auto-scaling/load-based-scaling.sh

# Cron으로 주기적 실행 (2분마다)
echo "*/2 * * * * /path/to/devtrip/devops/auto-scaling/load-based-scaling.sh" | crontab -
```

## 📊 스케일링 조건

### HPA (Horizontal Pod Autoscaler)
- **CPU 사용률**: 70% 초과시 확장
- **메모리 사용률**: 80% 초과시 확장
- **최소 리플리카**: 2개
- **최대 리플리카**: 10개

### 커스텀 스케일링 조건
- **활성 사용자 수**: 100명 초과시 확장
- **API 요청 처리율**: 100 RPS 초과시 확장
- **응답 시간**: 2초 초과시 확장

## 🔧 설정 커스터마이징

### 환경 변수
```bash
export SLACK_WEBHOOK="https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK"
export EMAIL_RECIPIENTS="devops@company.com,admin@company.com"
export NAMESPACE="devtrip"
export METRICS_SERVER="prometheus:9090"
```

### 임계값 수정
`load-based-scaling.sh` 파일에서 다음 값들을 수정:
```bash
CPU_THRESHOLD=70                    # CPU 임계값 (%)
MEMORY_THRESHOLD=80                 # 메모리 임계값 (%)
ACTIVE_USERS_THRESHOLD=100          # 활성 사용자 임계값
REQUEST_RATE_THRESHOLD=100          # 요청 처리율 임계값 (RPS)
```

## 📈 모니터링 대시보드

### Grafana 대시보드 추가
1. **CPU/Memory 사용률**: Kubernetes Pod 메트릭
2. **활성 사용자 수**: 커스텀 메트릭 `devtrip:active_users_total`
3. **API 응답 시간**: HTTP 요청 지연 시간
4. **스케일링 이벤트**: Pod 확장/축소 이력

### 주요 메트릭 쿼리
```promql
# 서비스별 CPU 사용률
avg(rate(container_cpu_usage_seconds_total{namespace="devtrip"}[5m])) by (pod) * 100

# 활성 사용자 수
sum(devtrip:active_users_total)

# API 응답 시간 95퍼센타일
histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{namespace="devtrip"}[5m])) by (le, service))
```

## 🚨 알람 설정

### Slack 알림
- 스케일링 이벤트 발생시 자동 알림
- CPU/메모리 임계값 초과시 경고

### 이메일 알림
- 중요한 스케일링 이벤트시 이메일 발송
- 시스템 장애시 즉시 알림

## 🔄 CI/CD 통합

### Jenkins Pipeline 통합
```groovy
stage('Auto-Scaling Check') {
    steps {
        sh './devops/auto-scaling/load-based-scaling.sh'
    }
}
```

### GitOps 연동
ArgoCD를 통해 스케일링 정책 변경시 자동 반영

## 📝 로그 및 디버깅

### 로그 확인
```bash
# Cluster AutoScaler 로그
kubectl logs -n kube-system deployment/cluster-autoscaler

# HPA 상태 확인
kubectl get hpa -n devtrip

# Pod 리소스 사용량 확인
kubectl top pods -n devtrip
```

### 디버깅 모드
```bash
# 자세한 로그와 함께 스크립트 실행
DEBUG=1 ./devops/auto-scaling/load-based-scaling.sh
```

## ⚠️ 주의사항

1. **비용 최적화**: 최대 리플리카 수를 적절히 설정하여 비용 증가 방지
2. **리소스 제한**: 각 Pod의 리소스 제한을 명확히 설정
3. **모니터링**: 스케일링 동작을 지속적으로 모니터링하고 임계값 조정
4. **테스트**: 운영 환경 적용 전 스테이징 환경에서 충분한 테스트

## 🔧 문제 해결

### 일반적인 문제들
- **메트릭 수집 실패**: Prometheus 연결 확인
- **권한 오류**: RBAC 설정 확인  
- **스케일링 지연**: HPA 정책 및 쿨다운 시간 조정
- **과도한 스케일링**: 임계값 및 알고리즘 검토