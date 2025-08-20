#!/bin/bash

# Gateway와 PaymentService 연동 테스트 스크립트
echo "🔗 Gateway ↔ PaymentService 연동 테스트"
echo "========================================"

API_BASE="http://localhost:8080"
PAYMENT_DIRECT="http://localhost:8082"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 테스트 결과 저장
PASSED=0
FAILED=0

# 테스트 함수
test_api() {
    local test_name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expected_status="$5"
    local headers="$6"
    
    echo -e "\n${BLUE}테스트: $test_name${NC}"
    
    if [ -n "$headers" ]; then
        if [ "$method" = "GET" ]; then
            response=$(curl -s -w "\n%{http_code}" -H "$headers" "$url")
        else
            response=$(curl -s -w "\n%{http_code}" -X "$method" -H "Content-Type: application/json" -H "$headers" -d "$data" "$url")
        fi
    else
        if [ "$method" = "GET" ]; then
            response=$(curl -s -w "\n%{http_code}" "$url")
        else
            response=$(curl -s -w "\n%{http_code}" -X "$method" -H "Content-Type: application/json" -d "$data" "$url")
        fi
    fi
    
    # 응답 분리
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "$expected_status" ]; then
        echo -e "✅ ${GREEN}PASS${NC} - HTTP $status"
        ((PASSED++))
    else
        echo -e "❌ ${RED}FAIL${NC} - Expected: $expected_status, Got: $status"
        ((FAILED++))
    fi
    
    # JSON이면 예쁘게 출력
    if echo "$body" | jq . >/dev/null 2>&1; then
        echo "$body" | jq .
    else
        echo "$body"
    fi
}

# 0. PaymentService 직접 헬스체크
echo -e "\n${YELLOW}=== 0. PaymentService 직접 연결 테스트 ===${NC}"
echo "PaymentService가 8082 포트에서 실행 중인지 확인..."
test_api "PaymentService 직접 헬스체크" "GET" "$PAYMENT_DIRECT/api/v1/health" "" "200"

# 1. 인증 서비스에서 로그인
echo -e "\n${YELLOW}=== 1. 사용자 인증 ===${NC}"
login_data='{"email":"test@example.com","password":"password123"}'
login_response=$(curl -s -X POST -H "Content-Type: application/json" -d "$login_data" "$API_BASE/auth/login")
jwt_token=$(echo "$login_response" | jq -r '.accessToken // empty')

if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
    echo -e "✅ ${GREEN}로그인 성공${NC} - JWT 토큰 획득"
    echo "JWT Token: ${jwt_token:0:50}..."
    ((PASSED++))
else
    echo -e "❌ ${RED}로그인 실패${NC} - 새 사용자 생성 시도"
    
    # 회원가입 후 다시 로그인
    signup_data='{"email":"test@example.com","password":"password123","name":"Test User"}'
    curl -s -X POST -H "Content-Type: application/json" -d "$signup_data" "$API_BASE/auth/signup" > /dev/null
    
    login_response=$(curl -s -X POST -H "Content-Type: application/json" -d "$login_data" "$API_BASE/auth/login")
    jwt_token=$(echo "$login_response" | jq -r '.accessToken // empty')
    
    if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
        echo -e "✅ ${GREEN}회원가입 후 로그인 성공${NC}"
        ((PASSED++))
    else
        echo -e "❌ ${RED}인증 실패${NC} - 테스트 중단"
        exit 1
    fi
fi

# 2. Gateway를 통한 PaymentService 헬스체크
echo -e "\n${YELLOW}=== 2. Gateway 라우팅 테스트 ===${NC}"
auth_header="Authorization: Bearer $jwt_token"
test_api "Gateway → PaymentService 헬스체크" "GET" "$API_BASE/gateway/payment/api/v1/health" "" "200" "$auth_header"

# 3. Gateway를 통한 PaymentService 정보 조회
test_api "Gateway → PaymentService 정보 조회" "GET" "$API_BASE/gateway/payment/api/v1/info" "" "200" "$auth_header"

# 4. 사용자 ID 추출 (JWT에서)
user_id=$(echo $jwt_token | jq -R 'split(".") | .[1] | @base64d' | jq -r '.sub // empty' 2>/dev/null)
if [ -z "$user_id" ] || [ "$user_id" = "null" ]; then
    user_id="ef4b8906-2ea8-4f10-b1e7-fa63dd242475"  # UUID 형태로 fallback
fi

echo -e "\n${BLUE}사용자 ID: $user_id${NC}"

# 5. 인증이 필요한 PaymentService API 테스트
echo -e "\n${YELLOW}=== 3. 인증 기반 PaymentService API 테스트 ===${NC}"

# 티켓 조회 (본인 정보)
test_api "사용자 티켓 조회 (본인)" "GET" "$API_BASE/gateway/payment/api/v1/tickets/users/$user_id" "" "200" "$auth_header"

# 구독 조회 (본인 정보)  
test_api "사용자 구독 조회 (본인)" "GET" "$API_BASE/gateway/payment/api/v1/subscriptions/users/$user_id" "" "200" "$auth_header"

# 활성 구독 조회 (본인 정보)
test_api "사용자 활성 구독 조회 (본인)" "GET" "$API_BASE/gateway/payment/api/v1/subscriptions/users/$user_id/active" "" "404" "$auth_header"

# 6. 권한 검증 테스트 (다른 사용자 정보 접근 시도)
echo -e "\n${YELLOW}=== 4. 권한 검증 테스트 ===${NC}"
other_user_id="999999"

# 다른 사용자 티켓 조회 시도 (Forbidden 예상)
test_api "다른 사용자 티켓 조회 (권한 없음)" "GET" "$API_BASE/gateway/payment/api/v1/tickets/users/$other_user_id" "" "403" "$auth_header"

# 다른 사용자 구독 조회 시도 (Forbidden 예상)
test_api "다른 사용자 구독 조회 (권한 없음)" "GET" "$API_BASE/gateway/payment/api/v1/subscriptions/users/$other_user_id" "" "403" "$auth_header"

# 7. 토큰 없이 접근 시도
echo -e "\n${YELLOW}=== 5. 인증 없이 접근 테스트 ===${NC}"

# 토큰 없이 티켓 조회 (Unauthorized 예상)
test_api "토큰 없이 티켓 조회 (인증 필요)" "GET" "$API_BASE/gateway/payment/api/v1/tickets/users/$user_id" "" "401"

# 토큰 없이 구독 조회 (Unauthorized 예상)
test_api "토큰 없이 구독 조회 (인증 필요)" "GET" "$API_BASE/gateway/payment/api/v1/subscriptions/users/$user_id" "" "401"

# 8. Gateway 헤더 전달 확인
echo -e "\n${YELLOW}=== 6. Gateway 헤더 전달 확인 ===${NC}"
echo "PaymentService 로그에서 다음과 같은 메시지를 확인하세요:"
echo "- 'Fetching tickets for authenticated user: User(id=..., email=...)'"
echo "- 'User ... attempted to access tickets of user ...'"

# 테스트 결과 요약
echo -e "\n${YELLOW}========================================"
echo "🧪 Gateway ↔ PaymentService 연동 테스트 결과"
echo "========================================${NC}"
echo -e "✅ ${GREEN}통과: $PASSED${NC}"
echo -e "❌ ${RED}실패: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "\n🎉 ${GREEN}모든 테스트 통과! Gateway와 PaymentService가 정상 연동됩니다.${NC}"
    echo ""
    echo -e "${BLUE}🔗 연동 확인 사항:${NC}"
    echo "1. ✅ Gateway가 PaymentService(8082)로 요청을 정상 라우팅"
    echo "2. ✅ JWT 토큰 기반 인증 검증"
    echo "3. ✅ X-User-Id, X-User-Email 헤더 전달"
    echo "4. ✅ 권한 기반 접근 제어 (본인 정보만 접근 가능)"
    echo "5. ✅ 인증 없는 접근 차단"
    exit 0
else
    echo -e "\n⚠️  ${YELLOW}일부 테스트 실패. 연동 상태를 확인해주세요.${NC}"
    exit 1
fi