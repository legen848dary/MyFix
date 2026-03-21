#!/usr/bin/env bash
# =============================================================================
# deploy_native_stack.sh — Build and deploy MyFix directly to a Linux droplet
# =============================================================================
# Usage:
#   ./scripts/deploy_native_stack.sh <host-or-ip> <ssh-key-path-or-glob> [ssh-user] [options]
#
# Default model:
#   - SSH / deploy as root
#   - Run TheFixSimulator and TheFixClient as a normal Linux user (default: djs)
#   - Build locally, copy artifacts over SSH, switch a release symlink, restart systemd
#   - Configure nginx + Let's Encrypt for both public hostnames
# =============================================================================

set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW_BIN="${PROJECT_ROOT}/gradlew"
RUNTIME_PROFILE_HELPER="${PROJECT_ROOT}/TheFixSimulator/scripts/runtime_profile_common.sh"
NATIVE_RUNTIME_TARGETS_HELPER="${PROJECT_ROOT}/scripts/native_runtime_targets.sh"

DROPLET_HOST=""
SSH_KEY_PATH=""
DROPLET_USER="root"
APP_DIR="${APP_DIR:-/opt/myfix}"
APP_USER="${APP_USER:-djs}"
SIM_HOSTNAME="${SIM_HOSTNAME:-exsim.insoftu.com}"
CLIENT_HOSTNAME="${CLIENT_HOSTNAME:-fixclient.insoftu.com}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-dj.iitian@gmail.com}"
SIM_WEB_PORT="${SIM_WEB_PORT:-8080}"
CLIENT_WEB_PORT="${CLIENT_WEB_PORT:-8081}"
FIX_PORT="${FIX_PORT:-9880}"
WAIT_SECONDS="${WAIT_SECONDS:-120}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
RELEASE_ID=""
SKIP_BUILD=false
SKIP_CERTBOT=false
DRY_RUN=false

SIMULATOR_JAR=""
CLIENT_INSTALL_DIR="${PROJECT_ROOT}/TheFixClient/build/install/TheFixClient"
STAGING_DIR=""
SIM_HEAP_XMS="512m"
SIM_HEAP_XMX="512m"
CLIENT_HEAP_XMS="256m"
CLIENT_HEAP_XMX="512m"
SIM_CPU_PINNING=""
CLIENT_CPU_PINNING=""
BASE_SIM_CONFIG_FILE="${PROJECT_ROOT}/TheFixSimulator/config/simulator.properties"
GIT_COMMIT="unknown"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'
CYAN=$'\033[0;36m'; BOLD=$'\033[1m'; RESET=$'\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
banner()  {
    echo -e "\n${BOLD}${CYAN}══════════════════════════════════════════${RESET}"
    echo -e "${BOLD}  $*${RESET}"
    echo -e "${BOLD}${CYAN}══════════════════════════════════════════${RESET}\n"
}

usage() {
    cat <<EOF
${BOLD}${CYAN}Build and deploy both MyFix web apps directly to a droplet${RESET}

${BOLD}Usage:${RESET}
  ./scripts/${SCRIPT_NAME} <host-or-ip> <ssh-key-path-or-glob> [ssh-user] [options]

${BOLD}Positional arguments:${RESET}
  ${GREEN}host-or-ip${RESET}                 Droplet hostname or IP address
  ${GREEN}ssh-key-path-or-glob${RESET}      Private key path or glob (example: ~/.ssh/id_rsa_ai*)
  ${GREEN}ssh-user${RESET}                   SSH user (default: root)

${BOLD}Options:${RESET}
  ${GREEN}--app-dir <path>${RESET}            Remote application root (default: ${APP_DIR})
  ${GREEN}--app-user <name>${RESET}           Linux user that runs both apps (default: ${APP_USER})
  ${GREEN}--sim-host <hostname>${RESET}       Public hostname for TheFixSimulator (default: ${SIM_HOSTNAME})
  ${GREEN}--client-host <hostname>${RESET}    Public hostname for TheFixClient (default: ${CLIENT_HOSTNAME})
  ${GREEN}--email <address>${RESET}           Email for Let's Encrypt (default: ${CERTBOT_EMAIL})
  ${GREEN}--sim-web-port <port>${RESET}       Simulator backend port (default: ${SIM_WEB_PORT})
  ${GREEN}--client-web-port <port>${RESET}    Client backend port (default: ${CLIENT_WEB_PORT})
  ${GREEN}--fix-port <port>${RESET}           Simulator FIX port (default: ${FIX_PORT})
  ${GREEN}--wait-seconds <n>${RESET}          Health-check timeout (default: ${WAIT_SECONDS})
  ${GREEN}--keep-releases <n>${RESET}         Number of releases to retain remotely (default: ${KEEP_RELEASES})
  ${GREEN}--release-id <id>${RESET}           Override auto-generated release id
  ${GREEN}--skip-build${RESET}                Reuse existing local artifacts instead of rebuilding
  ${GREEN}--skip-certbot${RESET}              Deploy nginx configs but skip Let's Encrypt
  ${GREEN}--dry-run${RESET}                   Print commands and remote actions without executing them
  ${GREEN}help${RESET}                        Show this help

${BOLD}Examples:${RESET}
  ./scripts/${SCRIPT_NAME} ip_or_hostname private_key_path root
  ./scripts/${SCRIPT_NAME} ip_or_hostname private_key_path root --dry-run --skip-build

${BOLD}What this script does:${RESET}
  - builds :TheFixSimulator:shadowJar and :TheFixClient:installDist locally
  - renders native runtime config + env files
  - uploads a new release to ${APP_DIR}/releases/<release-id>
  - updates ${APP_DIR}/current to the new release
  - restarts myfix-simulator.service and myfix-client.service
  - configures nginx for ${SIM_HOSTNAME} and ${CLIENT_HOSTNAME}
  - obtains / renews Let's Encrypt certificates unless --skip-certbot is used
EOF
}

require_option_value() {
    local option_name="$1"
    local remaining_args="$2"
    if [[ "${remaining_args}" -lt 2 ]]; then
        error "Missing value for ${option_name}"
        exit 1
    fi
}

resolve_private_key_path() {
    local pattern="$1"
    local -a matches=()
    local candidate=""

    if [[ "${pattern}" == *'*'* || "${pattern}" == *'?'* || "${pattern}" == *'['* ]]; then
        while IFS= read -r candidate; do
            [[ -n "${candidate}" ]] && matches+=("${candidate}")
        done < <(compgen -G "${pattern}" || true)
        for candidate in "${matches[@]}"; do
            if [[ "${candidate}" != *.pub ]]; then
                printf '%s\n' "${candidate}"
                return 0
            fi
        done
        printf '%s\n' ""
        return 0
    fi

    printf '%s\n' "${pattern}"
}

parse_args() {
    if [[ $# -eq 0 ]]; then
        usage
        exit 1
    fi

    case "${1:-}" in
        help|--help|-h)
            usage
            exit 0
            ;;
    esac

    if [[ $# -lt 2 ]]; then
        error "Missing required arguments: <host-or-ip> <ssh-key-path-or-glob> [ssh-user]"
        echo ""
        usage
        exit 1
    fi

    DROPLET_HOST="$1"
    SSH_KEY_PATH_RAW="$2"
    shift 2

    if [[ $# -gt 0 && "${1}" != --* ]]; then
        DROPLET_USER="$1"
        shift
    fi

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --app-dir)
                require_option_value "$1" "$#"
                APP_DIR="$2"
                shift 2
                ;;
            --app-user)
                require_option_value "$1" "$#"
                APP_USER="$2"
                shift 2
                ;;
            --sim-host)
                require_option_value "$1" "$#"
                SIM_HOSTNAME="$2"
                shift 2
                ;;
            --client-host)
                require_option_value "$1" "$#"
                CLIENT_HOSTNAME="$2"
                shift 2
                ;;
            --email)
                require_option_value "$1" "$#"
                CERTBOT_EMAIL="$2"
                shift 2
                ;;
            --sim-web-port)
                require_option_value "$1" "$#"
                SIM_WEB_PORT="$2"
                shift 2
                ;;
            --client-web-port)
                require_option_value "$1" "$#"
                CLIENT_WEB_PORT="$2"
                shift 2
                ;;
            --fix-port)
                require_option_value "$1" "$#"
                FIX_PORT="$2"
                shift 2
                ;;
            --wait-seconds)
                require_option_value "$1" "$#"
                WAIT_SECONDS="$2"
                shift 2
                ;;
            --keep-releases)
                require_option_value "$1" "$#"
                KEEP_RELEASES="$2"
                shift 2
                ;;
            --release-id)
                require_option_value "$1" "$#"
                RELEASE_ID="$2"
                shift 2
                ;;
            --skip-build)
                SKIP_BUILD=true
                shift
                ;;
            --skip-certbot)
                SKIP_CERTBOT=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            help|--help|-h)
                usage
                exit 0
                ;;
            *)
                error "Unknown argument: $1"
                usage
                exit 1
                ;;
        esac
    done

    SSH_KEY_PATH="$(resolve_private_key_path "${SSH_KEY_PATH_RAW}")"
}

require_local_binary() {
    local binary="$1"
    if ! command -v "${binary}" >/dev/null 2>&1; then
        error "Required local dependency is missing: ${binary}"
        exit 1
    fi
}

ssh_target() {
    printf '%s@%s' "${DROPLET_USER}" "${DROPLET_HOST}"
}

compute_release_id() {
    if [[ -n "${RELEASE_ID}" ]]; then
        return 0
    fi

    local timestamp
    timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
    if command -v git >/dev/null 2>&1 && git -C "${PROJECT_ROOT}" rev-parse --short HEAD >/dev/null 2>&1; then
        GIT_COMMIT="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)"
        RELEASE_ID="${timestamp}-${GIT_COMMIT}"
    else
        RELEASE_ID="${timestamp}"
    fi
}

validate_inputs() {
    [[ -n "${DROPLET_HOST}" ]] || { error "Droplet host cannot be empty."; exit 1; }
    [[ -n "${APP_DIR}" ]] || { error "App dir cannot be empty."; exit 1; }
    [[ -n "${APP_USER}" ]] || { error "App user cannot be empty."; exit 1; }
    [[ -n "${SIM_HOSTNAME}" ]] || { error "--sim-host cannot be empty."; exit 1; }
    [[ -n "${CLIENT_HOSTNAME}" ]] || { error "--client-host cannot be empty."; exit 1; }
    [[ -n "${CERTBOT_EMAIL}" || "${SKIP_CERTBOT}" == true ]] || { error "--email is required unless --skip-certbot is used."; exit 1; }
    [[ "${SIM_WEB_PORT}" =~ ^[0-9]+$ ]] || { error "--sim-web-port must be numeric."; exit 1; }
    [[ "${CLIENT_WEB_PORT}" =~ ^[0-9]+$ ]] || { error "--client-web-port must be numeric."; exit 1; }
    [[ "${FIX_PORT}" =~ ^[0-9]+$ ]] || { error "--fix-port must be numeric."; exit 1; }
    [[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]] || { error "--wait-seconds must be numeric."; exit 1; }
    [[ "${KEEP_RELEASES}" =~ ^[0-9]+$ ]] || { error "--keep-releases must be numeric."; exit 1; }

    require_local_binary ssh
    require_local_binary rsync
    require_local_binary python3
    require_local_binary curl

    if [[ ! -x "${GRADLEW_BIN}" && "${SKIP_BUILD}" == false ]]; then
        error "Gradle wrapper not found at ${GRADLEW_BIN}"
        exit 1
    fi

    if [[ "${DRY_RUN}" == false && ( -z "${SSH_KEY_PATH}" || ! -f "${SSH_KEY_PATH}" ) ]]; then
        error "SSH private key not found: ${SSH_KEY_PATH_RAW}"
        exit 1
    fi

    if [[ "${DRY_RUN}" == true && ( -z "${SSH_KEY_PATH}" || ! -f "${SSH_KEY_PATH}" ) ]]; then
        warn "Dry-run mode: resolved private key is missing locally (${SSH_KEY_PATH_RAW}); continuing anyway."
        SSH_KEY_PATH="${SSH_KEY_PATH_RAW}"
    fi
}

load_runtime_profile() {
    if [[ -f "${RUNTIME_PROFILE_HELPER}" ]]; then
        # shellcheck disable=SC1090
        source "${RUNTIME_PROFILE_HELPER}"
        runtime_profile_load_env
        if [[ -n "${LLEX_JAVA_XMS:-}" ]]; then
            SIM_HEAP_XMS="${LLEX_JAVA_XMS}"
            SIM_HEAP_XMX="${LLEX_JAVA_XMX:-${LLEX_JAVA_XMS}}"
        fi
        if [[ -n "${FIX_DEMO_JAVA_XMS:-}" ]]; then
            CLIENT_HEAP_XMS="${FIX_DEMO_JAVA_XMS}"
            CLIENT_HEAP_XMX="${FIX_DEMO_JAVA_XMX:-${FIX_DEMO_JAVA_XMS}}"
        fi
        if [[ -n "${LLEX_CONFIG_DIR:-}" && -f "${LLEX_CONFIG_DIR}/simulator.properties" ]]; then
            BASE_SIM_CONFIG_FILE="${LLEX_CONFIG_DIR}/simulator.properties"
        fi
    fi

    if [[ -f "${NATIVE_RUNTIME_TARGETS_HELPER}" ]]; then
        # shellcheck disable=SC1090
        source "${NATIVE_RUNTIME_TARGETS_HELPER}"
        native_runtime_targets_load
        if [[ -n "${FIXSIM_TARGET_HEAP_XMS:-}" ]]; then
            SIM_HEAP_XMS="${FIXSIM_TARGET_HEAP_XMS}"
        fi
        if [[ -n "${FIXSIM_TARGET_HEAP_XMX:-}" ]]; then
            SIM_HEAP_XMX="${FIXSIM_TARGET_HEAP_XMX}"
        fi
        if [[ -n "${FIXCLIENT_TARGET_HEAP_XMS:-}" ]]; then
            CLIENT_HEAP_XMS="${FIXCLIENT_TARGET_HEAP_XMS}"
        fi
        if [[ -n "${FIXCLIENT_TARGET_HEAP_XMX:-}" ]]; then
            CLIENT_HEAP_XMX="${FIXCLIENT_TARGET_HEAP_XMX}"
        fi
        SIM_CPU_PINNING="${FIXSIM_TARGET_CPU_PINNING:-}"
        CLIENT_CPU_PINNING="${FIXCLIENT_TARGET_CPU_PINNING:-}"
    fi
}

find_latest_simulator_jar() {
    local jar
    jar="$(find "${PROJECT_ROOT}/TheFixSimulator/build/libs" -maxdepth 1 -type f -name 'LLExSimulator-*.jar' ! -name '*-plain.jar' -print 2>/dev/null | sort | tail -n 1)"
    printf '%s\n' "${jar}"
}

build_local_artifacts() {
    load_runtime_profile

    if [[ "${SKIP_BUILD}" == false ]]; then
        banner "Building Local Artifacts"
        info "Running Gradle for TheFixSimulator and TheFixClient"
        "${GRADLEW_BIN}" --no-daemon :TheFixSimulator:shadowJar :TheFixClient:installDist -x :TheFixSimulator:test -x :TheFixClient:test
    else
        info "Skipping local rebuild; reusing existing artifacts."
    fi

    SIMULATOR_JAR="$(find_latest_simulator_jar)"
    if [[ -z "${SIMULATOR_JAR}" ]]; then
        error "Could not find TheFixSimulator shadow JAR under TheFixSimulator/build/libs"
        exit 1
    fi
    if [[ ! -d "${CLIENT_INSTALL_DIR}" ]]; then
        error "Could not find TheFixClient installDist output under ${CLIENT_INSTALL_DIR}"
        exit 1
    fi
}

prepare_staging_dir() {
    STAGING_DIR="${PROJECT_ROOT}/build/native-deploy/${RELEASE_ID}"
    rm -rf "${STAGING_DIR}"
    mkdir -p "${STAGING_DIR}/shared/bin" "${STAGING_DIR}/shared/config" "${STAGING_DIR}/shared/env" "${STAGING_DIR}/scripts"
}

write_simulator_config() {
    cp "${BASE_SIM_CONFIG_FILE}" "${STAGING_DIR}/shared/config/simulator.properties"
    cat <<EOF >> "${STAGING_DIR}/shared/config/simulator.properties"

# Deployment overrides generated by scripts/${SCRIPT_NAME}
fix.host=127.0.0.1
fix.port=${FIX_PORT}
fix.log.dir=${APP_DIR}/shared/state/artio
fix.raw.message.logging.enabled=false
web.port=${SIM_WEB_PORT}
aeron.dir=${APP_DIR}/shared/state/aeron
benchmark.mode.enabled=false
EOF
}

write_env_files() {
    cat <<EOF > "${STAGING_DIR}/shared/env/simulator.env"
SIMULATOR_LOG_DIR=${APP_DIR}/shared/logs/simulator
JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms${SIM_HEAP_XMS} -Xmx${SIM_HEAP_XMX} -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Daeron.dir=${APP_DIR}/shared/state/aeron -Daeron.ipc.term.buffer.length=8388608 -Daeron.threading.mode=SHARED -Daeron.shared.idle.strategy=backoff -Dagrona.disable.bounds.checks=true"
CPU_AFFINITY=${SIM_CPU_PINNING}
EOF

    cat <<EOF > "${STAGING_DIR}/shared/env/client.env"
JAVA_OPTS="-Xms${CLIENT_HEAP_XMS} -Xmx${CLIENT_HEAP_XMX}"
CPU_AFFINITY=${CLIENT_CPU_PINNING}
THEFIX_CLIENT_PORT=${CLIENT_WEB_PORT}
THEFIX_FIX_HOST=127.0.0.1
THEFIX_FIX_PORT=${FIX_PORT}
THEFIX_FIX_LOG_DIR=${APP_DIR}/shared/state/quickfixj
THEFIX_FIX_RAW_LOGGING_ENABLED=false
EOF
}

write_shared_wrapper_scripts() {
    cat <<'EOF' > "${STAGING_DIR}/shared/bin/run-simulator.sh"
#!/usr/bin/env bash
set -euo pipefail
APP_DIR='__APP_DIR__'
ENV_FILE="${APP_DIR}/shared/env/simulator.env"
if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
fi
cd "${APP_DIR}/shared"
read -r -a java_opts_arr <<< "${JAVA_OPTS:-}"
java_cmd=("${JAVA_BIN:-/usr/bin/java}"
    "${java_opts_arr[@]}"
    -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
    -Dllexsim.log.dir="${SIMULATOR_LOG_DIR:-${APP_DIR}/shared/logs/simulator}"
    -Dllexsim.log.name=llexsimulator
    --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
    --add-opens=java.base/java.nio=ALL-UNNAMED
    --add-opens=java.base/java.lang=ALL-UNNAMED
    -jar "${APP_DIR}/current/simulator/llexsimulator.jar")
if [[ -n "${CPU_AFFINITY:-}" ]] && command -v taskset >/dev/null 2>&1; then
    exec taskset -c "${CPU_AFFINITY}" "${java_cmd[@]}"
fi
exec "${java_cmd[@]}"
EOF

    cat <<'EOF' > "${STAGING_DIR}/shared/bin/run-client.sh"
#!/usr/bin/env bash
set -euo pipefail
APP_DIR='__APP_DIR__'
ENV_FILE="${APP_DIR}/shared/env/client.env"
if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
fi
cd "${APP_DIR}/current/client/TheFixClient"
export JAVA_OPTS="${JAVA_OPTS:-}"
if [[ -n "${CPU_AFFINITY:-}" ]] && command -v taskset >/dev/null 2>&1; then
    exec taskset -c "${CPU_AFFINITY}" ./bin/TheFixClient
fi
exec ./bin/TheFixClient
EOF

    sed -i.bak "s|__APP_DIR__|${APP_DIR}|g" "${STAGING_DIR}/shared/bin/run-simulator.sh" "${STAGING_DIR}/shared/bin/run-client.sh"
    rm -f "${STAGING_DIR}/shared/bin/run-simulator.sh.bak" "${STAGING_DIR}/shared/bin/run-client.sh.bak"
    chmod 0755 "${STAGING_DIR}/shared/bin/run-simulator.sh" "${STAGING_DIR}/shared/bin/run-client.sh"
}

write_release_scripts() {
    cat <<'EOF' > "${STAGING_DIR}/scripts/start-fixsimulator.sh"
#!/usr/bin/env bash
set -euo pipefail
if [[ "$(id -u)" -eq 0 ]]; then
    exec systemctl start myfix-simulator.service
fi
exec sudo systemctl start myfix-simulator.service
EOF

    cat <<'EOF' > "${STAGING_DIR}/scripts/stop-fixsimulator.sh"
#!/usr/bin/env bash
set -euo pipefail
if [[ "$(id -u)" -eq 0 ]]; then
    exec systemctl stop myfix-simulator.service
fi
exec sudo systemctl stop myfix-simulator.service
EOF

    cat <<'EOF' > "${STAGING_DIR}/scripts/start-fixclient.sh"
#!/usr/bin/env bash
set -euo pipefail
if [[ "$(id -u)" -eq 0 ]]; then
    exec systemctl start myfix-client.service
fi
exec sudo systemctl start myfix-client.service
EOF

    cat <<'EOF' > "${STAGING_DIR}/scripts/stop-fixclient.sh"
#!/usr/bin/env bash
set -euo pipefail
if [[ "$(id -u)" -eq 0 ]]; then
    exec systemctl stop myfix-client.service
fi
exec sudo systemctl stop myfix-client.service
EOF

    cat <<'EOF' > "${STAGING_DIR}/scripts/start-all.sh"
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/start-fixsimulator.sh"
"${SCRIPT_DIR}/start-fixclient.sh"
EOF

    cat <<'EOF' > "${STAGING_DIR}/scripts/stop-all.sh"
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/stop-fixclient.sh"
"${SCRIPT_DIR}/stop-fixsimulator.sh"
EOF

    chmod 0755 "${STAGING_DIR}/scripts/"*.sh
}

prepare_local_payload() {
    prepare_staging_dir
    write_simulator_config
    write_env_files
    write_shared_wrapper_scripts
    write_release_scripts
}

remote_exec() {
    ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -i "${SSH_KEY_PATH}" "$(ssh_target)" "$@"
}

ensure_remote_dirs() {
    local remote_cmd
    remote_cmd=$(cat <<EOF
set -euo pipefail
install -d "${APP_DIR}/releases/${RELEASE_ID}/simulator" \
           "${APP_DIR}/releases/${RELEASE_ID}/client/TheFixClient" \
           "${APP_DIR}/releases/${RELEASE_ID}/scripts" \
           "${APP_DIR}/shared/bin" \
           "${APP_DIR}/shared/config" \
           "${APP_DIR}/shared/env"
EOF
)

    if [[ "${DRY_RUN}" == true ]]; then
        banner "Dry Run: Remote Directory Prep"
        printf '%s\n' "${remote_cmd}"
        return 0
    fi

    printf '%s\n' "${remote_cmd}" | remote_exec bash -s --
}

upload_release_files() {
    local ssh_rsh
    local -a rsync_common=(--archive --compress --human-readable --partial --no-owner --no-group)
    ssh_rsh="ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ServerAliveCountMax=6 -i ${SSH_KEY_PATH}"

    if [[ "${DRY_RUN}" == true ]]; then
        banner "Dry Run: Rsync Commands"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group %q %q:%q\n' "${SIMULATOR_JAR}" "$(ssh_target)" "${APP_DIR}/releases/${RELEASE_ID}/simulator/llexsimulator.jar"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group --delete %q %q:%q\n' "${CLIENT_INSTALL_DIR}/" "$(ssh_target)" "${APP_DIR}/releases/${RELEASE_ID}/client/TheFixClient/"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group %q %q:%q\n' "${STAGING_DIR}/shared/config/simulator.properties" "$(ssh_target)" "${APP_DIR}/shared/config/simulator.properties"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group %q %q:%q\n' "${STAGING_DIR}/shared/env/simulator.env" "$(ssh_target)" "${APP_DIR}/shared/env/simulator.env"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group %q %q:%q\n' "${STAGING_DIR}/shared/env/client.env" "$(ssh_target)" "${APP_DIR}/shared/env/client.env"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group --delete %q %q:%q\n' "${STAGING_DIR}/shared/bin/" "$(ssh_target)" "${APP_DIR}/shared/bin/"
        printf 'rsync --archive --compress --human-readable --partial --no-owner --no-group --delete %q %q:%q\n' "${STAGING_DIR}/scripts/" "$(ssh_target)" "${APP_DIR}/releases/${RELEASE_ID}/scripts/"
        return 0
    fi

    info "Uploading TheFixSimulator jar to $(ssh_target):${APP_DIR}/releases/${RELEASE_ID}/simulator/"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" "${SIMULATOR_JAR}" "$(ssh_target):${APP_DIR}/releases/${RELEASE_ID}/simulator/llexsimulator.jar"

    info "Uploading TheFixClient installDist to $(ssh_target):${APP_DIR}/releases/${RELEASE_ID}/client/TheFixClient/"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" --delete "${CLIENT_INSTALL_DIR}/" "$(ssh_target):${APP_DIR}/releases/${RELEASE_ID}/client/TheFixClient/"

    info "Uploading shared simulator config"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" "${STAGING_DIR}/shared/config/simulator.properties" "$(ssh_target):${APP_DIR}/shared/config/simulator.properties"

    info "Uploading simulator environment file"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" "${STAGING_DIR}/shared/env/simulator.env" "$(ssh_target):${APP_DIR}/shared/env/simulator.env"

    info "Uploading client environment file"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" "${STAGING_DIR}/shared/env/client.env" "$(ssh_target):${APP_DIR}/shared/env/client.env"

    info "Uploading shared runtime wrapper scripts"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" --delete "${STAGING_DIR}/shared/bin/" "$(ssh_target):${APP_DIR}/shared/bin/"

    info "Uploading release control scripts"
    RSYNC_RSH="${ssh_rsh}" rsync "${rsync_common[@]}" --delete "${STAGING_DIR}/scripts/" "$(ssh_target):${APP_DIR}/releases/${RELEASE_ID}/scripts/"
}

build_remote_finalize_script() {
    cat <<EOF
set -euo pipefail

APP_DIR='${APP_DIR}'
APP_USER='${APP_USER}'
SIM_HOSTNAME='${SIM_HOSTNAME}'
CLIENT_HOSTNAME='${CLIENT_HOSTNAME}'
CERTBOT_EMAIL='${CERTBOT_EMAIL}'
SIM_WEB_PORT='${SIM_WEB_PORT}'
CLIENT_WEB_PORT='${CLIENT_WEB_PORT}'
FIX_PORT='${FIX_PORT}'
WAIT_SECONDS='${WAIT_SECONDS}'
KEEP_RELEASES='${KEEP_RELEASES}'
RELEASE_ID='${RELEASE_ID}'
SKIP_CERTBOT='${SKIP_CERTBOT}'

log() {
    printf '[remote] %s\n' "\$*"
}

run_root() {
    if [[ "\$(id -u)" -eq 0 ]]; then
        "\$@"
    else
        sudo "\$@"
    fi
}

require_root_access() {
    if [[ "\$(id -u)" -eq 0 ]]; then
        return 0
    fi
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n true >/dev/null 2>&1; then
        echo 'This deployment step requires root or passwordless sudo.' >&2
        exit 1
    fi
}

wait_for_http() {
    local label="\$1"
    local url="\$2"
    local max_wait="\$3"
    local waited=0
    while (( waited < max_wait )); do
        if curl -fsS "\${url}" >/dev/null 2>&1; then
            log "\${label} is ready"
            return 0
        fi
        sleep 2
        waited=\$((waited + 2))
    done
    echo "Timed out waiting for \${label} at \${url}" >&2
    return 1
}

wait_for_tcp() {
    local label="\$1"
    local host="\$2"
    local port="\$3"
    local max_wait="\$4"
    local waited=0
    while (( waited < max_wait )); do
        if python3 - "\${host}" "\${port}" <<'PY' >/dev/null 2>&1
import socket, sys
host = sys.argv[1]
port = int(sys.argv[2])
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.settimeout(1.0)
    try:
        sock.connect((host, port))
    except OSError:
        raise SystemExit(1)
raise SystemExit(0)
PY
        then
            log "\${label} is ready"
            return 0
        fi
        sleep 2
        waited=\$((waited + 2))
    done
    echo "Timed out waiting for \${label} at \${host}:\${port}" >&2
    return 1
}

install_nginx_site() {
    local site_name="\$1"
    local hostname="\$2"
    local port="\$3"
    local conf_path="/etc/nginx/sites-available/\${site_name}.conf"
    local enabled_path="/etc/nginx/sites-enabled/\${site_name}.conf"

    cat <<NGINX | run_root tee "\${conf_path}" >/dev/null
server {
    listen 80;
    listen [::]:80;
    server_name \${hostname};

    client_max_body_size 16m;

    location / {
        proxy_pass http://127.0.0.1:\${port};
        proxy_http_version 1.1;
        proxy_set_header Host \\\$host;
        proxy_set_header X-Real-IP \\\$remote_addr;
        proxy_set_header X-Forwarded-For \\\$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \\\$scheme;
        proxy_set_header Upgrade \\\$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
NGINX

    run_root ln -sfn "\${conf_path}" "\${enabled_path}"
}

ensure_certificate() {
    local hostname="\$1"
    local -a email_args=()
    if [[ -n "\${CERTBOT_EMAIL}" ]]; then
        email_args=(--email "\${CERTBOT_EMAIL}")
    else
        email_args=(--register-unsafely-without-email)
    fi

    run_root certbot --nginx \
        --non-interactive \
        --agree-tos \
        --redirect \
        --keep-until-expiring \
        "\${email_args[@]}" \
        -d "\${hostname}"
}

verify_https_vhost() {
    local hostname="\$1"
    curl -fsS --resolve "\${hostname}:443:127.0.0.1" "https://\${hostname}/api/health" >/dev/null
}

prune_old_releases() {
    if [[ "\${KEEP_RELEASES}" -le 0 ]]; then
        return 0
    fi

    mapfile -t releases < <(find "\${APP_DIR}/releases" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r)
    if [[ "\${#releases[@]}" -le "\${KEEP_RELEASES}" ]]; then
        return 0
    fi

    for old_release in "\${releases[@]:\${KEEP_RELEASES}}"; do
        if [[ "\${old_release}" == "\${RELEASE_ID}" ]]; then
            continue
        fi
        log "Pruning old release \${old_release}"
        run_root rm -rf "\${APP_DIR}/releases/\${old_release}"
    done
}

require_root_access
APP_GROUP="\$(id -gn "\${APP_USER}")"
run_root chown -R "\${APP_USER}:\${APP_GROUP}" "\${APP_DIR}/releases/\${RELEASE_ID}" "\${APP_DIR}/shared/bin" "\${APP_DIR}/shared/config" "\${APP_DIR}/shared/env" "\${APP_DIR}/shared/logs" "\${APP_DIR}/shared/state"
run_root ln -sfn "\${APP_DIR}/releases/\${RELEASE_ID}" "\${APP_DIR}/current"
run_root rm -f /etc/nginx/sites-enabled/default
install_nginx_site myfix-simulator "\${SIM_HOSTNAME}" "\${SIM_WEB_PORT}"
install_nginx_site myfix-client "\${CLIENT_HOSTNAME}" "\${CLIENT_WEB_PORT}"
run_root nginx -t
run_root systemctl daemon-reload
run_root systemctl enable myfix-simulator.service myfix-client.service >/dev/null
run_root systemctl restart myfix-simulator.service
wait_for_http 'TheFixSimulator' "http://127.0.0.1:\${SIM_WEB_PORT}/api/health" "\${WAIT_SECONDS}"
wait_for_tcp 'TheFixSimulator FIX listener' 127.0.0.1 "\${FIX_PORT}" "\${WAIT_SECONDS}"
run_root systemctl restart myfix-client.service
wait_for_http 'TheFixClient' "http://127.0.0.1:\${CLIENT_WEB_PORT}/api/health" "\${WAIT_SECONDS}"
run_root systemctl reload nginx

if [[ "\${SKIP_CERTBOT}" != 'true' ]]; then
    ensure_certificate "\${SIM_HOSTNAME}"
    ensure_certificate "\${CLIENT_HOSTNAME}"
    verify_https_vhost "\${SIM_HOSTNAME}"
    verify_https_vhost "\${CLIENT_HOSTNAME}"
fi

prune_old_releases

log "Release deployed: \${RELEASE_ID}"
log "Simulator URL: https://\${SIM_HOSTNAME}"
log "Client URL: https://\${CLIENT_HOSTNAME}"
log "FIX listener: 127.0.0.1:\${FIX_PORT}"
EOF
}

run_remote_finalize() {
    if [[ "${DRY_RUN}" == true ]]; then
        banner "Dry Run: Remote Finalize Script"
        echo "----------------------------------------"
        build_remote_finalize_script
        echo "----------------------------------------"
        return 0
    fi

    build_remote_finalize_script | remote_exec bash -s --
}

show_plan() {
    banner "MyFix Native Deploy"
    info "Project root: ${PROJECT_ROOT}"
    info "Target: $(ssh_target)"
    info "Remote app dir: ${APP_DIR}"
    info "App runtime user: ${APP_USER}"
    info "Release id: ${RELEASE_ID}"
    info "Simulator hostname: ${SIM_HOSTNAME}"
    info "Client hostname: ${CLIENT_HOSTNAME}"
    info "Let's Encrypt email: ${CERTBOT_EMAIL}"
    info "Simulator backend: 127.0.0.1:${SIM_WEB_PORT}"
    info "Client backend: 127.0.0.1:${CLIENT_WEB_PORT}"
    info "FIX listener: 127.0.0.1:${FIX_PORT}"
    info "Simulator heap: ${SIM_HEAP_XMS}/${SIM_HEAP_XMX}"
    info "Client heap: ${CLIENT_HEAP_XMS}/${CLIENT_HEAP_XMX}"
    info "Simulator CPU pinning: ${SIM_CPU_PINNING:-off}"
    info "Client CPU pinning: ${CLIENT_CPU_PINNING:-off}"
    if [[ "${SKIP_CERTBOT}" == true ]]; then
        warn "Certbot step will be skipped"
    fi
}

cleanup_local_staging() {
    if [[ -n "${STAGING_DIR}" && -d "${STAGING_DIR}" ]]; then
        rm -rf "${STAGING_DIR}"
    fi
}

main() {
    parse_args "$@"
    compute_release_id
    validate_inputs
    build_local_artifacts
    prepare_local_payload
    show_plan
    ensure_remote_dirs
    upload_release_files
    run_remote_finalize
    cleanup_local_staging
    success "Deployment completed."
}

trap cleanup_local_staging EXIT
main "$@"



