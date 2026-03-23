#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${BENCHMARK_PROJECT_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
RUNNER="${BENCHMARK_RUNNER:-${SCRIPT_DIR}/run_benchmark_direct_jvm.sh}"
GRADLEW="${BENCHMARK_GRADLEW:-${PROJECT_ROOT}/gradlew}"
GIT_BIN="${BENCHMARK_GIT:-git}"
BASELINE_FILE="${PROJECT_ROOT}/LATENCY_BASELINE.md"
BUILD_FIRST=false
UPDATE_BASELINE=false
P90_THRESHOLD_US=10

die() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

extract_metric() {
  local key="$1"
  printf '%s\n' "$output" | awk -F= -v key="$key" '$1 == key { print substr($0, index($0, "=") + 1); exit }'
}

ensure_clean_git_state() {
  local status
  status="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager status --short)"
  [[ -z "$status" ]] || die "Baseline update requires a clean git worktree. Commit/stash changes first."
}

ensure_git_upstream() {
  CURRENT_BRANCH="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --abbrev-ref HEAD)"
  UPSTREAM_REF="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null)" \
    || die "Baseline update requires the current branch to have an upstream configured."
  BENCHMARKED_COMMIT="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse HEAD)"
  BENCHMARKED_COMMIT_SHORT="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --short HEAD)"
}

rewrite_latency_baseline() {
  local verified_on="$1"
  local commit_hash="$2"
  local p50_us="$3"
  local p90_us="$4"
  local artifact_path="$5"

  {
    cat <<EOF
# Latency Baseline Tracker

This file records the last verified commit that stayed under the latency target so we do not lose track while iterating on performance or features.

## Last known good baseline

- Verified on: ${verified_on}
EOF
    printf -- '- Commit hash: `%s`\n' "$commit_hash"
    cat <<EOF
- Benchmark mode: direct JVM, no Docker
- Benchmark rate: \`500 msg/s\`
- p50 latency: \`${p50_us} µs\`
- p90 latency: \`${p90_us} µs\`
EOF
    printf -- '- Benchmark artifact: `%s`\n\n' "$artifact_path"
    cat <<EOF
## Update rule

Only update this file after:
1. running from a clean committed git worktree with an upstream branch configured,
2. rebuilding the simulator,
3. rerunning the direct-JVM benchmark,
4. confirming \`p90 < 100 µs\` at \`500 msg/s\`, and
5. confirming the build is green.

Keep the previous good entry if a later change regresses latency.
EOF
  } > "$BASELINE_FILE"
}

usage() {
  cat <<EOF
Usage:
  $(basename "$0") [--build] [--update-baseline]

Runs the direct-JVM simulator benchmark at 500 msg/s and prints only:
  p50LatencyUs
  p75LatencyUs
  p90LatencyUs

Optional baseline-update mode (\`--update-baseline\`) also:
  - requires a clean git worktree on a committed branch with an upstream
  - confirms the benchmark produced a parsable artifact and \`p90 < ${P90_THRESHOLD_US} µs\`
  - runs \`./gradlew --no-daemon build\`
  - updates \`LATENCY_BASELINE.md\`
  - commits only \`LATENCY_BASELINE.md\`
  - pushes the new baseline commit to the current branch upstream
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b)
      BUILD_FIRST=true
      shift
      ;;
    --update-baseline|-u)
      UPDATE_BASELINE=true
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

if [[ "$UPDATE_BASELINE" == true ]]; then
  [[ -f "$BASELINE_FILE" ]] || die "Baseline file not found: $BASELINE_FILE"
  [[ -x "$GRADLEW" ]] || die "Gradle wrapper not found or not executable: $GRADLEW"
  require_command "$GIT_BIN"
  ensure_clean_git_state
  ensure_git_upstream
fi

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

if [[ "$UPDATE_BASELINE" != true ]]; then
  exit 0
fi

p50_latency_us="$(extract_metric "p50LatencyUs")"
p75_latency_us="$(extract_metric "p75LatencyUs")"
p90_latency_us="$(extract_metric "p90LatencyUs")"
artifact_dir="$(extract_metric "artifact_dir")"

[[ -n "$p50_latency_us" ]] || die "Benchmark output did not include p50LatencyUs"
[[ -n "$p75_latency_us" ]] || die "Benchmark output did not include p75LatencyUs"
[[ -n "$p90_latency_us" ]] || die "Benchmark output did not include p90LatencyUs"
[[ -n "$artifact_dir" ]] || die "Benchmark output did not include artifact_dir"

[[ "$p50_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p50LatencyUs is not an integer: $p50_latency_us"
[[ "$p75_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p75LatencyUs is not an integer: $p75_latency_us"
[[ "$p90_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p90LatencyUs is not an integer: $p90_latency_us"

if (( p90_latency_us >= P90_THRESHOLD_US )); then
  die "Baseline update skipped: p90 latency ${p90_latency_us} µs is not below ${P90_THRESHOLD_US} µs."
fi

echo "Benchmark preconditions satisfied; verifying full build before updating LATENCY_BASELINE.md..." >&2
(cd "$PROJECT_ROOT" && "$GRADLEW" --no-daemon build >/dev/null)

artifact_display="$artifact_dir"
if [[ "$artifact_dir" == "$PROJECT_ROOT"/* ]]; then
  artifact_display="${artifact_dir#"$PROJECT_ROOT"/}"
fi

rewrite_latency_baseline "$(date +%Y-%m-%d)" "$BENCHMARKED_COMMIT" "$p50_latency_us" "$p90_latency_us" "$artifact_display"

if (cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --quiet -- LATENCY_BASELINE.md); then
  echo "LATENCY_BASELINE.md already matches the latest verified benchmark for commit ${BENCHMARKED_COMMIT_SHORT}; nothing to commit." >&2
  exit 0
fi

(cd "$PROJECT_ROOT" && "$GIT_BIN" add LATENCY_BASELINE.md)

staged_files="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --cached --name-only)"
[[ "$staged_files" == "LATENCY_BASELINE.md" ]] || die "Refusing to commit unexpected staged files: ${staged_files}"

unstaged_files="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --name-only)"
[[ -z "$unstaged_files" ]] || die "Refusing to push with unexpected unstaged changes: ${unstaged_files}"

(cd "$PROJECT_ROOT" && "$GIT_BIN" commit -m "Update latency baseline for ${BENCHMARKED_COMMIT_SHORT}" >/dev/null)
(cd "$PROJECT_ROOT" && "$GIT_BIN" push >/dev/null)

echo "Updated LATENCY_BASELINE.md for benchmarked commit ${BENCHMARKED_COMMIT_SHORT} and pushed baseline commit to ${UPSTREAM_REF}." >&2

