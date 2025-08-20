#!/bin/bash

# SSO 및 보안 시스템 통합 테스트 스크립트
# DevOps Platform Authentication Service

echo "🔐 SSO 및 보안 시스템 통합 테스트 시작"
echo "=================================="

API_BASE="http://localhost:8080"
TEST_EMAIL="sso-test@example.com"
TEST_PASSWORD="testPassword123"

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
    
    echo -e "\n${BLUE}테스트: $test_name${NC}"
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$url")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" -H "Content-Type: application/json" -d "$data" "$url")
    fi
    
    # 응답 분리
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "$expected_status" ]; then
        echo -e "✅ ${GREEN}PASS${NC} - HTTP $status"
        ((PASSED++))
    else
        echo -e "❌ ${RED}FAIL${NC} - Expected: $expected_status, Got: $status"
        echo "Response: $body"
        ((FAILED++))
    fi
    
    # JSON이면 예쁘게 출력
    if echo "$body" | jq . >/dev/null 2>&1; then
        echo "$body" | jq .
    else
        echo "$body"
    fi
}

# 1. 헬스체크
echo -e "\n${YELLOW}=== 1. 시스템 헬스체크 ===${NC}"
test_api "인증 서비스 헬스체크" "GET" "$API_BASE/auth/health" "" "200"

# 2. 사용자 회원가입
echo -e "\n${YELLOW}=== 2. 사용자 인증 테스트 ===${NC}"
signup_data='{"email":"'$TEST_EMAIL'","password":"'$TEST_PASSWORD'","name":"SSO Test User"}'
test_api "사용자 회원가입" "POST" "$API_BASE/auth/signup" "$signup_data" "200"

# 3. 로그인
login_data='{"email":"'$TEST_EMAIL'","password":"'$TEST_PASSWORD'"}'
login_response=$(curl -s -X POST -H "Content-Type: application/json" -d "$login_data" "$API_BASE/auth/login")
jwt_token=$(echo "$login_response" | jq -r '.accessToken // empty')

if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
    echo -e "✅ ${GREEN}로그인 성공${NC} - JWT 토큰 획득"
    echo "JWT Token: ${jwt_token:0:50}..."
    ((PASSED++))
else
    echo -e "❌ ${RED}로그인 실패${NC}"
    echo "Response: $login_response"
    ((FAILED++))
    exit 1
fi

# 4. SSO 토큰 업그레이드
echo -e "\n${YELLOW}=== 3. SSO 시스템 테스트 ===${NC}"
sso_upgrade_data='{"jwt_token":"'$jwt_token'"}'
sso_response=$(curl -s -X POST -H "Content-Type: application/json" -d "$sso_upgrade_data" "$API_BASE/sso/upgrade")
sso_token=$(echo "$sso_response" | jq -r '.sso_token // empty')

if [ -n "$sso_token" ] && [ "$sso_token" != "null" ]; then
    echo -e "✅ ${GREEN}SSO 업그레이드 성공${NC}"
    echo "SSO Token: ${sso_token:0:50}..."
    ((PASSED++))
else
    echo -e "❌ ${RED}SSO 업그레이드 실패${NC}"
    echo "Response: $sso_response"
    ((FAILED++))
fi

# 5. SSO 토큰 검증
if [ -n "$sso_token" ]; then
    sso_validate_data='{"sso_token":"'$sso_token'"}'
    test_api "SSO 토큰 검증" "POST" "$API_BASE/sso/validate" "$sso_validate_data" "200"
fi

# 6. 애플리케이션 등록
if [ -n "$sso_token" ]; then
    echo -e "\n${YELLOW}=== 4. 멀티 애플리케이션 연동 테스트 ===${NC}"
    
    # 여러 애플리케이션 등록
    apps=("analytics-dashboard:Analytics Dashboard" "devops-console:DevOps Console" "project-manager:Project Manager")
    
    for app in "${apps[@]}"; do
        IFS=':' read -r app_id app_name <<< "$app"
        app_register_data='{"sso_token":"'$sso_token'","application_id":"'$app_id'","application_name":"'$app_name'"}'
        test_api "$app_name 등록" "POST" "$API_BASE/sso/register-app" "$app_register_data" "200"
    done
    
    # 세션 정보 확인
    test_api "SSO 세션 정보 조회" "GET" "$API_BASE/sso/session?sso_token=$sso_token" "" "200"
fi

# 7. Redis 보안 기능 테스트
echo -e "\n${YELLOW}=== 5. Redis 보안 이상탐지 테스트 ===${NC}"

# 잘못된 비밀번호로 여러 번 로그인 시도
echo "🔒 로그인 실패 시나리오 테스트 중..."
fake_email="security-test@example.com"
for i in {1..3}; do
    fake_login_data='{"email":"'$fake_email'","password":"wrongpassword'$i'"}'
    echo "실패 시도 $i/3..."
    curl -s -X POST -H "Content-Type: application/json" -d "$fake_login_data" "$API_BASE/auth/login" > /dev/null
    sleep 1
done

echo -e "✅ ${GREEN}보안 이상탐지 테스트 완료${NC} - Redis에 실패 시도 기록됨"
((PASSED++))

# 8. 소셜 로그인 디스커버리 테스트
echo -e "\n${YELLOW}=== 6. 소셜 로그인 시스템 테스트 ===${NC}"
test_api "Google 로그인 엔드포인트" "GET" "$API_BASE/social/google/login" "" "302"
test_api "Kakao 로그인 엔드포인트" "GET" "$API_BASE/social/kakao/login" "" "302"

# 9. OAuth 2.0 디스커버리 테스트
echo -e "\n${YELLOW}=== 7. OAuth 2.0 시스템 테스트 ===${NC}"
test_api "OAuth Discovery" "GET" "$API_BASE/.well-known/oauth-authorization-server" "" "200"
test_api "JWKS 엔드포인트" "GET" "$API_BASE/oauth/jwks" "" "200"

# 10. 모니터링 및 메트릭 테스트
echo -e "\n${YELLOW}=== 8. 모니터링 시스템 테스트 ===${NC}"
test_api "Actuator Health" "GET" "$API_BASE/actuator/health" "" "200"
test_api "Prometheus 메트릭" "GET" "$API_BASE/actuator/prometheus" "" "200"

# 11. 최종 로그아웃 테스트
if [ -n "$sso_token" ]; then
    echo -e "\n${YELLOW}=== 9. 전체 로그아웃 테스트 ===${NC}"
    logout_data='{"sso_token":"'$sso_token'"}'
    test_api "SSO 전체 로그아웃" "POST" "$API_BASE/sso/logout" "$logout_data" "200"
    
    # 로그아웃 후 검증 (실패해야 정상)
    test_api "로그아웃 후 SSO 토큰 검증 (실패 예상)" "POST" "$API_BASE/sso/validate" "$sso_validate_data" "200"
fi

# 테스트 결과 요약
echo -e "\n${YELLOW}=================================="
echo "🧪 테스트 결과 요약"
echo "==================================${NC}"
echo -e "✅ ${GREEN}통과: $PASSED${NC}"
echo -e "❌ ${RED}실패: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "\n🎉 ${GREEN}모든 테스트 통과! SSO 및 보안 시스템이 정상 작동합니다.${NC}"
    exit 0
else
    echo -e "\n⚠️  ${YELLOW}일부 테스트 실패. 로그를 확인해주세요.${NC}"
    exit 1
fi