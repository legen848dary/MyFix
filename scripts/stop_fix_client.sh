#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
CLIENT_PORT="${WEB_STACK_CLIENT_PORT}"

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Stop TheFixClient (Direct JVM)${RESET}

Usage:
  ./scripts/stop_fix_client.sh [options]

Options:
  --port PORT   HTTP / REST API port used when the client was started (default: 8081)
  -h, --help    Show this help message

Examples:
  ./scripts/stop_fix_client.sh
  ./scripts/stop_fix_client.sh --port 8082

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port) CLIENT_PORT="$2"; shift 2 ;;
        help|--help|-h) show_help; exit 0 ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Stopping TheFixClient (Direct JVM)"
ensure_runtime_dirs

stop_pidfile_process "TheFixClient" "${CLIENT_PID_FILE}"
stop_listener_on_port "TheFixClient" "${CLIENT_PORT}"

success "TheFixClient stopped."
