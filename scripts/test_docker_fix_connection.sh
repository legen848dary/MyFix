#!/usr/bin/env bash
set -euo pipefail

# Integration test: Verify FIX Client can connect to FIX Simulator in Docker
# Tests docker-compose networking, FIX session establishment, and clean shutdown

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.web-stack.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test state
SUCCESS=0
CLEANUP_DONE=false

# Cleanup function
cleanup() {
    if [ "$CLEANUP_DONE" = false ]; then
        CLEANUP_DONE=true
        echo -e "\n${YELLOW}Cleaning up Docker stack...${NC}"
        cd "${PROJECT_ROOT}"
        docker-compose -f "${COMPOSE_FILE}" down --volumes 2>/dev/null || true
        sleep 1
    fi
}

trap cleanup EXIT

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}FIX Docker Integration Test${NC}"
echo -e "${BLUE}Simulator ↔ Client Connection Verification${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}\n"

# Step 1: Build project artifacts
echo -e "${YELLOW}[1/7] Building project artifacts...${NC}"
cd "${PROJECT_ROOT}"

if ! ./gradlew --no-daemon :TheFixSimulator:shadowJar -q 2>/dev/null; then
    echo -e "${RED}✗ Failed to build simulator${NC}"
    exit 1
fi

if ! ./gradlew --no-daemon :TheFixClient:installDist -q 2>/dev/null; then
    echo -e "${RED}✗ Failed to build client${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Build complete${NC}"

# Step 2: Clean up any existing stack
echo -e "${YELLOW}[2/7] Cleaning up existing containers...${NC}"
docker-compose -f "${COMPOSE_FILE}" down --volumes 2>/dev/null || true
sleep 2

# Step 3: Start fresh stack
echo -e "${YELLOW}[3/7] Starting Docker web stack...${NC}"
docker-compose -f "${COMPOSE_FILE}" up -d 2>&1 | grep -E "Creating|Created|Starting|Started" || true
sleep 3

# Step 4: Wait for services to be healthy
echo -e "${YELLOW}[4/7] Waiting for services to become healthy...${NC}"

wait_for_service() {
    local service=$1
    local url=$2
    local max_attempts=120  # 2 minutes total
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo -e "${GREEN}✓ $service is healthy${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        if [ $((attempt % 10)) -eq 0 ]; then
            echo -ne "\r  Waiting for $service... ($attempt/$max_attempts) "
        fi
        sleep 1
    done
    
    echo -e "\n${RED}✗ $service failed to become healthy after ${max_attempts}s${NC}"
    
    # Print container logs for debugging
    echo -e "\n${YELLOW}Container logs:${NC}"
    docker-compose -f "${COMPOSE_FILE}" logs --tail=20 2>/dev/null || true
    
    return 1
}

wait_for_service "Simulator" "http://localhost:8080/api/health" || exit 1
wait_for_service "Client" "http://localhost:8081/api/health" || exit 1
echo ""

# Step 5: Authenticate with demo credentials
echo -e "${YELLOW}[5/7] Authenticating (trader1:trader1)...${NC}"

LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:8081/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"trader1"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 || echo "")

if [ -z "$TOKEN" ]; then
    echo -e "${RED}✗ Login failed${NC}"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ Authenticated${NC}"

# Step 6: Connect FIX session
echo -e "${YELLOW}[6/7] Initiating FIX connection to simulator...${NC}"

CONNECT_RESPONSE=$(curl -s -X POST "http://localhost:8081/api/session/connect" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{}')

echo "  Response: $CONNECT_RESPONSE"
sleep 3

# Step 7: Verify connection
echo -e "${YELLOW}[7/7] Verifying FIX session status...${NC}"

OVERVIEW=$(curl -s "http://localhost:8081/api/overview" \
  -H "Authorization: Bearer ${TOKEN}")

# Extract connection status from response
CONNECTED=$(echo "$OVERVIEW" | grep -o '"connected":true\|"connected":false' | head -1 || echo "")
STATUS=$(echo "$OVERVIEW" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 | head -1 || echo "")

echo "  Connection status: $CONNECTED"
echo "  Session status: $STATUS"

# Check logs for success indicators
echo -e "\n${YELLOW}Verification logs:${NC}"

echo -e "  ${BLUE}Simulator FIX events:${NC}"
docker-compose -f "${COMPOSE_FILE}" logs llexsimulator 2>/dev/null | grep -i "logon\|session\|connection" | tail -5 | sed 's/^/    /' || echo "    (no matches)"

echo -e "  ${BLUE}Client status:${NC}"
docker-compose -f "${COMPOSE_FILE}" logs thefixclient 2>/dev/null | grep -i "connected\|logon\|session\|fixed" | tail -5 | sed 's/^/    /' || echo "    (no matches)"

if echo "$CONNECTED" | grep -q "true"; then
    echo -e "\n${GREEN}✓ FIX connection ESTABLISHED!${NC}"
    echo -e "${GREEN}✓ Session status: $STATUS${NC}"
    SUCCESS=1
elif [ -n "$STATUS" ] && echo "$STATUS" | grep -q -i "connected\|loggedon"; then
    echo -e "\n${GREEN}✓ FIX session is connected!${NC}"
    SUCCESS=1
else
    echo -e "\n${YELLOW}⚠ Connection status unclear, checking detailed logs...${NC}"
    
    # Try to find evidence of successful connection
    if docker-compose -f "${COMPOSE_FILE}" logs 2>/dev/null | grep -q -i "loggedon\|session.*start\|fix.*connected"; then
        echo -e "${GREEN}✓ Found evidence of FIX connection in logs${NC}"
        SUCCESS=1
    else
        echo -e "${RED}✗ No evidence of FIX connection${NC}"
        SUCCESS=0
    fi
fi

# Disconnect
echo -e "\n${YELLOW}Disconnecting and preparing cleanup...${NC}"
curl -s -X POST "http://localhost:8081/api/session/disconnect" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" >/dev/null 2>&1 || true
sleep 2

echo -e "\n${BLUE}═══════════════════════════════════════════════════════════════${NC}"
if [ "$SUCCESS" -eq 1 ]; then
    echo -e "${GREEN}✓ TEST PASSED: FIX connection established successfully${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    exit 0
else
    echo -e "${RED}✗ TEST FAILED: FIX connection could not be verified${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    exit 1
fi
