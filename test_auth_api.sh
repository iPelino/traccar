#!/bin/bash

# Test script for Traccar authentication API endpoints
# This script tests the authentication API endpoints to verify that they return role and companyId

# Configuration
SERVER_URL="http://localhost:8082/api"
SESSION_ENDPOINT="$SERVER_URL/session"
USERNAME="admin"
PASSWORD="admin"

echo "Testing Traccar Authentication API Endpoints"
echo "============================================"

# Test 1: Login endpoint (POST /session)
echo "Test 1: Login endpoint (POST /session)"
echo "-------------------------------------"
echo "Sending POST request to $SESSION_ENDPOINT with username=$USERNAME and password=$PASSWORD"

LOGIN_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "email=$USERNAME&password=$PASSWORD" \
  -c cookies.txt \
  $SESSION_ENDPOINT)

echo "Response:"
echo $LOGIN_RESPONSE | jq .

# Check if role and companyId are in the response
if echo $LOGIN_RESPONSE | jq -e '.role' > /dev/null; then
  echo "✅ Role is present in the response"
else
  echo "❌ Role is missing from the response"
fi

if echo $LOGIN_RESPONSE | jq -e 'has("companyId")' > /dev/null; then
  echo "✅ CompanyId field is present in the response"
else
  echo "❌ CompanyId field is missing from the response"
fi

echo ""

# Test 2: Session info endpoint (GET /session)
echo "Test 2: Session info endpoint (GET /session)"
echo "-----------------------------------------"
echo "Sending GET request to $SESSION_ENDPOINT with session cookie"

SESSION_RESPONSE=$(curl -s -X GET \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  $SESSION_ENDPOINT)

echo "Response:"
echo $SESSION_RESPONSE | jq .

# Check if role and companyId are in the response
if echo $SESSION_RESPONSE | jq -e '.role' > /dev/null; then
  echo "✅ Role is present in the response"
else
  echo "❌ Role is missing from the response"
fi

if echo $SESSION_RESPONSE | jq -e 'has("companyId")' > /dev/null; then
  echo "✅ CompanyId field is present in the response"
else
  echo "❌ CompanyId field is missing from the response"
fi

echo ""

# Test 3: Logout endpoint (DELETE /session)
echo "Test 3: Logout endpoint (DELETE /session)"
echo "---------------------------------------"
echo "Sending DELETE request to $SESSION_ENDPOINT with session cookie"

LOGOUT_RESPONSE=$(curl -s -X DELETE \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -w "%{http_code}" \
  -o /dev/null \
  $SESSION_ENDPOINT)

echo "Response status code: $LOGOUT_RESPONSE"

if [ "$LOGOUT_RESPONSE" -eq 204 ]; then
  echo "✅ Logout successful (status code 204 No Content)"
else
  echo "❌ Logout failed (expected status code 204, got $LOGOUT_RESPONSE)"
fi

# Clean up
rm -f cookies.txt

echo ""
echo "Authentication API Testing Complete"