#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

if [[ "${1:-}" =~ ^(help|--help|-h)$ ]]; then
	cat << EOF

${BOLD}${CYAN}Stop Web Stack (Docker)${RESET}

Usage:
  ./scripts/stop_web_stack_docker.sh

Stops the containerized TheFixSimulator and TheFixClient Docker Compose stack.

EOF
	exit 0
fi

banner "Stopping Web Stack (Docker)"
source_runtime_profile_if_available
require_compose
cd "${PROJECT_ROOT}"

info "Stopping Docker services..."
compose_cmd down --remove-orphans

success "Docker web stack stopped."


