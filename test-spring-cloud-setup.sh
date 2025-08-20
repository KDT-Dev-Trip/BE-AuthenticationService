#!/bin/bash

echo "🧪 Spring Cloud Gateway + Eureka 테스트"
echo "====================================="

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 테스트 결과
PASSED=0
FAILED=0

test_service() {
    local service_name="$1"
    local url="$2"
    local expected_status="$3"
    
    echo -e "\n${BLUE}테스트: $service_name${NC}"
    
    response=$(curl -s -w "\n%{http_code}" "$url")
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "$expected_status" ]; then
        echo -e "✅ ${GREEN}PASS${NC} - HTTP $status"
        ((PASSED++))
    else
        echo -e "❌ ${RED}FAIL${NC} - Expected: $expected_status, Got: $status"
        ((FAILED++))
    fi
    
    echo "$body"
}

echo -e "\n${YELLOW}=== 1. Eureka Server 상태 확인 ===${NC}"
test_service "Eureka Server" "http://localhost:8761" "200"

echo -e "\n${YELLOW}=== 2. 서비스 등록 확인 ===${NC}"
echo "Eureka Dashboard에서 다음 서비스들이 등록되었는지 확인:"
echo "- AUTHENTICATION-SERVICE (localhost:8081)"
echo "- PAYMENT-SERVICE (localhost:8082)"
echo "- API-GATEWAY (localhost:8080)"

curl -s "http://localhost:8761/eureka/apps" | grep -o '<name>[^<]*</name>' | sort | uniq

echo -e "\n${YELLOW}=== 3. 인증 서비스 직접 접근 ===${NC}"
test_service "Authentication Service Health" "http://localhost:8081/actuator/health" "200"

echo -e "\n${YELLOW}=== 4. Payment Service 직접 접근 ===${NC}"
test_service "Payment Service Health" "http://localhost:8082/api/v1/health" "200"

echo -e "\n${YELLOW}=== 5. Gateway를 통한 인증 ===${NC}"
login_data='{"email":"test@example.com","password":"password123"}'
login_response=$(curl -s -X POST -H "Content-Type: application/json" -d "$login_data" "http://localhost:8080/auth/login")
jwt_token=$(echo "$login_response" | jq -r '.accessToken // empty')

if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
    echo -e "✅ ${GREEN}Gateway 인증 성공${NC}"
    echo "JWT Token: ${jwt_token:0:50}..."
    ((PASSED++))
else
    echo -e "❌ ${RED}Gateway 인증 실패${NC}"
    echo "Response: $login_response"
    ((FAILED++))
fi

echo -e "\n${YELLOW}=== 6. Gateway를 통한 서비스 라우팅 ===${NC}"
if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
    auth_header="Authorization: Bearer $jwt_token"
    
    # Payment Service를 Gateway를 통해 접근
    test_service "Gateway → Payment Service" "http://localhost:8080/gateway/payment/api/v1/health" "200"
    
    # 사용자 정보 조회
    user_id=$(echo $jwt_token | jq -R 'split(".") | .[1] | @base64d' | jq -r '.sub // empty' 2>/dev/null)
    if [ -z "$user_id" ] || [ "$user_id" = "null" ]; then
        user_id="ef4b8906-2ea8-4f10-b1e7-fa63dd242475"
    fi
    
    echo -e "\n${BLUE}사용자 ID: $user_id${NC}"
    
    # 인증된 사용자의 티켓 조회
    echo -e "\n${BLUE}Gateway를 통한 인증된 API 호출${NC}"
    response=$(curl -s -w "\n%{http_code}" -H "$auth_header" "http://localhost:8080/gateway/payment/api/v1/tickets/users/$user_id")
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "200" ] || [ "$status" = "404" ]; then
        echo -e "✅ ${GREEN}PASS${NC} - 인증된 요청 처리됨 (HTTP $status)"
        ((PASSED++))
    else
        echo -e "❌ ${RED}FAIL${NC} - Expected: 200 or 404, Got: $status"
        ((FAILED++))
    fi
    
    echo "$body"
else
    echo -e "❌ ${RED}JWT 토큰 없음 - 서비스 라우팅 테스트 건너뜀${NC}"
    ((FAILED++))
fi

echo -e "\n${YELLOW}====================================="
echo "🧪 Spring Cloud Gateway + Eureka 테스트 결과"
echo "=====================================${NC}"
echo -e "✅ ${GREEN}통과: $PASSED${NC}"
echo -e "❌ ${RED}실패: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "\n🎉 ${GREEN}모든 테스트 통과! Spring Cloud Gateway + Eureka 설정이 완료되었습니다.${NC}"
    echo ""
    echo -e "${BLUE}🔗 서비스 구성:${NC}"
    echo "1. ✅ Eureka Server (http://localhost:8761)"
    echo "2. ✅ API Gateway (http://localhost:8080)"
    echo "3. ✅ Authentication Service (http://localhost:8081)"
    echo "4. ✅ Payment Service (http://localhost:8082)"
    echo ""
    echo -e "${BLUE}🌐 Gateway 라우팅:${NC}"
    echo "- /auth/** → authentication-service"
    echo "- /gateway/payment/** → payment-service"
    echo ""
    echo -e "${BLUE}📋 Eureka Dashboard: http://localhost:8761${NC}"
    exit 0
else
    echo -e "\n⚠️ ${YELLOW}일부 테스트 실패. 서비스 상태를 확인해주세요.${NC}"
    exit 1
fi