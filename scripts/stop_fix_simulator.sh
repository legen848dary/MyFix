#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
SIM_WEB_PORT="${WEB_STACK_SIM_WEB_PORT}"
SIM_FIX_PORT="${WEB_STACK_FIX_PORT}"

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Stop TheFixSimulator (Direct JVM)${RESET}

Usage:
  ./scripts/stop_fix_simulator.sh [options]

Options:
  --web-port PORT   Web / REST API port used when the simulator was started (default: 8080)
  --fix-port PORT   FIX acceptor TCP port used when the simulator was started (default: 9880)
  -h, --help        Show this help message

Examples:
  ./scripts/stop_fix_simulator.sh
  ./scripts/stop_fix_simulator.sh --web-port 9090 --fix-port 9881

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --web-port) SIM_WEB_PORT="$2"; shift 2 ;;
        --fix-port) SIM_FIX_PORT="$2"; shift 2 ;;
        help|--help|-h) show_help; exit 0 ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Stopping TheFixSimulator (Direct JVM)"
ensure_runtime_dirs

stop_pidfile_process "TheFixSimulator" "${SIM_PID_FILE}"
stop_listener_on_port "TheFixSimulator web"         "${SIM_WEB_PORT}"
stop_listener_on_port "TheFixSimulator FIX acceptor" "${SIM_FIX_PORT}"

success "TheFixSimulator stopped."
