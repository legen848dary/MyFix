#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${SCRIPT_DIR}/run_benchmark_direct_jvm.sh"
BUILD_FIRST=false

usage() {
  cat <<EOF
Usage:
  $(basename "$0") [--build]

Runs the direct-JVM simulator benchmark at 500 msg/s and prints only:
  p50LatencyUs
  p75LatencyUs
  p90LatencyUs
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      BUILD_FIRST=true
      shift
      ;;
    --help|-h|help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

runner_args=(--rate 500)
if [[ "$BUILD_FIRST" == true ]]; then
  runner_args=(--build "${runner_args[@]}")
fi

output="$(bash "$RUNNER" "${runner_args[@]}")"

printf '%s\n' "$output" | awk -F= '
  /^p50LatencyUs=/ { print }
  /^p75LatencyUs=/ { print }
  /^p90LatencyUs=/ { print }
'

