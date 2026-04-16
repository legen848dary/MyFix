#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"
# shellcheck source=./native_runtime_targets.sh
source "${SCRIPT_DIR}/native_runtime_targets.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
SIM_WEB_PORT="${WEB_STACK_SIM_WEB_PORT}"
SIM_FIX_PORT="${WEB_STACK_FIX_PORT}"
HEAP_XMS=""
HEAP_XMX=""
CPU_PINNING=""
NO_BUILD=0
RAW_LOGGING="${THEFIX_FIX_RAW_LOGGING_ENABLED:-false}"

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Start TheFixSimulator (Direct JVM)${RESET}

Usage:
  ./scripts/start_fix_simulator.sh [options]

Options:
  --web-port PORT        Vert.x web / REST API port            (default: 8080)
  --fix-port PORT        FIX acceptor TCP port                  (default: 9880)
  --heap-xms SIZE        JVM initial heap size, e.g. 512m / 1g (default: 512m)
  --heap-xmx SIZE        JVM max heap size, e.g. 512m / 1g     (default: 512m)
  --cpu-pinning CPUS     CPU affinity via taskset, e.g. 0-3     (default: none)
  --no-build             Skip Gradle build (reuse existing JAR)
  --raw-logging          Enable raw FIX message logging          (default: false)
  -h, --help             Show this help message

Environment Variables (all overridable via flags above):
  WEB_STACK_SIM_WEB_PORT   Web port        (default: 8080)
  WEB_STACK_FIX_PORT       FIX port        (default: 9880)
  LLEX_JAVA_XMS            Initial heap    (default: 512m)
  LLEX_JAVA_XMX            Max heap        (default: 512m)

Examples:
  # Start with defaults
  ./scripts/start_fix_simulator.sh

  # Custom ports and heap
  ./scripts/start_fix_simulator.sh --web-port 9090 --fix-port 9881 --heap-xmx 1g

  # Skip build, pin to CPU cores 0-3
  ./scripts/start_fix_simulator.sh --no-build --cpu-pinning 0-3

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --web-port)    SIM_WEB_PORT="$2";  shift 2 ;;
        --fix-port)    SIM_FIX_PORT="$2";  shift 2 ;;
        --heap-xms)    HEAP_XMS="$2";      shift 2 ;;
        --heap-xmx)    HEAP_XMX="$2";      shift 2 ;;
        --cpu-pinning) CPU_PINNING="$2";   shift 2 ;;
        --no-build)    NO_BUILD=1;         shift   ;;
        --raw-logging) RAW_LOGGING="true"; shift   ;;
        help|--help|-h) show_help; exit 0 ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Starting TheFixSimulator (Direct JVM)"
require_java
require_gradle_wrapper
source_runtime_profile_if_available
native_runtime_targets_load

HEAP_XMS="${HEAP_XMS:-${FIXSIM_TARGET_HEAP_XMS:-${LLEX_JAVA_XMS:-512m}}}"
HEAP_XMX="${HEAP_XMX:-${FIXSIM_TARGET_HEAP_XMX:-${LLEX_JAVA_XMX:-512m}}}"
CPU_PINNING="${CPU_PINNING:-${FIXSIM_TARGET_CPU_PINNING:-}}"

# ── Already-running check ─────────────────────────────────────────────────────
SIM_PID_FILE_EXISTING="$(read_pid_file "${SIM_PID_FILE}")"
if is_pid_running "${SIM_PID_FILE_EXISTING}"; then
    success "TheFixSimulator is already running (pid=${SIM_PID_FILE_EXISTING})."
    echo -e "  ${BOLD}Web UI${RESET}      → http://localhost:${SIM_WEB_PORT}"
    echo -e "  ${BOLD}FIX Acceptor${RESET} → tcp://localhost:${SIM_FIX_PORT}"
    exit 0
fi

# ── Port availability ─────────────────────────────────────────────────────────
for PORT in "${SIM_WEB_PORT}" "${SIM_FIX_PORT}"; do
    if port_in_use "${PORT}"; then
        error "Required port ${PORT} is already in use. Stop the existing service first."
        exit 1
    fi
done

# ── Runtime directories ───────────────────────────────────────────────────────
ensure_runtime_dirs
SIMULATOR_ARTIO_DIR="${SIM_RUNTIME_DIR}/artio-state/data"
SIMULATOR_AERON_DIR="${SIM_RUNTIME_DIR}/aeron"
SIMULATOR_LOG_DIR="${SIM_RUNTIME_DIR}/logs"
mkdir -p "${SIMULATOR_ARTIO_DIR}" "${SIMULATOR_AERON_DIR}" "${SIMULATOR_LOG_DIR}"

# ── Build ─────────────────────────────────────────────────────────────────────
cd "${PROJECT_ROOT}"
if [[ "${NO_BUILD}" -eq 0 ]]; then
    info "Building TheFixSimulator shadow JAR..."
    "${GRADLEW_BIN}" --no-daemon :TheFixSimulator:clean :TheFixSimulator:shadowJar -x :TheFixSimulator:test
fi

# ── Start ─────────────────────────────────────────────────────────────────────
info "Starting TheFixSimulator..."
(
    cd "${PROJECT_ROOT}/TheFixSimulator"
    SIM_CMD=(java \
        -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
        -Dllexsim.log.dir="${SIMULATOR_LOG_DIR}" \
        -Dllexsim.log.name=llexsimulator \
        -Dfix.host=0.0.0.0 \
        -Dfix.port="${SIM_FIX_PORT}" \
        -Dfix.log.dir="${SIMULATOR_ARTIO_DIR}" \
        -Dfix.raw.message.logging.enabled="${RAW_LOGGING}" \
        -Dweb.port="${SIM_WEB_PORT}" \
        -Daeron.dir="${SIMULATOR_AERON_DIR}" \
        -Dbenchmark.mode.enabled=false \
        -XX:+UseZGC -XX:+ZGenerational \
        -Xms"${HEAP_XMS}" -Xmx"${HEAP_XMX}" \
        -XX:+AlwaysPreTouch \
        -XX:+DisableExplicitGC \
        -XX:+PerfDisableSharedMem \
        --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
        --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        -jar "${PROJECT_ROOT}/TheFixSimulator/build/libs/LLExSimulator-1.0-SNAPSHOT.jar")
    if [[ -n "${CPU_PINNING}" ]] && command -v taskset >/dev/null 2>&1; then
        nohup taskset -c "${CPU_PINNING}" "${SIM_CMD[@]}" > "${SIM_LOG_FILE}" 2>&1 &
    else
        nohup "${SIM_CMD[@]}" > "${SIM_LOG_FILE}" 2>&1 &
    fi
    echo $! > "${SIM_PID_FILE}"
)

if ! wait_for_http "TheFixSimulator" "http://localhost:${SIM_WEB_PORT}/api/health" 90; then
    stop_pidfile_process "TheFixSimulator" "${SIM_PID_FILE}"
    exit 1
fi

echo ""
success "TheFixSimulator is running."
echo -e "  ${BOLD}Web UI / REST API${RESET} → http://localhost:${SIM_WEB_PORT}"
echo -e "  ${BOLD}FIX Acceptor${RESET}      → tcp://localhost:${SIM_FIX_PORT}"
echo -e "  ${BOLD}Logs${RESET}              → ${SIM_LOG_FILE}"
