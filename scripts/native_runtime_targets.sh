#!/usr/bin/env bash

if [[ -n "${MYFIX_NATIVE_RUNTIME_TARGETS_SOURCED:-}" ]]; then
    return 0
fi
MYFIX_NATIVE_RUNTIME_TARGETS_SOURCED=1

NATIVE_RUNTIME_TARGETS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_RUNTIME_TARGETS_PROJECT_ROOT="$(cd "${NATIVE_RUNTIME_TARGETS_DIR}/.." && pwd)"
FIXSIM_TARGET_PROPERTIES_FILE="${FIXSIM_TARGET_PROPERTIES_FILE:-${NATIVE_RUNTIME_TARGETS_PROJECT_ROOT}/TheFixSimulator/config/target-fixsim.properties}"
FIXCLIENT_TARGET_PROPERTIES_FILE="${FIXCLIENT_TARGET_PROPERTIES_FILE:-${NATIVE_RUNTIME_TARGETS_PROJECT_ROOT}/TheFixClient/config/target-fixclient.properties}"

native_runtime_target_property() {
    local file_path="$1"
    local key="$2"
    local default_value="${3:-}"

    if [[ ! -f "${file_path}" ]]; then
        printf '%s\n' "${default_value}"
        return 0
    fi

    python3 - "$file_path" "$key" "$default_value" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
key = sys.argv[2]
default = sys.argv[3]

value = default
for raw_line in path.read_text().splitlines():
    line = raw_line.strip()
    if not line or line.startswith('#') or '=' not in raw_line:
        continue
    current_key, current_value = raw_line.split('=', 1)
    if current_key.strip() == key:
        value = current_value.strip()
        break
print(value)
PY
}

native_runtime_targets_load() {
    export FIXSIM_TARGET_HEAP_XMS="$(native_runtime_target_property "${FIXSIM_TARGET_PROPERTIES_FILE}" 'java.heap.min' '')"
    export FIXSIM_TARGET_HEAP_XMX="$(native_runtime_target_property "${FIXSIM_TARGET_PROPERTIES_FILE}" 'java.heap.max' '')"
    export FIXSIM_TARGET_CPU_PINNING="$(native_runtime_target_property "${FIXSIM_TARGET_PROPERTIES_FILE}" 'cpu.pinning' '')"

    export FIXCLIENT_TARGET_HEAP_XMS="$(native_runtime_target_property "${FIXCLIENT_TARGET_PROPERTIES_FILE}" 'java.heap.min' '')"
    export FIXCLIENT_TARGET_HEAP_XMX="$(native_runtime_target_property "${FIXCLIENT_TARGET_PROPERTIES_FILE}" 'java.heap.max' '')"
    export FIXCLIENT_TARGET_CPU_PINNING="$(native_runtime_target_property "${FIXCLIENT_TARGET_PROPERTIES_FILE}" 'cpu.pinning' '')"
}

