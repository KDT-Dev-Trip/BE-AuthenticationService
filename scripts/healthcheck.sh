#!/bin/sh

# ==================================================
# Health Check Script for Authentication Service
# ==================================================

set -e

# 설정
HOST=${HOST:-localhost}
PORT=${PORT:-8080}
TIMEOUT=${TIMEOUT:-30}
MAX_RETRIES=${MAX_RETRIES:-3}

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 로깅 함수
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') [HEALTHCHECK] $1"
}

log_error() {
    echo "${RED}$(date '+%Y-%m-%d %H:%M:%S') [ERROR] $1${NC}" >&2
}

log_success() {
    echo "${GREEN}$(date '+%Y-%m-%d %H:%M:%S') [SUCCESS] $1${NC}"
}

log_warning() {
    echo "${YELLOW}$(date '+%Y-%m-%d %H:%M:%S') [WARNING] $1${NC}"
}

# 헬스체크 함수
check_endpoint() {
    local endpoint=$1
    local description=$2
    local expected_status=${3:-200}
    
    log "Checking $description..."
    
    response=$(curl -s -w "HTTPSTATUS:%{http_code}" \
        --max-time $TIMEOUT \
        --fail-with-body \
        "http://${HOST}:${PORT}${endpoint}" 2>/dev/null || echo "HTTPSTATUS:000")
    
    http_code=$(echo "$response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo "$response" | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$http_code" -eq "$expected_status" ]; then
        log_success "$description is healthy (HTTP $http_code)"
        return 0
    else
        log_error "$description failed (HTTP $http_code)"
        if [ -n "$body" ] && [ "$body" != "HTTPSTATUS:$http_code" ]; then
            log_error "Response: $body"
        fi
        return 1
    fi
}

# JSON 파싱 함수 (jq가 없는 환경용)
parse_json_value() {
    local json=$1
    local key=$2
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed 's/.*"\([^"]*\)"/\1/'
}

# 종합 헬스체크 함수
comprehensive_health_check() {
    log "Starting comprehensive health check..."
    
    local failed_checks=0
    
    # 1. 기본 헬스체크
    if ! check_endpoint "/actuator/health" "Spring Boot Actuator Health"; then
        failed_checks=$((failed_checks + 1))
    fi
    
    # 2. 애플리케이션 헬스체크
    if ! check_endpoint "/auth/health" "Authentication Service Health"; then
        failed_checks=$((failed_checks + 1))
    fi
    
    # 3. SSO 서비스 헬스체크
    if ! check_endpoint "/sso/status" "SSO Service Status"; then
        failed_checks=$((failed_checks + 1))
    fi
    
    # 4. OAuth 서비스 테스트 (간단한 엔드포인트)
    if ! check_endpoint "/auth/test" "OAuth Service Test"; then
        failed_checks=$((failed_checks + 1))
    fi
    
    # 5. 메트릭 엔드포인트 확인
    if ! check_endpoint "/actuator/metrics" "Metrics Endpoint"; then
        log_warning "Metrics endpoint failed, but continuing..."
    fi
    
    # 6. 데이터베이스 연결 확인 (간접적)
    log "Checking database connectivity..."
    response=$(curl -s --max-time $TIMEOUT "http://${HOST}:${PORT}/actuator/health" 2>/dev/null || echo "")
    
    if echo "$response" | grep -q '"db"'; then
        db_status=$(parse_json_value "$response" "status")
        if [ "$db_status" = "UP" ]; then
            log_success "Database connectivity is healthy"
        else
            log_error "Database connectivity issues detected"
            failed_checks=$((failed_checks + 1))
        fi
    else
        log_warning "Could not determine database status"
    fi
    
    # 7. Redis 연결 확인 (간접적)
    log "Checking Redis connectivity..."
    if echo "$response" | grep -q '"redis"'; then
        redis_status=$(parse_json_value "$response" "status")
        if [ "$redis_status" = "UP" ]; then
            log_success "Redis connectivity is healthy"
        else
            log_error "Redis connectivity issues detected"
            failed_checks=$((failed_checks + 1))
        fi
    else
        log_warning "Could not determine Redis status"
    fi
    
    # 결과 판정
    if [ $failed_checks -eq 0 ]; then
        log_success "All health checks passed!"
        return 0
    elif [ $failed_checks -le 2 ]; then
        log_warning "$failed_checks checks failed, but service may still be functional"
        return 1
    else
        log_error "$failed_checks checks failed, service is unhealthy"
        return 1
    fi
}

# 재시도 로직이 있는 메인 함수
main() {
    log "Starting health check for Authentication Service"
    log "Target: http://${HOST}:${PORT}"
    log "Timeout: ${TIMEOUT}s, Max retries: ${MAX_RETRIES}"
    
    for retry in $(seq 1 $MAX_RETRIES); do
        log "Health check attempt $retry of $MAX_RETRIES"
        
        if comprehensive_health_check; then
            log_success "Health check completed successfully"
            exit 0
        else
            if [ $retry -lt $MAX_RETRIES ]; then
                log_warning "Health check failed, retrying in 5 seconds..."
                sleep 5
            else
                log_error "Health check failed after $MAX_RETRIES attempts"
                exit 1
            fi
        fi
    done
}

# 스크립트 실행
main "$@"