#!/bin/bash

# DevTrip 서비스 부하 기반 자동 스케일링 스크립트
# 사용자 수에 따른 동적 서버 확장

set -euo pipefail

# 설정
NAMESPACE="devtrip"
METRICS_SERVER="prometheus:9090"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
EMAIL_RECIPIENTS="${EMAIL_RECIPIENTS:-devops@company.com}"

# 임계값 설정
CPU_THRESHOLD=70
MEMORY_THRESHOLD=80
ACTIVE_USERS_THRESHOLD=100
REQUEST_RATE_THRESHOLD=100  # requests/second

# 로깅
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1" >&2
}

# Slack 알림
send_slack_notification() {
    local message="$1"
    local color="${2:-warning}"
    
    if [[ -n "${SLACK_WEBHOOK}" ]]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"🚨 DevTrip Auto-Scaling Alert\", \"attachments\":[{\"color\":\"${color}\",\"text\":\"${message}\"}]}" \
            "${SLACK_WEBHOOK}"
    fi
}

# 메트릭 조회
get_metric() {
    local query="$1"
    curl -s "${METRICS_SERVER}/api/v1/query?query=${query}" | \
        jq -r '.data.result[0].value[1] // "0"'
}

# 현재 리플리카 수 조회
get_current_replicas() {
    local deployment="$1"
    kubectl get deployment "${deployment}" -n "${NAMESPACE}" -o jsonpath='{.spec.replicas}'
}

# 리플리카 수 업데이트
scale_deployment() {
    local deployment="$1"
    local replicas="$2"
    local current_replicas=$(get_current_replicas "${deployment}")
    
    if [[ "${current_replicas}" != "${replicas}" ]]; then
        log "Scaling ${deployment} from ${current_replicas} to ${replicas} replicas"
        kubectl scale deployment "${deployment}" -n "${NAMESPACE}" --replicas="${replicas}"
        
        # Slack 알림
        send_slack_notification "Scaled ${deployment}: ${current_replicas} → ${replicas} replicas" "good"
        
        # 이메일 알림
        if command -v mail &> /dev/null; then
            echo "DevTrip service ${deployment} scaled from ${current_replicas} to ${replicas} replicas at $(date)" | \
                mail -s "DevTrip Auto-Scaling Alert" "${EMAIL_RECIPIENTS}"
        fi
    fi
}

# 스케일링 결정 로직
calculate_required_replicas() {
    local service="$1"
    local current_replicas="$2"
    
    # CPU 사용률 조회 (예: rate(container_cpu_usage_seconds_total[5m]) * 100)
    local cpu_usage=$(get_metric "avg(rate(container_cpu_usage_seconds_total{namespace=\"${NAMESPACE}\",pod=~\"${service}.*\"}[5m])) * 100")
    
    # 메모리 사용률 조회
    local memory_usage=$(get_metric "avg(container_memory_working_set_bytes{namespace=\"${NAMESPACE}\",pod=~\"${service}.*\"} / container_spec_memory_limit_bytes{namespace=\"${NAMESPACE}\",pod=~\"${service}.*\"}) * 100")
    
    # 활성 사용자 수 조회 (가정: 커스텀 메트릭)
    local active_users=$(get_metric "active_users_total{service=\"${service}\"}")
    
    # 요청 처리율 조회
    local request_rate=$(get_metric "rate(http_requests_total{service=\"${service}\"}[5m])")
    
    log "Service: ${service} - CPU: ${cpu_usage}%, Memory: ${memory_usage}%, Users: ${active_users}, RPS: ${request_rate}"
    
    # 스케일링 결정
    local required_replicas=${current_replicas}
    
    # CPU 기반 스케일링
    if (( $(echo "${cpu_usage} > ${CPU_THRESHOLD}" | bc -l) )); then
        required_replicas=$((required_replicas + 1))
        log "CPU threshold exceeded for ${service}: ${cpu_usage}% > ${CPU_THRESHOLD}%"
    fi
    
    # 메모리 기반 스케일링
    if (( $(echo "${memory_usage} > ${MEMORY_THRESHOLD}" | bc -l) )); then
        required_replicas=$((required_replicas + 1))
        log "Memory threshold exceeded for ${service}: ${memory_usage}% > ${MEMORY_THRESHOLD}%"
    fi
    
    # 사용자 수 기반 스케일링
    if (( $(echo "${active_users} > ${ACTIVE_USERS_THRESHOLD}" | bc -l) )); then
        local user_based_replicas=$(echo "${active_users} / ${ACTIVE_USERS_THRESHOLD}" | bc)
        if [[ ${user_based_replicas} -gt ${required_replicas} ]]; then
            required_replicas=${user_based_replicas}
        fi
        log "Active users threshold exceeded for ${service}: ${active_users} > ${ACTIVE_USERS_THRESHOLD}"
    fi
    
    # 요청 처리율 기반 스케일링
    if (( $(echo "${request_rate} > ${REQUEST_RATE_THRESHOLD}" | bc -l) )); then
        local rps_based_replicas=$(echo "${request_rate} / ${REQUEST_RATE_THRESHOLD}" | bc)
        if [[ ${rps_based_replicas} -gt ${required_replicas} ]]; then
            required_replicas=${rps_based_replicas}
        fi
        log "Request rate threshold exceeded for ${service}: ${request_rate} > ${REQUEST_RATE_THRESHOLD}"
    fi
    
    # 최대/최소 리플리카 제한
    if [[ ${required_replicas} -gt 10 ]]; then
        required_replicas=10
    elif [[ ${required_replicas} -lt 1 ]]; then
        required_replicas=1
    fi
    
    echo ${required_replicas}
}

# 주요 서비스 목록
SERVICES=(
    "be-authentication-service"
    "be-ai-evaluation-service" 
    "be-payment-service"
    "be-mission-management-service"
    "be-user-management-service"
)

# 메인 스케일링 로직
main() {
    log "Starting DevTrip auto-scaling check..."
    
    # 네임스페이스 확인
    if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        error "Namespace ${NAMESPACE} not found"
        exit 1
    fi
    
    for service in "${SERVICES[@]}"; do
        log "Checking ${service}..."
        
        # 현재 리플리카 수 확인
        current_replicas=$(get_current_replicas "${service}")
        if [[ -z "${current_replicas}" ]] || [[ "${current_replicas}" == "0" ]]; then
            log "Warning: ${service} deployment not found or has 0 replicas"
            continue
        fi
        
        # 필요한 리플리카 수 계산
        required_replicas=$(calculate_required_replicas "${service}" "${current_replicas}")
        
        # 스케일링 실행
        scale_deployment "${service}" "${required_replicas}"
    done
    
    log "Auto-scaling check completed"
}

# 예외 처리
trap 'error "Script interrupted"; exit 1' INT TERM

# 스크립트 실행
main "$@"