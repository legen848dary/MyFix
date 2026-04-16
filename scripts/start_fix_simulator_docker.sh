#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./web_stack_common.sh
source "${SCRIPT_DIR}/web_stack_common.sh"

# ── Defaults ──────────────────────────────────────────────────────────────────
SIM_WEB_PORT="${WEB_STACK_SIM_WEB_PORT}"
SIM_FIX_PORT="${WEB_STACK_FIX_PORT}"
HEAP_XMS="${LLEX_JAVA_XMS:-1024m}"
HEAP_XMX="${LLEX_JAVA_XMX:-1024m}"
SHM_SIZE="${LLEX_SHM_SIZE:-512m}"
CONFIG_DIR=""
LOG_DIR=""
NETWORK_MODE="bridge"
CONTAINER_NAME="llexsimulator"
NO_BUILD=0

# ── Help ──────────────────────────────────────────────────────────────────────
show_help() {
    cat << EOF

${BOLD}${CYAN}Start TheFixSimulator (Docker)${RESET}

Usage:
  ./scripts/start_fix_simulator_docker.sh [options]

Options:
  --web-port PORT          Vert.x web / REST API host port               (default: 8080)
  --fix-port PORT          FIX acceptor host port                         (default: 9880)
  --heap-xms SIZE          JVM initial heap size, e.g. 512m / 1g         (default: 1024m)
  --heap-xmx SIZE          JVM max heap size, e.g. 512m / 1g             (default: 1024m)
  --shm-size SIZE          Docker shared memory size (/dev/shm)           (default: 512m)
  --config-dir DIR         Host path to simulator.properties config dir   (default: ./TheFixSimulator/config)
  --log-dir DIR            Host path for log output                        (default: ./TheFixSimulator/logs)
  --network-mode MODE      Docker network mode: bridge | host | none       (default: bridge)
                           'NAT' is treated as 'bridge' (Windows alias).
                           In 'host' mode, port mapping flags are omitted.
  --container-name NAME    Docker container name                           (default: llexsimulator)
  --no-build               Skip Gradle build and Docker image build
  -h, --help               Show this help message

Environment Variables:
  WEB_STACK_SIM_WEB_PORT   Web port             (default: 8080)
  WEB_STACK_FIX_PORT        FIX port             (default: 9880)
  LLEX_JAVA_XMS             Initial heap         (default: 1024m)
  LLEX_JAVA_XMX             Max heap             (default: 1024m)
  LLEX_SHM_SIZE             Shared memory size   (default: 512m)

Examples:
  # Start with defaults
  ./scripts/start_fix_simulator_docker.sh

  # Custom ports and heap
  ./scripts/start_fix_simulator_docker.sh --web-port 9090 --fix-port 9881 --heap-xmx 2g

  # Host network mode (Linux only — bypasses port mapping)
  ./scripts/start_fix_simulator_docker.sh --network-mode host

  # Skip image rebuild
  ./scripts/start_fix_simulator_docker.sh --no-build

EOF
}

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --web-port)       SIM_WEB_PORT="$2";    shift 2 ;;
        --fix-port)       SIM_FIX_PORT="$2";    shift 2 ;;
        --heap-xms)       HEAP_XMS="$2";        shift 2 ;;
        --heap-xmx)       HEAP_XMX="$2";        shift 2 ;;
        --shm-size)       SHM_SIZE="$2";        shift 2 ;;
        --config-dir)     CONFIG_DIR="$2";      shift 2 ;;
        --log-dir)        LOG_DIR="$2";         shift 2 ;;
        --network-mode)   NETWORK_MODE="$2";    shift 2 ;;
        --container-name) CONTAINER_NAME="$2";  shift 2 ;;
        --no-build)       NO_BUILD=1;           shift   ;;
        help|--help|-h)   show_help; exit 0     ;;
        *) error "Unknown option: $1"; echo ""; show_help; exit 1 ;;
    esac
done

banner "Starting TheFixSimulator (Docker)"
require_docker
require_gradle_wrapper
source_runtime_profile_if_available

# ── Resolve directories ───────────────────────────────────────────────────────
CONFIG_DIR="${CONFIG_DIR:-${WEB_STACK_SIM_CONFIG_DIR:-${PROJECT_ROOT}/TheFixSimulator/config}}"
LOG_DIR="${LOG_DIR:-${WEB_STACK_SIM_LOG_DIR:-${PROJECT_ROOT}/TheFixSimulator/logs}}"
mkdir -p "${CONFIG_DIR}" "${LOG_DIR}"

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
    success "TheFixSimulator container '${CONTAINER_NAME}' is already running."
    echo -e "  ${BOLD}Web UI${RESET}      → http://localhost:${SIM_WEB_PORT}"
    echo -e "  ${BOLD}FIX Acceptor${RESET} → tcp://localhost:${SIM_FIX_PORT}"
    exit 0
fi

# ── Port availability (bridge mode only — host mode binds on host directly) ───
if [[ "${NETWORK_MODE}" == "bridge" ]]; then
    for PORT in "${SIM_WEB_PORT}" "${SIM_FIX_PORT}"; do
        if port_in_use "${PORT}"; then
            error "Required port ${PORT} is already in use. Stop the existing service first."
            exit 1
        fi
    done
fi

# ── Validate config directory ─────────────────────────────────────────────────
# The bind-mount overlays the image's baked-in /app/config. If the directory
# exists but does not contain simulator.properties the container will start
# without any config, so we error here with a clear message rather than
# letting the JVM fail silently at startup.
if [[ ! -f "${CONFIG_DIR}/simulator.properties" ]]; then
    error "simulator.properties not found in --config-dir '${CONFIG_DIR}'."
    error "Expected: ${CONFIG_DIR}/simulator.properties"
    error "Either create the file or omit --config-dir to use the bundled defaults."
    exit 1
fi

# ── Build ─────────────────────────────────────────────────────────────────────
cd "${PROJECT_ROOT}"
if [[ "${NO_BUILD}" -eq 0 ]]; then
    info "Building TheFixSimulator shadow JAR..."
    "${GRADLEW_BIN}" --no-daemon :TheFixSimulator:shadowJar -x :TheFixSimulator:test

    info "Building TheFixSimulator Docker image..."
    docker build -t llexsimulator:1.0-SNAPSHOT "${PROJECT_ROOT}/TheFixSimulator"
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
    --tmpfs /tmp/artio-state:size=64m,mode=1777
    --shm-size "${SHM_SIZE}"
    --volume "${CONFIG_DIR}:/app/config:ro"
    --volume "${LOG_DIR}:/app/logs"
    --env "JAVA_OPTS=-XX:+UseZGC -XX:+ZGenerational \
-Xms${HEAP_XMS} -Xmx${HEAP_XMX} \
-XX:+AlwaysPreTouch \
-XX:+DisableExplicitGC \
-XX:+PerfDisableSharedMem \
-Dweb.port=${SIM_WEB_PORT} \
-Dfix.port=${SIM_FIX_PORT} \
-Daeron.dir=/dev/shm/aeron-llexsim \
-Daeron.ipc.term.buffer.length=8388608 \
-Daeron.threading.mode=SHARED \
-Daeron.shared.idle.strategy=backoff \
-Dagrona.disable.bounds.checks=true \
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED"
    --health-cmd "curl -sf http://localhost:${SIM_WEB_PORT}/api/health"  # container-internal port (set via -Dweb.port)
    --health-interval 30s
    --health-timeout 5s
    --health-retries 3
    --health-start-period 30s
)

if [[ "${NETWORK_MODE}" == "host" ]]; then
    DOCKER_RUN_ARGS+=(--network host)
elif [[ "${NETWORK_MODE}" == "none" ]]; then
    DOCKER_RUN_ARGS+=(--network none)
else
    # bridge: -Dweb.port / -Dfix.port make the container listen on the user-chosen
    # ports, so we publish them symmetrically (no fixed 8080/9880 remapping needed).
    DOCKER_RUN_ARGS+=(
        --publish "${SIM_WEB_PORT}:${SIM_WEB_PORT}"
        --publish "${SIM_FIX_PORT}:${SIM_FIX_PORT}"
    )
fi

# ── Start container ───────────────────────────────────────────────────────────
info "Starting container '${CONTAINER_NAME}'..."
docker run "${DOCKER_RUN_ARGS[@]}" llexsimulator:1.0-SNAPSHOT

if ! wait_for_http "TheFixSimulator" "http://localhost:${SIM_WEB_PORT}/api/health" 90; then
    warn "TheFixSimulator did not become healthy. Check logs:"
    warn "  docker logs ${CONTAINER_NAME}"
    exit 1
fi

echo ""
success "TheFixSimulator container is running."
echo -e "  ${BOLD}Container${RESET}    → ${CONTAINER_NAME}"
echo -e "  ${BOLD}Network Mode${RESET} → ${NETWORK_MODE}"
echo -e "  ${BOLD}Web UI${RESET}       → http://localhost:${SIM_WEB_PORT}"
echo -e "  ${BOLD}FIX Acceptor${RESET} → tcp://localhost:${SIM_FIX_PORT}"
echo -e "  ${BOLD}Logs${RESET}         → ${LOG_DIR}"
echo -e "  ${BOLD}Docker Logs${RESET}  → docker logs ${CONTAINER_NAME} -f"
