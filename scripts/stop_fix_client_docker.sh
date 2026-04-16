#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
CONTAINER_NAME="thefixclient"
STOP_TIMEOUT=15

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Stop TheFixClient (Docker)${RESET}

Usage:
  ./scripts/stop_fix_client_docker.sh [options]

Options:
  --container-name NAME   Name of the running Docker container (default: thefixclient)
  --timeout SECONDS       Seconds to wait for graceful shutdown before SIGKILL (default: 15)
  -h, --help              Show this help message

Examples:
  ./scripts/stop_fix_client_docker.sh
  ./scripts/stop_fix_client_docker.sh --container-name my-client --timeout 30

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --container-name) CONTAINER_NAME="$2"; shift 2 ;;
        --timeout)        STOP_TIMEOUT="$2";   shift 2 ;;
        help|--help|-h)   show_help; exit 0    ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Stopping TheFixClient (Docker)"
require_docker

if ! docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    warn "Container '${CONTAINER_NAME}' not found — nothing to stop."
    exit 0
fi

info "Stopping container '${CONTAINER_NAME}' (timeout=${STOP_TIMEOUT}s)..."
docker stop --time "${STOP_TIMEOUT}" "${CONTAINER_NAME}" 2>/dev/null || true

info "Removing container '${CONTAINER_NAME}'..."
docker rm "${CONTAINER_NAME}" 2>/dev/null || true

success "TheFixClient container stopped and removed."
