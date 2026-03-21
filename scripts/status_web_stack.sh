#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

if [[ "${1:-}" =~ ^(help|--help|-h)$ ]]; then
    cat << EOF

${BOLD}${CYAN}Status Web Stack${RESET}

Usage:
  ./scripts/status_web_stack.sh

Reports the status of:
  - the Docker web stack
  - the direct JVM web stack
  - key health endpoints and bound ports

EOF
    exit 0
fi

source_runtime_profile_if_available
ensure_runtime_dirs

status_line() {
    local label="$1"
    local value="$2"
    printf '  %-24s %s\n' "${label}" "${value}"
}

status_badge() {
    local state="$1"
    case "${state}" in
        RUNNING) printf '%bRUNNING%b' "${GREEN}" "${RESET}" ;;
        PARTIAL) printf '%bPARTIAL%b' "${YELLOW}" "${RESET}" ;;
        STOPPED) printf '%bSTOPPED%b' "${RED}" "${RESET}" ;;
        *) printf '%s' "${state}" ;;
    esac
}

component_health_label() {
    local ok="$1"
    if [[ "${ok}" == "true" ]]; then
        printf '%bOK%b' "${GREEN}" "${RESET}"
    else
        printf '%bDOWN%b' "${RED}" "${RESET}"
    fi
}

bool_text() {
    local value="$1"
    if [[ "${value}" == "true" ]]; then
        printf 'yes'
    else
        printf 'no'
    fi
}

port_owner_text() {
    local port="$1"
    local pid
    pid="$(port_listener_pid "${port}")"
    if [[ -z "${pid}" ]]; then
        printf 'free'
    else
        printf 'pid=%s' "${pid}"
    fi
}

report_direct_status() {
    local sim_pid client_pid
    local sim_running=false client_running=false
    local sim_health=false client_health=false
    local state="STOPPED"

    sim_pid="$(read_pid_file "${SIM_PID_FILE}")"
    client_pid="$(read_pid_file "${CLIENT_PID_FILE}")"

    is_pid_running "${sim_pid}" && sim_running=true
    is_pid_running "${client_pid}" && client_running=true
    http_healthy "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" && sim_health=true
    http_healthy "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health" && client_health=true

    if [[ "${sim_running}" == "true" && "${client_running}" == "true" && "${sim_health}" == "true" && "${client_health}" == "true" ]]; then
        state="RUNNING"
    elif [[ "${sim_running}" == "true" || "${client_running}" == "true" || -n "${sim_pid}" || -n "${client_pid}" ]]; then
        state="PARTIAL"
    fi

    echo -e "${BOLD}Direct JVM stack:${RESET} $(status_badge "${state}")"
    status_line "TheFixSimulator PID" "${sim_pid:-none}"
    status_line "TheFixSimulator alive" "$(bool_text "${sim_running}")"
    status_line "TheFixSimulator health" "$(component_health_label "${sim_health}")"
    status_line "TheFixClient PID" "${client_pid:-none}"
    status_line "TheFixClient alive" "$(bool_text "${client_running}")"
    status_line "TheFixClient health" "$(component_health_label "${client_health}")"
    status_line "Port ${WEB_STACK_SIM_WEB_PORT}" "$(port_owner_text "${WEB_STACK_SIM_WEB_PORT}")"
    status_line "Port ${WEB_STACK_CLIENT_PORT}" "$(port_owner_text "${WEB_STACK_CLIENT_PORT}")"
    status_line "Port ${WEB_STACK_FIX_PORT}" "$(port_owner_text "${WEB_STACK_FIX_PORT}")"
    status_line "Logs" "${LOG_DIR}"
    echo ""
}

report_docker_status() {
    local docker_ready=false
    local sim_running=false client_running=false
    local sim_health=false client_health=false
    local sim_container client_container
    local sim_container_health client_container_health
    local sim_container_id client_container_id
    local state="STOPPED"

    sim_container="$(docker_container_name "llexsimulator")"
    client_container="$(docker_container_name "thefixclient")"

    if docker_daemon_running; then
        docker_ready=true
        sim_container_id="$(docker_container_id "llexsimulator")"
        client_container_id="$(docker_container_id "thefixclient")"
        docker_container_running "llexsimulator" && sim_running=true
        docker_container_running "thefixclient" && client_running=true
        sim_container_health="$(docker_container_health "llexsimulator")"
        client_container_health="$(docker_container_health "thefixclient")"
        http_healthy "http://localhost:${WEB_STACK_SIM_WEB_PORT}/api/health" && sim_health=true
        http_healthy "http://localhost:${WEB_STACK_CLIENT_PORT}/api/health" && client_health=true

        if [[ "${sim_running}" == "true" && "${client_running}" == "true" && "${sim_health}" == "true" && "${client_health}" == "true" ]]; then
            state="RUNNING"
        elif [[ -n "${sim_container_id}" || -n "${client_container_id}" || "${sim_running}" == "true" || "${client_running}" == "true" ]]; then
            state="PARTIAL"
        fi
    fi

    echo -e "${BOLD}Docker stack:${RESET} $(status_badge "${state}")"
    if [[ "${docker_ready}" != "true" ]]; then
        status_line "Docker daemon" "unavailable"
        echo ""
        return 0
    fi

    status_line "TheFixSimulator container" "${sim_container}"
    status_line "TheFixSimulator running" "$(bool_text "${sim_running}")"
    status_line "TheFixSimulator health" "${sim_container_health:-none} / $(component_health_label "${sim_health}")"
    status_line "TheFixClient container" "${client_container}"
    status_line "TheFixClient running" "$(bool_text "${client_running}")"
    status_line "TheFixClient health" "${client_container_health:-none} / $(component_health_label "${client_health}")"
    status_line "Simulator logs" "${WEB_STACK_SIM_LOG_DIR}"
    status_line "Client logs" "${WEB_STACK_CLIENT_LOG_DIR}"
    echo ""
}

banner "Web Stack Status"
report_docker_status
report_direct_status

echo -e "${BOLD}Endpoints:${RESET}"
status_line "TheFixSimulator" "http://localhost:${WEB_STACK_SIM_WEB_PORT}"
status_line "TheFixClient" "http://localhost:${WEB_STACK_CLIENT_PORT}"
status_line "FIX Acceptor" "tcp://localhost:${WEB_STACK_FIX_PORT}"


