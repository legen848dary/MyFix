#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
CLIENT_PORT="${WEB_STACK_CLIENT_PORT}"
FIX_HOST="127.0.0.1"
FIX_PORT="${WEB_STACK_FIX_PORT}"
HEAP_XMS="${THEFIX_CLIENT_JAVA_XMS:-256m}"
HEAP_XMX="${THEFIX_CLIENT_JAVA_XMX:-512m}"
LOG_DIR=""
NETWORK_MODE="bridge"
CONTAINER_NAME="thefixclient"
NO_BUILD=0
RAW_LOGGING="${THEFIX_FIX_RAW_LOGGING_ENABLED:-false}"

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Start TheFixClient (Docker)${RESET}

Usage:
  ./scripts/start_fix_client_docker.sh [options]

Options:
  --port PORT              TheFixClient HTTP / REST API host port         (default: 8081)
  --fix-host HOST          FIX simulator hostname or IP                   (default: 127.0.0.1)
  --fix-port PORT          FIX simulator TCP port                         (default: 9880)
  --heap-xms SIZE          JVM initial heap size, e.g. 256m / 1g         (default: 256m)
  --heap-xmx SIZE          JVM max heap size, e.g. 512m / 1g             (default: 512m)
  --log-dir DIR            Host path for log output                        (default: ./TheFixClient/logs)
  --network-mode MODE      Docker network mode: bridge | host | none       (default: bridge)
                           'NAT' is treated as 'bridge' (Windows alias).
                           In 'host' mode, port mapping flags are omitted.
                           Use 'host' when the simulator is running on the host JVM or
                           in a Docker container with host networking.
  --container-name NAME    Docker container name                           (default: thefixclient)
  --no-build               Skip Gradle build and Docker image build
  --raw-logging            Enable raw FIX message logging                  (default: false)
  -h, --help               Show this help message

Environment Variables:
  WEB_STACK_CLIENT_PORT          Client HTTP port       (default: 8081)
  WEB_STACK_FIX_PORT             FIX port               (default: 9880)
  THEFIX_CLIENT_JAVA_XMS         Initial heap           (default: 256m)
  THEFIX_CLIENT_JAVA_XMX         Max heap               (default: 512m)
  THEFIX_FIX_RAW_LOGGING_ENABLED Raw FIX logging toggle (default: false)

Examples:
  # Start with defaults (simulator on 127.0.0.1:9880)
  ./scripts/start_fix_client_docker.sh

  # Connect to a containerised simulator on the same Docker bridge network
  ./scripts/start_fix_client_docker.sh --fix-host llexsimulator --fix-port 9880

  # Host network mode — shares host network namespace (Linux only)
  ./scripts/start_fix_client_docker.sh --network-mode host

  # Custom port, skip build
  ./scripts/start_fix_client_docker.sh --port 8082 --no-build

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)           CLIENT_PORT="$2";    shift 2 ;;
        --fix-host)       FIX_HOST="$2";       shift 2 ;;
        --fix-port)       FIX_PORT="$2";       shift 2 ;;
        --heap-xms)       HEAP_XMS="$2";       shift 2 ;;
        --heap-xmx)       HEAP_XMX="$2";       shift 2 ;;
        --log-dir)        LOG_DIR="$2";        shift 2 ;;
        --network-mode)   NETWORK_MODE="$2";   shift 2 ;;
        --container-name) CONTAINER_NAME="$2"; shift 2 ;;
        --no-build)       NO_BUILD=1;          shift   ;;
        --raw-logging)    RAW_LOGGING="true";  shift   ;;
        help|--help|-h)   show_help; exit 0    ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Starting TheFixClient (Docker)"
require_docker
require_gradle_wrapper
source_runtime_profile_if_available

# ── Resolve directories ───────────────────────────────────────────────────────
LOG_DIR="${LOG_DIR:-${WEB_STACK_CLIENT_LOG_DIR:-${PROJECT_ROOT}/TheFixClient/logs}}"
mkdir -p "${LOG_DIR}"

# ── Normalise network mode ────────────────────────────────────────────────────
# 'NAT' is the Windows Docker Desktop alias for bridge networking.
# Use tr for case-folding — compatible with Bash 3.x (macOS default shell).
_NETWORK_MODE_LOWER="$(printf '%s' "${NETWORK_MODE}" | tr '[:upper:]' '[:lower:]')"
if [[ "${_NETWORK_MODE_LOWER}" == "nat" ]]; then
    NETWORK_MODE="bridge"
else
    NETWORK_MODE="${_NETWORK_MODE_LOWER}"
fi
if [[ "${NETWORK_MODE}" != "bridge" && "${NETWORK_MODE}" != "host" && "${NETWORK_MODE}" != "none" ]]; then
    error "Invalid --network-mode '${NETWORK_MODE}'. Must be one of: bridge, host, none, NAT."
    exit 1
fi
unset _NETWORK_MODE_LOWER

# ── Already-running check ─────────────────────────────────────────────────────
if [[ "$(docker inspect -f '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null || echo 'false')" == "true" ]]; then
    success "TheFixClient container '${CONTAINER_NAME}' is already running."
    echo -e "  ${BOLD}Web UI${RESET} → http://localhost:${CLIENT_PORT}"
    exit 0
fi

# ── Port availability (bridge mode only) ─────────────────────────────────────
if [[ "${NETWORK_MODE}" == "bridge" ]]; then
    if port_in_use "${CLIENT_PORT}"; then
        error "Required port ${CLIENT_PORT} is already in use. Stop the existing service first."
        exit 1
    fi
fi

# ── Build ─────────────────────────────────────────────────────────────────────
cd "${PROJECT_ROOT}"
if [[ "${NO_BUILD}" -eq 0 ]]; then
    info "Building TheFixClient distribution..."
    "${GRADLEW_BIN}" --no-daemon :TheFixClient:installDist -x :TheFixClient:test

    info "Building TheFixClient Docker image..."
    docker build -t thefixclient:1.0-SNAPSHOT "${PROJECT_ROOT}/TheFixClient"
fi

# ── Remove any stopped container with the same name ──────────────────────────
if docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    info "Removing stopped container '${CONTAINER_NAME}'..."
    docker rm "${CONTAINER_NAME}" >/dev/null
fi

# ── Assemble docker run flags ─────────────────────────────────────────────────
DOCKER_RUN_ARGS=(
    --detach
    --name "${CONTAINER_NAME}"
    --restart unless-stopped
    --volume "${LOG_DIR}:/app/logs"
    --env "THEFIX_CLIENT_PORT=${CLIENT_PORT}"
    --env "THEFIX_FIX_HOST=${FIX_HOST}"
    --env "THEFIX_FIX_PORT=${FIX_PORT}"
    --env "THEFIX_FIX_LOG_DIR=/app/logs/thefixclient/quickfixj"
    --env "THEFIX_FIX_RAW_LOGGING_ENABLED=${RAW_LOGGING}"
    --env "JAVA_OPTS=-Xms${HEAP_XMS} -Xmx${HEAP_XMX}"
    --health-cmd "curl -sf http://localhost:${CLIENT_PORT}/api/health"  # container-internal port (set via THEFIX_CLIENT_PORT)
    --health-interval 30s
    --health-timeout 5s
    --health-retries 3
    --health-start-period 20s
)

if [[ "${NETWORK_MODE}" == "host" ]]; then
    DOCKER_RUN_ARGS+=(--network host)
elif [[ "${NETWORK_MODE}" == "none" ]]; then
    DOCKER_RUN_ARGS+=(--network none)
else
    # bridge: THEFIX_CLIENT_PORT makes the container listen on the user-chosen
    # port, so we publish it symmetrically.
    DOCKER_RUN_ARGS+=(--publish "${CLIENT_PORT}:${CLIENT_PORT}")
fi

# ── Start container ───────────────────────────────────────────────────────────
info "Starting container '${CONTAINER_NAME}'..."
docker run "${DOCKER_RUN_ARGS[@]}" thefixclient:1.0-SNAPSHOT

if ! wait_for_http "TheFixClient" "http://localhost:${CLIENT_PORT}/api/health" 90; then
    warn "TheFixClient did not become healthy. Check logs:"
    warn "  docker logs ${CONTAINER_NAME}"
    exit 1
fi

echo ""
success "TheFixClient container is running."
echo -e "  ${BOLD}Container${RESET}    → ${CONTAINER_NAME}"
echo -e "  ${BOLD}Network Mode${RESET} → ${NETWORK_MODE}"
echo -e "  ${BOLD}Web UI${RESET}       → http://localhost:${CLIENT_PORT}"
echo -e "  ${BOLD}FIX Simulator${RESET} → ${FIX_HOST}:${FIX_PORT}"
echo -e "  ${BOLD}Logs${RESET}          → ${LOG_DIR}"
echo -e "  ${BOLD}Docker Logs${RESET}   → docker logs ${CONTAINER_NAME} -f"
