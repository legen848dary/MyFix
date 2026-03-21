#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

if [[ "${1:-}" =~ ^(help|--help|-h)$ ]]; then
	cat << EOF

${BOLD}${CYAN}Stop Web Stack (Direct JVM)${RESET}

Usage:
  ./scripts/stop_web_stack.sh

Stops the host-JVM TheFixSimulator and TheFixClient processes started by ./scripts/start_web_stack.sh.

EOF
	exit 0
fi

banner "Stopping Web Stack (Direct JVM)"
ensure_runtime_dirs

stop_pidfile_process "TheFixClient" "${CLIENT_PID_FILE}"
stop_listener_on_port "TheFixClient" "${WEB_STACK_CLIENT_PORT}"

echo ""

stop_pidfile_process "TheFixSimulator" "${SIM_PID_FILE}"
stop_listener_on_port "TheFixSimulator" "${WEB_STACK_SIM_WEB_PORT}"
stop_listener_on_port "TheFixSimulator FIX acceptor" "${WEB_STACK_FIX_PORT}"

success "Direct JVM web stack stopped."

