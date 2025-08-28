#!/bin/bash

# DevTrip MSA 전체 서비스 실행 스크립트
# 사용법: ./start-all-services.sh [options]
# 옵션:
#   --infrastructure-only : 인프라만 실행
#   --services-only      : 서비스만 실행 (인프라는 이미 실행중이어야 함)
#   --service=<name>     : 특정 서비스만 실행 (auth, payment, user, mission, ai)

set -e

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로깅 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 서비스 경로 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

# 서비스 정보 배열
declare -A SERVICES=(
    ["auth"]="BE-authentication-service:8080"
    ["payment"]="BE-payment-service:8081"
    ["user"]="BE-user-management-service:8082"
    ["mission"]="BE-mission-management-service:8083"
    ["ai"]="BE-AI-evaluation-service:8084"
)

# 헬스체크 함수
wait_for_service() {
    local name=$1
    local url=$2
    local max_attempts=30
    local attempt=1

    log_step "Waiting for $name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$url" > /dev/null 2>&1; then
            log_info "$name is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    log_error "$name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# 인프라 시작 함수
start_infrastructure() {
    log_step "Starting DevTrip Infrastructure..."
    
    cd "$SCRIPT_DIR"
    
    # 기존 인프라 정리
    docker-compose -f docker-compose.infrastructure.yml down
    
    # 인프라 실행
    docker-compose -f docker-compose.infrastructure.yml up -d
    
    # 헬스체크
    log_step "Waiting for infrastructure services to be ready..."
    
    # MySQL 헬스체크
    wait_for_service "MySQL" "mysql://devtrip_user:devtrip_password@localhost:3306" || {
        log_error "MySQL 연결 실패. docker logs devtrip-mysql 로 로그를 확인하세요."
        return 1
    }
    
    # Redis 헬스체크  
    wait_for_service "Redis" "redis://localhost:6379" || {
        log_error "Redis 연결 실패. docker logs devtrip-redis 로 로그를 확인하세요."
        return 1
    }
    
    # Kafka 헬스체크
    sleep 10  # Kafka 초기화 대기
    if docker exec devtrip-kafka kafka-broker-api-versions --bootstrap-server localhost:29092 > /dev/null 2>&1; then
        log_info "Kafka is ready!"
    else
        log_error "Kafka 연결 실패. docker logs devtrip-kafka 로 로그를 확인하세요."
        return 1
    fi
    
    log_info "모든 인프라 서비스가 준비되었습니다!"
    log_info "Kafka UI: http://localhost:8079"
    log_info "Prometheus: http://localhost:9090" 
    log_info "Grafana: http://localhost:3000 (admin/devtrip_admin)"
}

# 개별 서비스 시작 함수
start_service() {
    local service_key=$1
    local service_info=${SERVICES[$service_key]}
    local service_dir=$(echo $service_info | cut -d':' -f1)
    local service_port=$(echo $service_info | cut -d':' -f2)
    
    local full_path="$BASE_DIR/$service_dir"
    
    if [ ! -d "$full_path" ]; then
        log_error "Service directory not found: $full_path"
        return 1
    fi
    
    log_step "Starting $service_key service ($service_dir)..."
    
    cd "$full_path"
    
    # Gradle 또는 Maven으로 서비스 시작
    if [ -f "gradlew" ]; then
        # 백그라운드에서 실행
        nohup ./gradlew bootRun --args='--spring.profiles.active=local' > "${service_key}.log" 2>&1 &
        echo $! > "${service_key}.pid"
    elif [ -f "mvnw" ]; then
        nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > "${service_key}.log" 2>&1 &
        echo $! > "${service_key}.pid"
    else
        log_error "$service_key: gradlew 또는 mvnw 파일을 찾을 수 없습니다."
        return 1
    fi
    
    # 서비스 준비 대기
    wait_for_service "$service_key" "http://localhost:$service_port/actuator/health" || {
        log_error "$service_key 서비스 시작 실패. ${service_key}.log 파일을 확인하세요."
        return 1
    }
    
    log_info "$service_key service started successfully on port $service_port"
}

# 모든 서비스 시작 함수
start_all_services() {
    log_step "Starting all DevTrip services..."
    
    # 서비스 의존성 순서대로 시작
    local service_order=("auth" "user" "payment" "mission" "ai")
    
    for service in "${service_order[@]}"; do
        start_service "$service" || {
            log_error "$service 서비스 시작 실패"
            return 1
        }
        sleep 5  # 서비스 간 시작 간격
    done
    
    log_info "모든 DevTrip 서비스가 성공적으로 시작되었습니다!"
    echo ""
    log_info "서비스 URL 목록:"
    log_info "  Authentication (API Gateway): http://localhost:8080"
    log_info "  Payment Service: http://localhost:8081"
    log_info "  User Management: http://localhost:8082"
    log_info "  Mission Management: http://localhost:8083"
    log_info "  AI Evaluation: http://localhost:8084"
}

# 서비스 중지 함수
stop_all_services() {
    log_step "Stopping all DevTrip services..."
    
    # 실행 중인 서비스 프로세스 찾아서 종료
    for service_key in "${!SERVICES[@]}"; do
        local service_info=${SERVICES[$service_key]}
        local service_dir=$(echo $service_info | cut -d':' -f1)
        local pid_file="$BASE_DIR/$service_dir/${service_key}.pid"
        
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                log_info "Stopping $service_key service (PID: $pid)..."
                kill "$pid"
                rm -f "$pid_file"
            fi
        fi
    done
    
    # 인프라 중지
    cd "$SCRIPT_DIR"
    docker-compose -f docker-compose.infrastructure.yml down
    
    log_info "모든 서비스가 중지되었습니다."
}

# 서비스 상태 확인 함수
check_status() {
    log_step "DevTrip Services Status Check"
    echo ""
    
    # 인프라 상태 확인
    log_info "Infrastructure Status:"
    docker-compose -f "$SCRIPT_DIR/docker-compose.infrastructure.yml" ps
    echo ""
    
    # 서비스 상태 확인
    log_info "Application Services Status:"
    for service_key in "${!SERVICES[@]}"; do
        local service_info=${SERVICES[$service_key]}
        local service_port=$(echo $service_info | cut -d':' -f2)
        local service_dir=$(echo $service_info | cut -d':' -f1)
        local pid_file="$BASE_DIR/$service_dir/${service_key}.pid"
        
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                if curl -f -s "http://localhost:$service_port/actuator/health" > /dev/null 2>&1; then
                    log_info "  $service_key: ✅ RUNNING (PID: $pid, Port: $service_port)"
                else
                    log_warn "  $service_key: ⚠️  STARTING (PID: $pid, Port: $service_port)"
                fi
            else
                log_error "  $service_key: ❌ STOPPED (Stale PID file)"
                rm -f "$pid_file"
            fi
        else
            log_error "  $service_key: ❌ STOPPED"
        fi
    done
}

# 사용법 출력
show_usage() {
    echo "DevTrip MSA Services Management Script"
    echo ""
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  start                    Start infrastructure and all services"
    echo "  stop                     Stop all services and infrastructure"
    echo "  restart                  Restart all services"
    echo "  status                   Check status of all services"
    echo "  infrastructure           Start only infrastructure"
    echo "  services                 Start only application services"
    echo ""
    echo "Options:"
    echo "  --service=<name>         Start specific service only (auth, payment, user, mission, ai)"
    echo "  --help                   Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start                 # Start everything"
    echo "  $0 infrastructure        # Start only infrastructure"
    echo "  $0 start --service=auth  # Start only auth service"
    echo "  $0 status                # Check service status"
}

# 메인 실행 로직
main() {
    local command=${1:-"help"}
    local specific_service=""
    
    # 파라미터 파싱
    for arg in "$@"; do
        case $arg in
            --service=*)
                specific_service="${arg#*=}"
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
        esac
    done
    
    case $command in
        start)
            if [ -n "$specific_service" ]; then
                if [[ -n "${SERVICES[$specific_service]}" ]]; then
                    start_service "$specific_service"
                else
                    log_error "Unknown service: $specific_service"
                    log_info "Available services: ${!SERVICES[*]}"
                    exit 1
                fi
            else
                start_infrastructure
                start_all_services
            fi
            ;;
        stop)
            stop_all_services
            ;;
        restart)
            stop_all_services
            sleep 5
            start_infrastructure
            start_all_services
            ;;
        status)
            check_status
            ;;
        infrastructure)
            start_infrastructure
            ;;
        services)
            start_all_services
            ;;
        help|--help)
            show_usage
            ;;
        *)
            log_error "Unknown command: $command"
            show_usage
            exit 1
            ;;
    esac
}

# 스크립트 실행
main "$@"