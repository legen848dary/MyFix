#!/bin/bash
# =============================================================================
# local_rebuild_and_run.sh — Full clean rebuild + start the default local stack
# =============================================================================
# Usage:
#   ./scripts/local_rebuild_and_run.sh
#   ./scripts/local_rebuild_and_run.sh --with-demo-client [rate-per-second]
#   ./scripts/local_rebuild_and_run.sh [rate-per-second]
#
# Default behavior:
#   - rebuilds and starts the simulator only
#   - leaves the terminal FIX demo client stopped unless explicitly requested
#
# Demo-client rate precedence (only when explicitly enabled):
#   1. explicit positional [rate-per-second]
#   2. FIX_DEMO_RATE environment variable
#   3. default 500 msg/s
#
# Steps
#   1. local_clean_ledgers.sh              — stop services and remove ledger/runtime state
#   2. local_llexsim.sh rebuild            — purge, Gradle build, Docker image, start container
#   3. optional: local_fix_demo_client.sh run <rate>
#
# Backward compatibility:
#   Passing a numeric positional argument still enables the demo client and uses
#   that value as the rate, but the default no-argument path no longer starts it.
# =============================================================================

set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASH_BIN="/bin/bash"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'
CYAN=$'\033[0;36m'; BOLD=$'\033[1m'; RESET=$'\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
banner()  { echo -e "\n${BOLD}${CYAN}══════════════════════════════════════════${RESET}"; \
            echo -e "${BOLD}  $*${RESET}"; \
            echo -e "${BOLD}${CYAN}══════════════════════════════════════════${RESET}\n"; }

require_script() {
    local script_path="$1"
    if [[ ! -f "${script_path}" ]]; then
        error "Required script not found: ${script_path}"
        exit 1
    fi
}

usage() {
    cat << EOF

${BOLD}${CYAN}Rebuild + Run${RESET}

${BOLD}Usage:${RESET}
  ./scripts/local_rebuild_and_run.sh
  ./scripts/local_rebuild_and_run.sh --with-demo-client [rate-per-second]
  ./scripts/local_rebuild_and_run.sh [rate-per-second]

${BOLD}Default behavior:${RESET}
  Rebuilds and starts the simulator only. The terminal FIX demo client is ${BOLD}opt-in${RESET}.

${BOLD}Examples:${RESET}
  ./scripts/local_rebuild_and_run.sh
  ./scripts/local_rebuild_and_run.sh --with-demo-client
  ./scripts/local_rebuild_and_run.sh --with-demo-client 50
  ./scripts/local_rebuild_and_run.sh 50

EOF
}

RATE="${FIX_DEMO_RATE:-500}"
WITH_DEMO_CLIENT=false

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --with-demo-client)
                WITH_DEMO_CLIENT=true
                if [[ $# -gt 1 ]] && [[ "$2" =~ ^[0-9]+$ ]]; then
                    RATE="$2"
                    shift
                fi
                ;;
            help|--help|-h)
                usage
                exit 0
                ;;
            *)
                if [[ "$1" =~ ^[0-9]+$ ]] && [[ "$1" -gt 0 ]]; then
                    WITH_DEMO_CLIENT=true
                    RATE="$1"
                else
                    error "Unknown argument: '$1'"
                    usage
                    exit 1
                fi
                ;;
        esac
        shift
    done
}

validate_rate() {
    if ! [[ "${RATE}" =~ ^[0-9]+$ ]] || [[ "${RATE}" -le 0 ]]; then
        error "Rate must be a positive integer (messages/sec). Got: '${RATE}'"
        usage
        exit 1
    fi
}

parse_args "$@"

require_script "${SCRIPTS_DIR}/local_llexsim.sh"
require_script "${SCRIPTS_DIR}/local_clean_ledgers.sh"
if [[ "${WITH_DEMO_CLIENT}" == true ]]; then
    require_script "${SCRIPTS_DIR}/local_fix_demo_client.sh"
    validate_rate
fi

if [[ "${WITH_DEMO_CLIENT}" == true ]]; then
    banner "Rebuild + Run  (simulator + demo client @ ${RATE} msg/s)"
else
    banner "Rebuild + Run  (simulator only by default)"
fi

TOTAL_STEPS=2
if [[ "${WITH_DEMO_CLIENT}" == true ]]; then
    TOTAL_STEPS=3
fi

# ── Step 1: clean ledger/runtime state ────────────────────────────────────────
info "Step 1/${TOTAL_STEPS} — stopping services and cleaning ledgers/runtime state..."
"${BASH_BIN}" "${SCRIPTS_DIR}/local_clean_ledgers.sh"
success "Ledger/runtime state cleanup completed."

echo ""

# ── Step 2: full clean rebuild of the simulator ───────────────────────────────
info "Step 2/${TOTAL_STEPS} — rebuilding simulator (purge → build → start)..."
"${BASH_BIN}" "${SCRIPTS_DIR}/local_llexsim.sh" rebuild
success "Simulator is up with clean ledger/runtime state."

if [[ "${WITH_DEMO_CLIENT}" == true ]]; then
    echo ""
    info "Step 3/${TOTAL_STEPS} — starting demo FIX client at ${RATE} msg/s (foreground, Ctrl+C to stop)..."
    "${BASH_BIN}" "${SCRIPTS_DIR}/local_fix_demo_client.sh" run "${RATE}"
else
    echo ""
    info "Default startup completed without the terminal FIX demo client."
    info "Start it explicitly any time with: ./scripts/local_fix_demo_client.sh run ${RATE}"
fi

