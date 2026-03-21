#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

if [[ "${1:-}" =~ ^(help|--help|-h)$ ]]; then
	cat << EOF

${BOLD}${CYAN}Start Web Stack (Docker)${RESET}

Usage:
  ./scripts/start_web_stack_docker.sh

Starts the containerized TheFixSimulator and TheFixClient together using Docker Compose.

EOF
	exit 0
fi

banner "Starting Web Stack (Docker)"
require_gradle_wrapper
source_runtime_profile_if_available
require_compose

if docker_stack_running; then
	success "The Docker web stack is already running."
	echo -e "  ${BOLD}TheFixSimulator${RESET} → http://localhost:${WEB_STACK_SIM_WEB_PORT}"
	echo -e "  ${BOLD}TheFixClient${RESET}   → http://localhost:${WEB_STACK_CLIENT_PORT}"
	exit 0
fi

assert_web_stack_ports_available

mkdir -p "${WEB_STACK_SIM_LOG_DIR}" "${WEB_STACK_CLIENT_LOG_DIR}"
cd "${PROJECT_ROOT}"

info "Building runtime artifacts for Docker images..."
"${GRADLEW_BIN}" --no-daemon :TheFixSimulator:shadowJar :TheFixClient:installDist -x :TheFixSimulator:test -x :TheFixClient:test

info "Building and starting Docker services..."
compose_cmd up -d --build --force-recreate

wait_for_http "TheFixSimulator" "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" 90
wait_for_http "TheFixClient" "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health" 90

echo ""
success "Docker web stack is running."
echo -e "  ${BOLD}TheFixSimulator${RESET} → http://localhost:${WEB_STACK_SIM_WEB_PORT}"
echo -e "  ${BOLD}TheFixClient${RESET}   → http://localhost:${WEB_STACK_CLIENT_PORT}"
echo -e "  ${BOLD}FIX Acceptor${RESET}    → tcp://localhost:${WEB_STACK_FIX_PORT}"


