#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"
# shellcheck source=./native_runtime_targets.sh
source "${SCRIPT_DIR}/native_runtime_targets.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
CLIENT_PORT="${WEB_STACK_CLIENT_PORT}"
FIX_HOST="127.0.0.1"
FIX_PORT="${WEB_STACK_FIX_PORT}"
HEAP_XMS=""
HEAP_XMX=""
CPU_PINNING=""
NO_BUILD=0
RAW_LOGGING="${THEFIX_FIX_RAW_LOGGING_ENABLED:-false}"

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Start TheFixClient (Direct JVM)${RESET}

Usage:
  ./scripts/start_fix_client.sh [options]

Options:
  --port PORT            TheFixClient HTTP / REST API port       (default: 8081)
  --fix-host HOST        FIX simulator hostname or IP            (default: 127.0.0.1)
  --fix-port PORT        FIX simulator TCP port                  (default: 9880)
  --heap-xms SIZE        JVM initial heap size, e.g. 256m / 1g  (default: 256m)
  --heap-xmx SIZE        JVM max heap size, e.g. 512m / 1g      (default: 512m)
  --cpu-pinning CPUS     CPU affinity via taskset, e.g. 4-7      (default: none)
  --no-build             Skip Gradle build (reuse existing distribution)
  --raw-logging          Enable raw FIX message logging           (default: false)
  -h, --help             Show this help message

Environment Variables (all overridable via flags above):
  WEB_STACK_CLIENT_PORT          Client HTTP port       (default: 8081)
  WEB_STACK_FIX_PORT             FIX port               (default: 9880)
  THEFIX_CLIENT_JAVA_XMS         Initial heap           (default: 256m)
  THEFIX_CLIENT_JAVA_XMX         Max heap               (default: 512m)
  THEFIX_FIX_RAW_LOGGING_ENABLED Raw FIX logging toggle (default: false)

Examples:
  # Start with defaults (simulator expected on 127.0.0.1:9880)
  ./scripts/start_fix_client.sh

  # Connect to a remote simulator
  ./scripts/start_fix_client.sh --fix-host 192.168.1.10 --fix-port 9881

  # Custom port, skip build
  ./scripts/start_fix_client.sh --port 8082 --no-build

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)        CLIENT_PORT="$2";  shift 2 ;;
        --fix-host)    FIX_HOST="$2";     shift 2 ;;
        --fix-port)    FIX_PORT="$2";     shift 2 ;;
        --heap-xms)    HEAP_XMS="$2";     shift 2 ;;
        --heap-xmx)    HEAP_XMX="$2";     shift 2 ;;
        --cpu-pinning) CPU_PINNING="$2";  shift 2 ;;
        --no-build)    NO_BUILD=1;        shift   ;;
        --raw-logging) RAW_LOGGING="true"; shift  ;;
        help|--help|-h) show_help; exit 0 ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Starting TheFixClient (Direct JVM)"
require_java
require_gradle_wrapper
source_runtime_profile_if_available
native_runtime_targets_load

HEAP_XMS="${HEAP_XMS:-${FIXCLIENT_TARGET_HEAP_XMS:-${THEFIX_CLIENT_JAVA_XMS:-256m}}}"
HEAP_XMX="${HEAP_XMX:-${FIXCLIENT_TARGET_HEAP_XMX:-${THEFIX_CLIENT_JAVA_XMX:-512m}}}"
CPU_PINNING="${CPU_PINNING:-${FIXCLIENT_TARGET_CPU_PINNING:-}}"

# ── Already-running check ─────────────────────────────────────────────────────
CLIENT_PID_EXISTING="$(read_pid_file "${CLIENT_PID_FILE}")"
if is_pid_running "${CLIENT_PID_EXISTING}"; then
    success "TheFixClient is already running (pid=${CLIENT_PID_EXISTING})."
    echo -e "  ${BOLD}Web UI${RESET} → http://localhost:${CLIENT_PORT}"
    exit 0
fi

# ── Port availability ─────────────────────────────────────────────────────────
if port_in_use "${CLIENT_PORT}"; then
    error "Required port ${CLIENT_PORT} is already in use. Stop the existing service first."
    exit 1
fi

# ── Runtime directories ───────────────────────────────────────────────────────
ensure_runtime_dirs
CLIENT_QUICKFIX_LOG_DIR="${CLIENT_RUNTIME_DIR}/quickfixj"
mkdir -p "${CLIENT_QUICKFIX_LOG_DIR}"

# ── Build ─────────────────────────────────────────────────────────────────────
cd "${PROJECT_ROOT}"
if [[ "${NO_BUILD}" -eq 0 ]]; then
    info "Building TheFixClient distribution..."
    "${GRADLEW_BIN}" --no-daemon :TheFixClient:clean :TheFixClient:installDist -x :TheFixClient:test
fi

# ── Start ─────────────────────────────────────────────────────────────────────
info "Starting TheFixClient..."
(
    cd "${PROJECT_ROOT}/TheFixClient/build/install/TheFixClient"
    CLIENT_ENV=(env \
        JAVA_OPTS="-Xms${HEAP_XMS} -Xmx${HEAP_XMX}" \
        THEFIX_CLIENT_PORT="${CLIENT_PORT}" \
        THEFIX_FIX_HOST="${FIX_HOST}" \
        THEFIX_FIX_PORT="${FIX_PORT}" \
        THEFIX_FIX_LOG_DIR="${CLIENT_QUICKFIX_LOG_DIR}" \
        THEFIX_FIX_RAW_LOGGING_ENABLED="${RAW_LOGGING}")
    if [[ -n "${CPU_PINNING}" ]] && command -v taskset >/dev/null 2>&1; then
        nohup "${CLIENT_ENV[@]}" taskset -c "${CPU_PINNING}" ./bin/TheFixClient > "${CLIENT_LOG_FILE}" 2>&1 &
    else
        nohup "${CLIENT_ENV[@]}" ./bin/TheFixClient > "${CLIENT_LOG_FILE}" 2>&1 &
    fi
    echo $! > "${CLIENT_PID_FILE}"
)

if ! wait_for_http "TheFixClient" "http://localhost:${CLIENT_PORT}/api/health" 90; then
    stop_pidfile_process "TheFixClient" "${CLIENT_PID_FILE}"
    exit 1
fi

echo ""
success "TheFixClient is running."
echo -e "  ${BOLD}Web UI / REST API${RESET} → http://localhost:${CLIENT_PORT}"
echo -e "  ${BOLD}FIX Simulator${RESET}     → ${FIX_HOST}:${FIX_PORT}"
echo -e "  ${BOLD}Logs${RESET}              → ${CLIENT_LOG_FILE}"
