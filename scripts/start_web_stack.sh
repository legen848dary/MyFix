#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

if [[ "${1:-}" =~ ^(help|--help|-h)$ ]]; then
    cat << EOF

${BOLD}${CYAN}Start Web Stack (Direct JVM)${RESET}

Usage:
  ./scripts/start_web_stack.sh

Builds the required artifacts, then starts TheFixSimulator and TheFixClient directly on the host JVM.

EOF
    exit 0
fi

banner "Starting Web Stack (Direct JVM)"
require_java
require_gradle_wrapper
source_runtime_profile_if_available
ensure_runtime_dirs

if direct_stack_running; then
    success "The direct web stack is already running."
    echo -e "  ${BOLD}TheFixSimulator${RESET} → http://localhost:${WEB_STACK_SIM_WEB_PORT}"
    echo -e "  ${BOLD}TheFixClient${RESET}   → http://localhost:${WEB_STACK_CLIENT_PORT}"
    exit 0
fi

assert_web_stack_ports_available

SIMULATOR_HEAP_XMS="${LLEX_JAVA_XMS:-512m}"
SIMULATOR_HEAP_XMX="${LLEX_JAVA_XMX:-512m}"
CLIENT_HEAP_XMS="${THEFIX_CLIENT_JAVA_XMS:-256m}"
CLIENT_HEAP_XMX="${THEFIX_CLIENT_JAVA_XMX:-512m}"

SIMULATOR_ARTIO_DIR="${SIM_RUNTIME_DIR}/artio-state/data"
SIMULATOR_AERON_DIR="${SIM_RUNTIME_DIR}/aeron"
SIMULATOR_LOG_DIR="${SIM_RUNTIME_DIR}/logs"
CLIENT_QUICKFIX_LOG_DIR="${CLIENT_RUNTIME_DIR}/quickfixj"

mkdir -p "${SIMULATOR_ARTIO_DIR}" "${SIMULATOR_AERON_DIR}" "${SIMULATOR_LOG_DIR}" "${CLIENT_QUICKFIX_LOG_DIR}"
cd "${PROJECT_ROOT}"

info "Building direct-run artifacts..."
"${GRADLEW_BIN}" --no-daemon :TheFixSimulator:shadowJar :TheFixClient:installDist -x :TheFixSimulator:test -x :TheFixClient:test

info "Starting TheFixSimulator..."
(
    cd "${PROJECT_ROOT}/TheFixSimulator"
    nohup java \
        -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
        -Dllexsim.log.dir="${SIMULATOR_LOG_DIR}" \
        -Dllexsim.log.name=llexsimulator \
        -Dfix.host=0.0.0.0 \
        -Dfix.port="${WEB_STACK_FIX_PORT}" \
        -Dfix.log.dir="${SIMULATOR_ARTIO_DIR}" \
        -Dfix.raw.message.logging.enabled=false \
        -Dweb.port="${WEB_STACK_SIM_WEB_PORT}" \
        -Daeron.dir="${SIMULATOR_AERON_DIR}" \
        -Dbenchmark.mode.enabled=false \
        -XX:+UseZGC -XX:+ZGenerational \
        -Xms"${SIMULATOR_HEAP_XMS}" -Xmx"${SIMULATOR_HEAP_XMX}" \
        -XX:+AlwaysPreTouch \
        -XX:+DisableExplicitGC \
        -XX:+PerfDisableSharedMem \
        --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
        --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        -jar "${PROJECT_ROOT}/TheFixSimulator/build/libs/LLExSimulator-1.0-SNAPSHOT.jar" \
        > "${SIM_LOG_FILE}" 2>&1 &
    echo $! > "${SIM_PID_FILE}"
)

if ! wait_for_http "TheFixSimulator" "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" 90; then
    stop_pidfile_process "TheFixSimulator" "${SIM_PID_FILE}"
    exit 1
fi

info "Starting TheFixClient..."
(
    cd "${PROJECT_ROOT}/TheFixClient/build/install/TheFixClient"
    env \
        JAVA_OPTS="-Xms${CLIENT_HEAP_XMS} -Xmx${CLIENT_HEAP_XMX}" \
        THEFIX_CLIENT_PORT="${WEB_STACK_CLIENT_PORT}" \
        THEFIX_FIX_HOST="127.0.0.1" \
        THEFIX_FIX_PORT="${WEB_STACK_FIX_PORT}" \
        THEFIX_FIX_LOG_DIR="${CLIENT_QUICKFIX_LOG_DIR}" \
        THEFIX_FIX_RAW_LOGGING_ENABLED="${THEFIX_FIX_RAW_LOGGING_ENABLED:-false}" \
        nohup ./bin/TheFixClient > "${CLIENT_LOG_FILE}" 2>&1 &
    echo $! > "${CLIENT_PID_FILE}"
)

if ! wait_for_http "TheFixClient" "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health" 90; then
    stop_pidfile_process "TheFixClient" "${CLIENT_PID_FILE}"
    stop_pidfile_process "TheFixSimulator" "${SIM_PID_FILE}"
    exit 1
fi

echo ""
success "Direct JVM web stack is running."
echo -e "  ${BOLD}TheFixSimulator${RESET} → http://localhost:${WEB_STACK_SIM_WEB_PORT}"
echo -e "  ${BOLD}TheFixClient${RESET}   → http://localhost:${WEB_STACK_CLIENT_PORT}"
echo -e "  ${BOLD}FIX Acceptor${RESET}    → tcp://localhost:${WEB_STACK_FIX_PORT}"
echo -e "  ${BOLD}Logs${RESET}            → ${LOG_DIR}"

