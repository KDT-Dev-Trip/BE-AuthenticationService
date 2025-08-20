#!/bin/bash

echo "Testing Redis-based login attempt limiting..."
echo "========================================="

EMAIL="test@example.com"
BASE_URL="http://localhost:8080"

# Function to make login request
make_login_request() {
    local attempt=$1
    echo "Attempt $attempt: Making login request for $EMAIL..."
    
    response=$(curl -s -X POST "$BASE_URL/api/auth/local/login" \
        -H "Content-Type: application/json" \
        -H "X-Real-IP: 192.168.1.100" \
        -H "User-Agent: TestBot/1.0" \
        -d '{
            "email": "'$EMAIL'",
            "password": "wrongpassword123"
        }' \
        -w "HTTP_STATUS:%{http_code}")
    
    http_status=$(echo "$response" | grep -o "HTTP_STATUS:[0-9]*" | cut -d: -f2)
    body=$(echo "$response" | sed 's/HTTP_STATUS:[0-9]*$//')
    
    echo "  Status: $http_status"
    echo "  Response: $body"
    echo
}

# Make 12 failed login attempts to test the 10-attempt limit
for i in {1..12}; do
    make_login_request $i
    sleep 1
done

echo "Testing admin unlock functionality..."
echo "====================================="

# Test getting account lock info
echo "Getting account lock info..."
curl -s -X GET "$BASE_URL/admin/account/$EMAIL/lock-info" | jq '.'
echo

# Test unlocking the account
echo "Unlocking account..."
curl -s -X POST "$BASE_URL/admin/account/$EMAIL/unlock?adminUser=testadmin" \
    -H "Content-Type: application/json" | jq '.'
echo

# Test login after unlock
echo "Testing login after unlock..."
make_login_request "after_unlock"

# Get login attempt stats
echo "Getting login attempt statistics..."
curl -s -X GET "$BASE_URL/admin/login-attempts/stats" | jq '.'