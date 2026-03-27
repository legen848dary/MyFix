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
ALLOW_REGRESSION=false
P90_THRESHOLD_US="${BENCHMARK_BASELINE_P90_THRESHOLD_US:-10}"
KNOWN_GOOD_TAG_PREFIX="${BENCHMARK_KNOWN_GOOD_TAG_PREFIX:-latency-known-good}"

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

format_elapsed_duration() {
  local total_seconds="$1"
  local hours=$(( total_seconds / 3600 ))
  local minutes=$(( (total_seconds % 3600) / 60 ))
  local seconds=$(( total_seconds % 60 ))

  if (( hours > 0 )); then
    printf '%dh %dm %ds' "$hours" "$minutes" "$seconds"
  elif (( minutes > 0 )); then
    printf '%dm %ds' "$minutes" "$seconds"
  else
    printf '%ds' "$seconds"
  fi
}

ensure_clean_git_state() {
  local status
  status="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager status --short)"
  [[ -z "$status" ]] || die "Baseline update requires a clean git worktree. Commit/stash changes first."
}

ensure_git_upstream() {
  CURRENT_BRANCH="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --abbrev-ref HEAD)"
  [[ "$CURRENT_BRANCH" != "HEAD" ]] || die "Baseline update requires a named branch, not detached HEAD."
  UPSTREAM_REF="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null)" \
    || die "Baseline update requires the current branch to have an upstream configured."
  UPSTREAM_REMOTE="${UPSTREAM_REF%%/*}"
  BENCHMARKED_COMMIT="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse HEAD)"
  BENCHMARKED_COMMIT_SHORT="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-parse --short HEAD)"
}

metadata_value() {
  local metadata_file="$1"
  local key="$2"
  awk -F= -v key="$key" '$1 == key { print substr($0, index($0, "=") + 1); exit }' "$metadata_file"
}

latest_baseline_p90() {
  python3 - "$BASELINE_FILE" <<'PY'
import sys
from datetime import datetime
from pathlib import Path


def parse_dt(value: str):
    value = value.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S HKT", "%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%SZ"):
        try:
            return datetime.strptime(value, fmt)
        except ValueError:
            pass
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.min

path = Path(sys.argv[1])
if not path.exists():
    print("")
    raise SystemExit(0)

rows = []
for line in path.read_text().splitlines():
    stripped = line.strip()
    if not stripped.startswith("|"):
        continue
    parts = [part.strip() for part in stripped.strip("|").split("|")]
    if len(parts) != 6:
        continue
    if parts[0].lower().startswith("datetime") or set(parts[0]) == {"-", ":"}:
        continue
    rows.append(parts)

rows.sort(key=lambda row: parse_dt(row[0]), reverse=True)
print(rows[0][5] if rows else "")
PY
}

render_baseline_rows() {
  python3 - "$BASELINE_FILE" "$BENCHMARK_STARTED_AT_HKT" "$BENCHMARKED_COMMIT" "$BENCHMARK_RATE" "$P50_LATENCY_US" "$P75_LATENCY_US" "$P90_LATENCY_US" <<'PY'
import sys
from datetime import datetime
from pathlib import Path


def parse_dt(value: str):
    value = value.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S HKT", "%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%SZ"):
        try:
            return datetime.strptime(value, fmt)
        except ValueError:
            pass
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.min

path = Path(sys.argv[1])
new_row = {
    "datetime": sys.argv[2],
    "commit": sys.argv[3],
    "msgs": sys.argv[4],
    "p50": sys.argv[5],
    "p75": sys.argv[6],
    "p90": sys.argv[7],
}

rows = []
if path.exists():
    for line in path.read_text().splitlines():
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        parts = [part.strip() for part in stripped.strip("|").split("|")]
        if len(parts) != 6:
            continue
        if parts[0].lower().startswith("datetime") or set(parts[0]) == {"-", ":"}:
            continue
        rows.append({
            "datetime": parts[0],
            "commit": parts[1].strip("`"),
            "msgs": parts[2],
            "p50": parts[3],
            "p75": parts[4],
            "p90": parts[5],
        })

rows = [row for row in rows if row["commit"] != new_row["commit"]]
rows.append(new_row)
rows.sort(key=lambda row: parse_dt(row["datetime"]), reverse=True)

for row in rows:
    print(f"| {row['datetime']} | `{row['commit']}` | {row['msgs']} | {row['p50']} | {row['p75']} | {row['p90']} |")
PY
}

ensure_known_good_tag() {
  KNOWN_GOOD_TAG="${KNOWN_GOOD_TAG_PREFIX}-${BENCHMARKED_COMMIT_SHORT}"
  local existing_target
  if existing_target="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager rev-list -n 1 "$KNOWN_GOOD_TAG" 2>/dev/null)"; then
    [[ "$existing_target" == "$BENCHMARKED_COMMIT" ]] || die "Existing tag ${KNOWN_GOOD_TAG} points to ${existing_target}, not ${BENCHMARKED_COMMIT}."
    TAG_CREATED=false
    return
  fi
  (cd "$PROJECT_ROOT" && "$GIT_BIN" tag -a "$KNOWN_GOOD_TAG" "$BENCHMARKED_COMMIT" -m "Known-good latency baseline for ${BENCHMARKED_COMMIT_SHORT}")
  TAG_CREATED=true
}

push_known_good_tag() {
  (cd "$PROJECT_ROOT" && "$GIT_BIN" push "$UPSTREAM_REMOTE" "refs/tags/${KNOWN_GOOD_TAG}" >/dev/null)
}

rewrite_latency_baseline() {
  local rows
  rows="$(render_baseline_rows)"
  {
    cat <<EOF
# Latency Baseline Tracker

This file records the accepted direct-JVM latency checkpoints so we do not lose track while iterating on performance or features.

Guarded baseline updates create immutable rollback tags named \`latency-known-good-<commit>\`.

## Baseline history

| Datetime (HKT) | Commit hash | Msg/s | p50 (µs) | p75 (µs) | p90 (µs) |
| --- | --- | ---: | ---: | ---: | ---: |
${rows}

## Update rule

Only update this file after:
1. running from a clean committed git worktree on a named branch with an upstream configured,
2. rerunning the exact direct-JVM 500 msg/s benchmark,
3. confirming the benchmark artifact metadata is present and matches the expected mode/rate,
4. confirming \`p90 < ${P90_THRESHOLD_US} µs\` at \`500 msg/s\`,
5. confirming the root build is green, and
6. confirming the new baseline does not regress versus the latest recorded \`p90\` unless \`--allow-regression\` is used intentionally.

Use the known-good tag for rollback, for example:

\`\`\`bash
git checkout ${KNOWN_GOOD_TAG}
\`\`\`
EOF
  } > "$BASELINE_FILE"
}

usage() {
  cat <<EOF
Usage:
  $(basename "$0") [--build] [--update-baseline] [--allow-regression]

Runs the direct-JVM simulator benchmark at 500 msg/s and prints only:
  p50LatencyUs
  p75LatencyUs
  p90LatencyUs

Optional baseline-update mode (\`--update-baseline\`) also:
  - requires a clean git worktree on a committed branch with an upstream
  - confirms the benchmark produced a parsable metadata artifact for direct-JVM 500 msg/s mode
  - confirms \`p90 < ${P90_THRESHOLD_US} us\`
  - refuses to overwrite a faster latest recorded baseline unless \`--allow-regression\` is set
  - runs \`./gradlew --no-daemon build\`
  - creates or reuses an annotated known-good git tag named \`${KNOWN_GOOD_TAG_PREFIX}-<commit>\`
  - updates \`LATENCY_BASELINE.md\` as a datetime-sorted history table
  - commits only \`LATENCY_BASELINE.md\`
  - pushes the new baseline commit and the known-good tag to the current branch upstream remote

Use \`--allow-regression\` only when you intentionally want to record a slower but still accepted baseline.
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
    --allow-regression)
      ALLOW_REGRESSION=true
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

benchmark_started_epoch="$(date +%s)"
output="$(bash "$RUNNER" "${runner_args[@]}")"
benchmark_finished_epoch="$(date +%s)"
benchmark_elapsed_sec=$(( benchmark_finished_epoch - benchmark_started_epoch ))
benchmark_elapsed_human="$(format_elapsed_duration "$benchmark_elapsed_sec")"

printf '%s\n' "$output" | awk -F= '
  /^p50LatencyUs=/ { print }
  /^p75LatencyUs=/ { print }
  /^p90LatencyUs=/ { print }
'
printf 'Time taken: %s\n' "$benchmark_elapsed_human"

if [[ "$UPDATE_BASELINE" != true ]]; then
  exit 0
fi

BENCHMARK_RATE=500

p50_latency_us="$(extract_metric "p50LatencyUs")"
p75_latency_us="$(extract_metric "p75LatencyUs")"
p90_latency_us="$(extract_metric "p90LatencyUs")"
artifact_dir="$(extract_metric "artifact_dir")"
metadata_file="$(extract_metric "metadata_file")"

[[ -n "$p50_latency_us" ]] || die "Benchmark output did not include p50LatencyUs"
[[ -n "$p75_latency_us" ]] || die "Benchmark output did not include p75LatencyUs"
[[ -n "$p90_latency_us" ]] || die "Benchmark output did not include p90LatencyUs"
[[ -n "$artifact_dir" ]] || die "Benchmark output did not include artifact_dir"
[[ -n "$metadata_file" ]] || die "Benchmark output did not include metadata_file"

[[ "$p50_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p50LatencyUs is not an integer: $p50_latency_us"
[[ "$p75_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p75LatencyUs is not an integer: $p75_latency_us"
[[ "$p90_latency_us" =~ ^[0-9]+$ ]] || die "Parsed p90LatencyUs is not an integer: $p90_latency_us"
[[ -f "$metadata_file" ]] || die "Benchmark metadata file not found: $metadata_file"

benchmark_mode="$(metadata_value "$metadata_file" "mode")"
benchmark_rate="$(metadata_value "$metadata_file" "rate")"
benchmark_started_at_hkt="$(metadata_value "$metadata_file" "benchmark_started_at_hkt")"
benchmark_started_at_utc="$(metadata_value "$metadata_file" "benchmark_started_at_utc")"
benchmark_command="$(metadata_value "$metadata_file" "benchmark_command")"
benchmark_warmup_sec="$(metadata_value "$metadata_file" "warmup_sec")"
benchmark_duration_sec="$(metadata_value "$metadata_file" "duration_sec")"
benchmark_sample_interval_sec="$(metadata_value "$metadata_file" "sample_interval_sec")"
benchmark_wait_strategy="$(metadata_value "$metadata_file" "wait_strategy")"
benchmark_fix_cancel_amend_enabled="$(metadata_value "$metadata_file" "fix_cancel_amend_enabled")"

[[ "$benchmark_mode" == "direct-jvm-warm" ]] || die "Baseline update requires direct-jvm-warm metadata, got: ${benchmark_mode:-<missing>}"
[[ "$benchmark_rate" == "500" ]] || die "Baseline update requires rate=500 metadata, got: ${benchmark_rate:-<missing>}"
if [[ -z "$benchmark_started_at_hkt" ]]; then
  if [[ -n "$benchmark_started_at_utc" ]]; then
    benchmark_started_at_hkt="$(python3 - "$benchmark_started_at_utc" <<'PY'
import sys
from datetime import datetime, timezone, timedelta

value = sys.argv[1]
dt = datetime.fromisoformat(value.replace('Z', '+00:00'))
hkt = dt.astimezone(timezone(timedelta(hours=8)))
print(hkt.strftime('%Y-%m-%d %H:%M:%S HKT'))
PY
)"
  else
    die "Benchmark metadata missing benchmark_started_at_hkt and benchmark_started_at_utc"
  fi
fi
[[ -n "$benchmark_command" ]] || die "Benchmark metadata missing benchmark_command"
[[ -n "$benchmark_warmup_sec" ]] || die "Benchmark metadata missing warmup_sec"
[[ -n "$benchmark_duration_sec" ]] || die "Benchmark metadata missing duration_sec"
[[ -n "$benchmark_sample_interval_sec" ]] || die "Benchmark metadata missing sample_interval_sec"
[[ -n "$benchmark_wait_strategy" ]] || die "Benchmark metadata missing wait_strategy"
[[ -n "$benchmark_fix_cancel_amend_enabled" ]] || die "Benchmark metadata missing fix_cancel_amend_enabled"

if (( p90_latency_us >= P90_THRESHOLD_US )); then
  die "Baseline update skipped: p90 latency ${p90_latency_us} us is not below ${P90_THRESHOLD_US} us."
fi

existing_p90_latency_us="$(latest_baseline_p90)"
if [[ -n "$existing_p90_latency_us" ]] && [[ "$existing_p90_latency_us" =~ ^[0-9]+$ ]] && (( p90_latency_us > existing_p90_latency_us )) && [[ "$ALLOW_REGRESSION" != true ]]; then
  die "Baseline update skipped: current p90 latency ${p90_latency_us} us regressed versus recorded baseline ${existing_p90_latency_us} us. Re-run with --allow-regression only if this slower baseline is intentional."
fi

echo "Benchmark preconditions satisfied; verifying full build before updating LATENCY_BASELINE.md..." >&2
(cd "$PROJECT_ROOT" && "$GRADLEW" --no-daemon build >/dev/null)

P50_LATENCY_US="$p50_latency_us"
P75_LATENCY_US="$p75_latency_us"
P90_LATENCY_US="$p90_latency_us"
BENCHMARK_STARTED_AT_HKT="$benchmark_started_at_hkt"
BENCHMARK_COMMAND="$benchmark_command"
BENCHMARK_WARMUP_SEC="$benchmark_warmup_sec"
BENCHMARK_DURATION_SEC="$benchmark_duration_sec"
BENCHMARK_SAMPLE_INTERVAL_SEC="$benchmark_sample_interval_sec"
BENCHMARK_WAIT_STRATEGY="$benchmark_wait_strategy"
BENCHMARK_FIX_CANCEL_AMEND_ENABLED="$benchmark_fix_cancel_amend_enabled"

ensure_known_good_tag


rewrite_latency_baseline

if (cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --quiet -- LATENCY_BASELINE.md); then
  push_known_good_tag
  echo "LATENCY_BASELINE.md already matches the latest verified benchmark for commit ${BENCHMARKED_COMMIT_SHORT}; known-good tag ${KNOWN_GOOD_TAG} is ready." >&2
  exit 0
fi

(cd "$PROJECT_ROOT" && "$GIT_BIN" add LATENCY_BASELINE.md)

staged_files="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --cached --name-only)"
[[ "$staged_files" == "LATENCY_BASELINE.md" ]] || die "Refusing to commit unexpected staged files: ${staged_files}"

unstaged_files="$(cd "$PROJECT_ROOT" && "$GIT_BIN" --no-pager diff --name-only)"
[[ -z "$unstaged_files" ]] || die "Refusing to push with unexpected unstaged changes: ${unstaged_files}"

(cd "$PROJECT_ROOT" && "$GIT_BIN" commit -m "Update latency baseline for ${BENCHMARKED_COMMIT_SHORT}" >/dev/null)
(cd "$PROJECT_ROOT" && "$GIT_BIN" push >/dev/null)
push_known_good_tag

echo "Updated LATENCY_BASELINE.md for benchmarked commit ${BENCHMARKED_COMMIT_SHORT}, and pushed the baseline commit plus known-good tag ${KNOWN_GOOD_TAG} to ${UPSTREAM_REMOTE}." >&2

