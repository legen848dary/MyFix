#!/usr/bin/env bash
# =============================================================================
# setup_native_droplet.sh — Prepare an Ubuntu droplet for native MyFix services
# =============================================================================
# Usage:
#   ./scripts/setup_native_droplet.sh <host-or-ip> <ssh-key-path-or-glob> [ssh-user] [options]
#
# Default model:
#   - SSH / bootstrap as root
#   - Run TheFixSimulator and TheFixClient as a normal Linux user (default: djs)
#   - Install Java 21, nginx, certbot, systemd units, and runtime directories
#   - Keep web backends private behind nginx + firewall rules
# =============================================================================

set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DROPLET_HOST=""
SSH_KEY_PATH=""
DROPLET_USER="root"
APP_DIR="${APP_DIR:-/opt/myfix}"
APP_USER="${APP_USER:-djs}"
SIM_WEB_PORT="${SIM_WEB_PORT:-8080}"
CLIENT_WEB_PORT="${CLIENT_WEB_PORT:-8081}"
FIX_PORT="${FIX_PORT:-9880}"
SWAP_SIZE_MB="${SWAP_SIZE_MB:-2048}"
ENABLE_FIREWALL=true
ENABLE_FAIL2BAN=true
DRY_RUN=false

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
${BOLD}${CYAN}Bootstrap a DigitalOcean Ubuntu droplet for native MyFix deployment${RESET}

${BOLD}Usage:${RESET}
  ./scripts/${SCRIPT_NAME} <host-or-ip> <ssh-key-path-or-glob> [ssh-user] [options]

${BOLD}Positional arguments:${RESET}
  ${GREEN}host-or-ip${RESET}                 Droplet hostname or IP address
  ${GREEN}ssh-key-path-or-glob${RESET}      Private key path or glob (example: ~/.ssh/id_rsa_ai*)
  ${GREEN}ssh-user${RESET}                   SSH user (default: root)

${BOLD}Options:${RESET}
  ${GREEN}--app-dir <path>${RESET}            Remote application root (default: ${APP_DIR})
  ${GREEN}--app-user <name>${RESET}           Linux user that will run both apps (default: ${APP_USER})
  ${GREEN}--sim-web-port <port>${RESET}       TheFixSimulator web port (default: ${SIM_WEB_PORT})
  ${GREEN}--client-web-port <port>${RESET}    TheFixClient web port (default: ${CLIENT_WEB_PORT})
  ${GREEN}--fix-port <port>${RESET}           Simulator FIX port (default: ${FIX_PORT})
  ${GREEN}--swap-mb <mb>${RESET}              Create swap if missing (default: ${SWAP_SIZE_MB})
  ${GREEN}--skip-firewall${RESET}             Do not configure UFW
  ${GREEN}--skip-fail2ban${RESET}             Do not install/enable fail2ban
  ${GREEN}--dry-run${RESET}                   Print the SSH command and remote bootstrap script only
  ${GREEN}help${RESET}                        Show this help

${BOLD}Examples:${RESET}
  ./scripts/${SCRIPT_NAME} 206.189.92.17 ~/.ssh/id_rsa_ai* root
  ./scripts/${SCRIPT_NAME} 206.189.92.17 ~/.ssh/id_rsa_ai* root --app-user djs --dry-run

${BOLD}What this script does:${RESET}
  - verifies Ubuntu + root/sudo access
  - creates ${APP_USER} if missing
  - installs Java 21, nginx, certbot, rsync, curl, git, jq, python3, ufw
  - creates ${APP_DIR}/shared and ${APP_DIR}/releases directory layout
  - writes systemd units for MyFix services
  - writes wrapper scripts that will run the apps as ${APP_USER}
  - enables nginx and prepares the machine for the deployment script
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
            --swap-mb)
                require_option_value "$1" "$#"
                SWAP_SIZE_MB="$2"
                shift 2
                ;;
            --skip-firewall)
                ENABLE_FIREWALL=false
                shift
                ;;
            --skip-fail2ban)
                ENABLE_FAIL2BAN=false
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

validate_inputs() {
    [[ -n "${DROPLET_HOST}" ]] || { error "Droplet host cannot be empty."; exit 1; }
    [[ -n "${APP_DIR}" ]] || { error "App dir cannot be empty."; exit 1; }
    [[ -n "${APP_USER}" ]] || { error "App user cannot be empty."; exit 1; }
    [[ "${SIM_WEB_PORT}" =~ ^[0-9]+$ ]] || { error "--sim-web-port must be numeric."; exit 1; }
    [[ "${CLIENT_WEB_PORT}" =~ ^[0-9]+$ ]] || { error "--client-web-port must be numeric."; exit 1; }
    [[ "${FIX_PORT}" =~ ^[0-9]+$ ]] || { error "--fix-port must be numeric."; exit 1; }
    [[ "${SWAP_SIZE_MB}" =~ ^[0-9]+$ ]] || { error "--swap-mb must be numeric."; exit 1; }

    require_local_binary ssh

    if [[ "${DRY_RUN}" == false && ( -z "${SSH_KEY_PATH}" || ! -f "${SSH_KEY_PATH}" ) ]]; then
        error "SSH private key not found: ${SSH_KEY_PATH_RAW}"
        exit 1
    fi

    if [[ "${DRY_RUN}" == true && ( -z "${SSH_KEY_PATH}" || ! -f "${SSH_KEY_PATH}" ) ]]; then
        warn "Dry-run mode: resolved private key is missing locally (${SSH_KEY_PATH_RAW}); continuing anyway."
        SSH_KEY_PATH="${SSH_KEY_PATH_RAW}"
    fi
}

ssh_target() {
    printf '%s@%s' "${DROPLET_USER}" "${DROPLET_HOST}"
}

build_remote_script() {
    cat <<EOF
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
APP_DIR='${APP_DIR}'
APP_USER='${APP_USER}'
SIM_WEB_PORT='${SIM_WEB_PORT}'
CLIENT_WEB_PORT='${CLIENT_WEB_PORT}'
FIX_PORT='${FIX_PORT}'
SWAP_SIZE_MB='${SWAP_SIZE_MB}'
ENABLE_FIREWALL='${ENABLE_FIREWALL}'
ENABLE_FAIL2BAN='${ENABLE_FAIL2BAN}'

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
    if ! command -v sudo >/dev/null 2>&1; then
        echo 'This bootstrap requires root or a passwordless sudo-capable user.' >&2
        exit 1
    fi
    if ! sudo -n true >/dev/null 2>&1; then
        echo 'Passwordless sudo is required when not using root.' >&2
        exit 1
    fi
}

ensure_swap() {
    if swapon --noheadings --show=NAME 2>/dev/null | grep -q '.'; then
        log 'Swap already active; leaving existing swap configuration unchanged.'
        return 0
    fi

    local swap_file='/swapfile'
    log "No active swap detected; provisioning \${SWAP_SIZE_MB} MB at \${swap_file}."
    if [[ ! -f "\${swap_file}" ]]; then
        if command -v fallocate >/dev/null 2>&1; then
            if ! run_root fallocate -l "\${SWAP_SIZE_MB}M" "\${swap_file}"; then
                run_root dd if=/dev/zero of="\${swap_file}" bs=1M count="\${SWAP_SIZE_MB}" status=progress
            fi
        else
            run_root dd if=/dev/zero of="\${swap_file}" bs=1M count="\${SWAP_SIZE_MB}" status=progress
        fi
    fi
    run_root chmod 600 "\${swap_file}"
    run_root mkswap "\${swap_file}" >/dev/null
    run_root swapon "\${swap_file}"
    if ! grep -q '^/swapfile ' /etc/fstab; then
        printf '%s\n' '/swapfile none swap sw 0 0' | run_root tee -a /etc/fstab >/dev/null
    fi
}

ensure_app_user() {
    if id -u "\${APP_USER}" >/dev/null 2>&1; then
        log "App user already exists: \${APP_USER}"
        return 0
    fi
    log "Creating app user \${APP_USER}"
    run_root useradd --create-home --shell /bin/bash "\${APP_USER}"
}

ensure_base_packages() {
    run_root apt-get update
    run_root apt-get install -y ca-certificates curl gnupg git rsync jq unzip tar python3 ufw nginx certbot python3-certbot-nginx
    if [[ "\${ENABLE_FAIL2BAN}" == 'true' ]]; then
        run_root apt-get install -y fail2ban
    fi
}

ensure_java21() {
    if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
        log 'Java 21 already installed.'
        return 0
    fi

    local pkg=''
    for candidate in openjdk-21-jdk-headless openjdk-21-jre-headless openjdk-21-jdk; do
        if run_root apt-cache show "\${candidate}" >/dev/null 2>&1; then
            pkg="\${candidate}"
            break
        fi
    done

    if [[ -z "\${pkg}" ]]; then
        echo 'Could not find an OpenJDK 21 package in apt repositories.' >&2
        exit 1
    fi

    log "Installing Java package: \${pkg}"
    run_root apt-get install -y "\${pkg}"
}

ensure_layout() {
    local app_group
    app_group="\$(id -gn "\${APP_USER}")"

    run_root install -d -m 0755 -o "\${APP_USER}" -g "\${app_group}" \
        "\${APP_DIR}" \
        "\${APP_DIR}/releases" \
        "\${APP_DIR}/shared" \
        "\${APP_DIR}/shared/bin" \
        "\${APP_DIR}/shared/config" \
        "\${APP_DIR}/shared/env" \
        "\${APP_DIR}/shared/logs" \
        "\${APP_DIR}/shared/logs/simulator" \
        "\${APP_DIR}/shared/logs/client" \
        "\${APP_DIR}/shared/state" \
        "\${APP_DIR}/shared/state/aeron" \
        "\${APP_DIR}/shared/state/artio" \
        "\${APP_DIR}/shared/state/quickfixj"

    run_root chown -R "\${APP_USER}:\${app_group}" "\${APP_DIR}"
}

write_wrapper_scripts() {
    cat <<'SIMWRAP' | run_root tee "\${APP_DIR}/shared/bin/run-simulator.sh" >/dev/null
#!/usr/bin/env bash
set -euo pipefail
APP_DIR='__APP_DIR__'
ENV_FILE="\${APP_DIR}/shared/env/simulator.env"
if [[ -f "\${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "\${ENV_FILE}"
    set +a
fi
cd "\${APP_DIR}/shared"
read -r -a java_opts_arr <<< "\${JAVA_OPTS:-}"
exec "\${JAVA_BIN:-/usr/bin/java}" \
    "\${java_opts_arr[@]}" \
    -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
    -Dllexsim.log.dir="\${SIMULATOR_LOG_DIR:-\${APP_DIR}/shared/logs/simulator}" \
    -Dllexsim.log.name=llexsimulator \
    --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -jar "\${APP_DIR}/current/simulator/llexsimulator.jar"
SIMWRAP

    cat <<'CLIENTWRAP' | run_root tee "\${APP_DIR}/shared/bin/run-client.sh" >/dev/null
#!/usr/bin/env bash
set -euo pipefail
APP_DIR='__APP_DIR__'
ENV_FILE="\${APP_DIR}/shared/env/client.env"
if [[ -f "\${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "\${ENV_FILE}"
    set +a
fi
cd "\${APP_DIR}/current/client/TheFixClient"
export JAVA_OPTS="\${JAVA_OPTS:-}"
exec ./bin/TheFixClient
CLIENTWRAP

    run_root sed -i "s|__APP_DIR__|\${APP_DIR}|g" "\${APP_DIR}/shared/bin/run-simulator.sh" "\${APP_DIR}/shared/bin/run-client.sh"
    run_root chmod 0755 "\${APP_DIR}/shared/bin/run-simulator.sh" "\${APP_DIR}/shared/bin/run-client.sh"
}

write_systemd_units() {
    local app_group
    app_group="\$(id -gn "\${APP_USER}")"

    cat <<UNIT | run_root tee /etc/systemd/system/myfix-simulator.service >/dev/null
[Unit]
Description=MyFix TheFixSimulator
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=\${APP_USER}
Group=\${app_group}
WorkingDirectory=\${APP_DIR}/shared
ExecStart=\${APP_DIR}/shared/bin/run-simulator.sh
Restart=on-failure
RestartSec=2
LimitNOFILE=65535
Environment=HOME=/home/\${APP_USER}

[Install]
WantedBy=multi-user.target
UNIT

    cat <<UNIT | run_root tee /etc/systemd/system/myfix-client.service >/dev/null
[Unit]
Description=MyFix TheFixClient
After=network-online.target myfix-simulator.service
Wants=network-online.target

[Service]
Type=simple
User=\${APP_USER}
Group=\${app_group}
WorkingDirectory=\${APP_DIR}/shared
ExecStart=\${APP_DIR}/shared/bin/run-client.sh
Restart=on-failure
RestartSec=2
LimitNOFILE=65535
Environment=HOME=/home/\${APP_USER}

[Install]
WantedBy=multi-user.target
UNIT

    run_root systemctl daemon-reload
    run_root systemctl enable nginx >/dev/null
}

write_placeholder_env_files() {
    if [[ ! -f "\${APP_DIR}/shared/env/simulator.env" ]]; then
        cat <<ENV | run_root tee "\${APP_DIR}/shared/env/simulator.env" >/dev/null
SIMULATOR_LOG_DIR=\${APP_DIR}/shared/logs/simulator
JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Daeron.dir=\${APP_DIR}/shared/state/aeron -Daeron.ipc.term.buffer.length=8388608 -Daeron.threading.mode=SHARED -Daeron.shared.idle.strategy=backoff -Dagrona.disable.bounds.checks=true"
ENV
    fi

    if [[ ! -f "\${APP_DIR}/shared/env/client.env" ]]; then
        cat <<ENV | run_root tee "\${APP_DIR}/shared/env/client.env" >/dev/null
JAVA_OPTS="-Xms256m -Xmx512m"
THEFIX_CLIENT_PORT=\${CLIENT_WEB_PORT}
THEFIX_FIX_HOST=127.0.0.1
THEFIX_FIX_PORT=\${FIX_PORT}
THEFIX_FIX_LOG_DIR=\${APP_DIR}/shared/state/quickfixj
THEFIX_FIX_RAW_LOGGING_ENABLED=false
ENV
    fi

    run_root chown "\${APP_USER}:\$(id -gn "\${APP_USER}")" "\${APP_DIR}/shared/env/simulator.env" "\${APP_DIR}/shared/env/client.env"
}

configure_firewall() {
    if [[ "\${ENABLE_FIREWALL}" != 'true' ]]; then
        log 'Skipping UFW configuration.'
        return 0
    fi
    run_root ufw allow OpenSSH >/dev/null
    run_root ufw allow 80/tcp >/dev/null
    run_root ufw allow 443/tcp >/dev/null
    run_root ufw --force enable >/dev/null
}

enable_services() {
    run_root systemctl enable --now nginx >/dev/null
    if [[ "\${ENABLE_FAIL2BAN}" == 'true' ]]; then
        run_root systemctl enable --now fail2ban >/dev/null
    fi
}

show_summary() {
    log 'Bootstrap summary:'
    java -version 2>&1 | sed 's/^/[remote] /'
    printf '[remote] app_dir=%s\n' "\${APP_DIR}"
    printf '[remote] app_user=%s\n' "\${APP_USER}"
    printf '[remote] simulator_web_port=%s\n' "\${SIM_WEB_PORT}"
    printf '[remote] client_web_port=%s\n' "\${CLIENT_WEB_PORT}"
    printf '[remote] fix_port=%s\n' "\${FIX_PORT}"
    printf '[remote] nginx_enabled=%s\n' "\$(systemctl is-enabled nginx 2>/dev/null || echo no)"
    printf '[remote] simulator_service_enabled=%s\n' "\$(systemctl is-enabled myfix-simulator.service 2>/dev/null || echo disabled-until-first-deploy)"
    printf '[remote] client_service_enabled=%s\n' "\$(systemctl is-enabled myfix-client.service 2>/dev/null || echo disabled-until-first-deploy)"
}

if [[ ! -r /etc/os-release ]]; then
    echo 'This machine does not look like Linux/Ubuntu.' >&2
    exit 1
fi

. /etc/os-release
if [[ "\${ID:-}" != 'ubuntu' ]]; then
    echo "This bootstrap currently supports Ubuntu only (found: \${ID:-unknown})." >&2
    exit 1
fi

require_root_access
ensure_swap
ensure_app_user
ensure_base_packages
ensure_java21
ensure_layout
write_wrapper_scripts
write_placeholder_env_files
write_systemd_units
configure_firewall
enable_services
show_summary
EOF
}

run_remote_bootstrap() {
    local -a ssh_cmd=(
        ssh
        -o BatchMode=yes
        -o StrictHostKeyChecking=accept-new
        -i "${SSH_KEY_PATH}"
        "$(ssh_target)"
        bash -s --
    )

    if [[ "${DRY_RUN}" == true ]]; then
        banner "Dry Run"
        info "Would bootstrap $(ssh_target) using key ${SSH_KEY_PATH}"
        echo ""
        echo "SSH command:"
        printf '  %q' "${ssh_cmd[@]}"
        echo ""
        echo ""
        echo "Remote bootstrap script:"
        echo "----------------------------------------"
        build_remote_script
        echo "----------------------------------------"
        return 0
    fi

    banner "Bootstrapping Native Droplet"
    info "Target: $(ssh_target)"
    build_remote_script | "${ssh_cmd[@]}"
}

main() {
    parse_args "$@"
    validate_inputs

    banner "MyFix Native Droplet Setup"
    info "Project root: ${PROJECT_ROOT}"
    info "Target: $(ssh_target)"
    info "Remote app dir: ${APP_DIR}"
    info "App runtime user: ${APP_USER}"
    info "Simulator web port: ${SIM_WEB_PORT}"
    info "Client web port: ${CLIENT_WEB_PORT}"
    info "FIX port: ${FIX_PORT}"
    if [[ "${ENABLE_FIREWALL}" == true ]]; then
        info "Firewall: OpenSSH + 80 + 443"
    else
        warn "Firewall changes disabled"
    fi
    if [[ "${ENABLE_FAIL2BAN}" == true ]]; then
        info "Fail2ban: enabled"
    else
        warn "Fail2ban installation disabled"
    fi

    run_remote_bootstrap
    success "Droplet bootstrap completed."
}

main "$@"

