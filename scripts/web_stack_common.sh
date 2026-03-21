#!/usr/bin/env bash

if [[ -n "${MYFIX_WEB_STACK_COMMON_SOURCED:-}" ]]; then
    return 0
fi
MYFIX_WEB_STACK_COMMON_SOURCED=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GRADLEW_BIN="${PROJECT_ROOT}/gradlew"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.web-stack.yml"
COMPOSE_PROJECT_NAME="myfix-web-stack"
RUNTIME_DIR="${PROJECT_ROOT}/build/web-stack"
PID_DIR="${RUNTIME_DIR}/pids"
LOG_DIR="${RUNTIME_DIR}/logs"
SIM_LOG_FILE="${LOG_DIR}/thefixsimulator.log"
CLIENT_LOG_FILE="${LOG_DIR}/thefixclient.log"
SIM_PID_FILE="${PID_DIR}/thefixsimulator.pid"
CLIENT_PID_FILE="${PID_DIR}/thefixclient.pid"
SIM_RUNTIME_DIR="${RUNTIME_DIR}/thefixsimulator"
CLIENT_RUNTIME_DIR="${RUNTIME_DIR}/thefixclient"
RUNTIME_PROFILE_HELPER="${PROJECT_ROOT}/TheFixSimulator/scripts/runtime_profile_common.sh"

WEB_STACK_SIM_WEB_PORT="${WEB_STACK_SIM_WEB_PORT:-8080}"
WEB_STACK_FIX_PORT="${WEB_STACK_FIX_PORT:-9880}"
WEB_STACK_CLIENT_PORT="${WEB_STACK_CLIENT_PORT:-8081}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'
CYAN=$'\033[0;36m'; BOLD=$'\033[1m'; RESET=$'\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
banner()  { echo -e "\n${BOLD}${CYAN}══════════════════════════════════════════${RESET}"; \
            echo -e "${BOLD}  $*${RESET}"; \
            echo -e "${BOLD}${CYAN}══════════════════════════════════════════${RESET}\n"; }

require_java() {
    if ! command -v java >/dev/null 2>&1; then
        error "Java is not installed or not in PATH."
        exit 1
    fi
}

require_gradle_wrapper() {
    if [[ ! -x "${GRADLEW_BIN}" ]]; then
        error "Gradle wrapper not found at ${GRADLEW_BIN}."
        exit 1
    fi
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        error "Docker is not installed or not in PATH."
        exit 1
    fi
    if ! docker info >/dev/null 2>&1; then
        error "Docker daemon is not running."
        exit 1
    fi
}

require_compose() {
    require_docker
    if ! docker compose version >/dev/null 2>&1; then
        error "Docker Compose v2 is required."
        exit 1
    fi
}

compose_cmd() {
    require_compose
    docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

ensure_runtime_dirs() {
    mkdir -p "${PID_DIR}" "${LOG_DIR}" "${SIM_RUNTIME_DIR}" "${CLIENT_RUNTIME_DIR}"
}

read_pid_file() {
    local pid_file="$1"
    if [[ -f "${pid_file}" ]]; then
        tr -d '[:space:]' < "${pid_file}"
    fi
}

source_runtime_profile_if_available() {
    if [[ -f "${RUNTIME_PROFILE_HELPER}" ]]; then
        # shellcheck disable=SC1090
        source "${RUNTIME_PROFILE_HELPER}"
        runtime_profile_load_env
    fi

    export WEB_STACK_SIM_CONFIG_DIR="${WEB_STACK_SIM_CONFIG_DIR:-${LLEX_CONFIG_DIR:-${PROJECT_ROOT}/TheFixSimulator/config}}"
    export WEB_STACK_SIM_LOG_DIR="${WEB_STACK_SIM_LOG_DIR:-${PROJECT_ROOT}/TheFixSimulator/logs}"
    export WEB_STACK_CLIENT_LOG_DIR="${WEB_STACK_CLIENT_LOG_DIR:-${PROJECT_ROOT}/TheFixClient/logs}"
}

is_pid_running() {
    local pid="$1"
    [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

http_healthy() {
    local url="$1"
    command -v curl >/dev/null 2>&1 && curl -sf "${url}" >/dev/null 2>&1
}

docker_available() {
    command -v docker >/dev/null 2>&1
}

docker_daemon_running() {
    docker_available && docker info >/dev/null 2>&1
}

docker_container_name() {
    local service="$1"
    printf '%s-%s-1\n' "${COMPOSE_PROJECT_NAME}" "${service}"
}

docker_container_id() {
    local service="$1"
    local container_name
    container_name="$(docker_container_name "${service}")"
    docker ps -aq -f "name=^/${container_name}$" 2>/dev/null | head -n 1 || true
}

docker_container_running() {
    local service="$1"
    local container_id
    container_id="$(docker_container_id "${service}")"
    [[ -n "${container_id}" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "${container_id}" 2>/dev/null || true)" == "true" ]]
}

docker_container_health() {
    local service="$1"
    local container_id
    container_id="$(docker_container_id "${service}")"
    if [[ -z "${container_id}" ]]; then
        return 0
    fi
    docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${container_id}" 2>/dev/null || true
}

port_listener_pid() {
    local port="$1"
    lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
}

port_in_use() {
    local port="$1"
    [[ -n "$(port_listener_pid "${port}")" ]]
}

wait_for_http() {
    local label="$1"
    local url="$2"
    local max_wait="${3:-60}"
    local waited=0

    info "Waiting for ${label} at ${url} (max ${max_wait}s)..."
    while (( waited < max_wait )); do
        if curl -sf "${url}" >/dev/null 2>&1; then
            success "${label} is ready."
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
        printf "."
    done
    echo ""
    error "Timed out waiting for ${label} at ${url}."
    return 1
}

wait_for_pid_exit() {
    local pid="$1"
    local max_wait="${2:-15}"
    local waited=0
    while is_pid_running "${pid}" && (( waited < max_wait )); do
        sleep 1
        waited=$((waited + 1))
    done
    ! is_pid_running "${pid}"
}

stop_pidfile_process() {
    local label="$1"
    local pid_file="$2"
    local pid=""

    if [[ -f "${pid_file}" ]]; then
        pid="$(tr -d '[:space:]' < "${pid_file}")"
    fi

    if is_pid_running "${pid}"; then
        info "Stopping ${label} (pid=${pid})..."
        kill "${pid}" 2>/dev/null || true
        if ! wait_for_pid_exit "${pid}" 15; then
            warn "${label} did not stop after SIGTERM; sending SIGKILL."
            kill -9 "${pid}" 2>/dev/null || true
        fi
        success "${label} stopped."
    else
        warn "${label} is not running from pidfile ${pid_file}."
    fi

    rm -f "${pid_file}"
}

stop_listener_on_port() {
    local label="$1"
    local port="$2"
    local pid

    pid="$(port_listener_pid "${port}")"
    if [[ -z "${pid}" ]]; then
        return 0
    fi

    info "Stopping ${label} listener on port ${port} (pid=${pid})..."
    kill "${pid}" 2>/dev/null || true
    if ! wait_for_pid_exit "${pid}" 10; then
        warn "Port ${port} is still active; sending SIGKILL to pid ${pid}."
        kill -9 "${pid}" 2>/dev/null || true
    fi
}

direct_stack_running() {
    local sim_pid=""
    local client_pid=""

    sim_pid="$(read_pid_file "${SIM_PID_FILE}")"
    client_pid="$(read_pid_file "${CLIENT_PID_FILE}")"

    is_pid_running "${sim_pid}" && is_pid_running "${client_pid}" && \
        http_healthy "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" && \
        http_healthy "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health"
}

docker_stack_running() {
    local running_services
    running_services="$(compose_cmd ps --services --filter status=running 2>/dev/null || true)"

    grep -qx "llexsimulator" <<< "${running_services}" && \
        grep -qx "thefixclient" <<< "${running_services}" && \
        http_healthy "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" && \
        http_healthy "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health"
}

assert_web_stack_ports_available() {
    local port
    for port in "${WEB_STACK_SIM_WEB_PORT}" "${WEB_STACK_FIX_PORT}" "${WEB_STACK_CLIENT_PORT}"; do
        if port_in_use "${port}"; then
            error "Required port ${port} is already in use. Stop the existing service first."
            exit 1
        fi
    done
}

