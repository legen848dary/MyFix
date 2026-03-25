#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SIM_ROOT="${PROJECT_ROOT}/TheFixSimulator"
ARTIFACTS_ROOT="${SIM_ROOT}/logs/direct-jvm-benchmark-reports"

RATE="${BENCHMARK_RATE:-500}"
WARMUP_SEC="${BENCHMARK_WARMUP_SEC:-20}"
DURATION_SEC="${BENCHMARK_DURATION_SEC:-30}"
SAMPLE_INTERVAL_SEC="${BENCHMARK_SAMPLE_INTERVAL_SEC:-5}"
SIM_WEB_PORT="${BENCHMARK_SIM_WEB_PORT:-28090}"
FIX_PORT="${BENCHMARK_FIX_PORT:-29890}"
HEAP_XMS="${BENCHMARK_JAVA_XMS:-512m}"
HEAP_XMX="${BENCHMARK_JAVA_XMX:-512m}"
WAIT_STRATEGY="${BENCHMARK_WAIT_STRATEGY:-BUSY_SPIN}"
FIX_CANCEL_AMEND_ENABLED="${BENCHMARK_FIX_CANCEL_AMEND_ENABLED:-true}"
BUILD_FIRST=false
BENCHMARK_STARTED_AT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BENCHMARK_STARTED_AT_HKT="$(TZ=Asia/Hong_Kong date '+%Y-%m-%d %H:%M:%S HKT')"

usage() {
  cat <<EOF
Usage:
  ./TheFixSimulator/scripts/run_benchmark_direct_jvm.sh [--build] [--rate N] [--warmup SEC] [--duration SEC] [--sample-interval SEC] [--sim-web-port PORT] [--fix-port PORT] [--disable-amend-cancel]

Defaults:
  rate=${RATE}
  warmup=${WARMUP_SEC}
  duration=${DURATION_SEC}
  sample-interval=${SAMPLE_INTERVAL_SEC}
  sim-web-port=${SIM_WEB_PORT}
  fix-port=${FIX_PORT}
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      BUILD_FIRST=true
      shift
      ;;
    --rate)
      RATE="$2"
      shift 2
      ;;
    --warmup)
      WARMUP_SEC="$2"
      shift 2
      ;;
    --duration)
      DURATION_SEC="$2"
      shift 2
      ;;
    --sample-interval)
      SAMPLE_INTERVAL_SEC="$2"
      shift 2
      ;;
    --sim-web-port)
      SIM_WEB_PORT="$2"
      shift 2
      ;;
    --fix-port)
      FIX_PORT="$2"
      shift 2
      ;;
    --disable-amend-cancel)
      FIX_CANCEL_AMEND_ENABLED=false
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

for value in "$RATE" "$WARMUP_SEC" "$DURATION_SEC" "$SAMPLE_INTERVAL_SEC" "$SIM_WEB_PORT" "$FIX_PORT"; do
  if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -le 0 ]]; then
    echo "All numeric arguments must be positive integers." >&2
    exit 1
  fi
done

mkdir -p "$ARTIFACTS_ROOT"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACTS_ROOT}/${RUN_ID}"
mkdir -p "$ARTIFACT_DIR" "$ARTIFACT_DIR/artio-state" "$ARTIFACT_DIR/aeron" "$ARTIFACT_DIR/fix-demo-client"

BENCHMARK_COMMAND=("./TheFixSimulator/scripts/run_benchmark_direct_jvm.sh")
if [[ "$BUILD_FIRST" == true ]]; then
  BENCHMARK_COMMAND+=("--build")
fi
BENCHMARK_COMMAND+=(
  "--rate" "$RATE"
  "--warmup" "$WARMUP_SEC"
  "--duration" "$DURATION_SEC"
  "--sample-interval" "$SAMPLE_INTERVAL_SEC"
  "--sim-web-port" "$SIM_WEB_PORT"
  "--fix-port" "$FIX_PORT"
)
if [[ "$FIX_CANCEL_AMEND_ENABLED" != true ]]; then
  BENCHMARK_COMMAND+=("--disable-amend-cancel")
fi
BENCHMARK_COMMAND_STRING="${BENCHMARK_COMMAND[*]}"

cleanup() {
  set +e
  if [[ -n "${CLIENT_PID:-}" ]] && kill -0 "$CLIENT_PID" 2>/dev/null; then
    kill "$CLIENT_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$CLIENT_PID" 2>/dev/null || true
  fi
  if [[ -n "${SIM_PID:-}" ]] && kill -0 "$SIM_PID" 2>/dev/null; then
    kill "$SIM_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$SIM_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

for port in "$SIM_WEB_PORT" "$FIX_PORT"; do
  if lsof -ti "tcp:${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Required benchmark port ${port} is already in use" >&2
    exit 1
  fi
done

SIM_JAR="${SIM_ROOT}/build/libs/LLExSimulator-1.0-SNAPSHOT.jar"

if [[ "$BUILD_FIRST" == true ]]; then
  (cd "$PROJECT_ROOT" && ./gradlew --no-daemon :TheFixSimulator:shadowJar)
fi

if [[ ! -f "$SIM_JAR" ]]; then
  echo "Simulator JAR not found: $SIM_JAR" >&2
  echo "Build it first with:  ./gradlew :TheFixSimulator:shadowJar" >&2
  echo "Or re-run with the --build flag." >&2
  exit 1
fi

cat > "${ARTIFACT_DIR}/metadata.txt" <<EOF
mode=direct-jvm-warm
benchmark_started_at_utc=${BENCHMARK_STARTED_AT_UTC}
benchmark_started_at_hkt=${BENCHMARK_STARTED_AT_HKT}
run_id=${RUN_ID}
benchmark_command=${BENCHMARK_COMMAND_STRING}
rate=${RATE}
warmup_sec=${WARMUP_SEC}
duration_sec=${DURATION_SEC}
sample_interval_sec=${SAMPLE_INTERVAL_SEC}
sim_web_port=${SIM_WEB_PORT}
fix_port=${FIX_PORT}
wait_strategy=${WAIT_STRATEGY}
benchmark_mode_enabled=true
fix_cancel_amend_enabled=${FIX_CANCEL_AMEND_ENABLED}
heap_xms=${HEAP_XMS}
heap_xmx=${HEAP_XMX}
EOF

java \
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
  -Dllexsim.log.dir="${ARTIFACT_DIR}" \
  -Dllexsim.log.name=llexsimulator \
  -Dfix.host=0.0.0.0 \
  -Dfix.port="${FIX_PORT}" \
  -Dfix.log.dir="${ARTIFACT_DIR}/artio-state" \
  -Dfix.raw.message.logging.enabled=false \
  -Dweb.port="${SIM_WEB_PORT}" \
  -Daeron.dir="${ARTIFACT_DIR}/aeron" \
  -Dbenchmark.mode.enabled=true \
  -Dfix.cancel.amend.enabled="${FIX_CANCEL_AMEND_ENABLED}" \
  -Dwait.strategy="${WAIT_STRATEGY}" \
  -XX:+UseZGC -XX:+ZGenerational \
  -Xms"${HEAP_XMS}" -Xmx"${HEAP_XMX}" \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  -XX:+PerfDisableSharedMem \
  --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -jar "$SIM_JAR" \
  > "${ARTIFACT_DIR}/simulator.log" 2>&1 &
SIM_PID=$!

python3 - <<PY
import json, os, sys, time, urllib.request
url      = "http://127.0.0.1:${SIM_WEB_PORT}/api/health"
sim_pid  = ${SIM_PID}
log_path = "${ARTIFACT_DIR}/simulator.log"

def _process_alive(pid):
    """Return True if *pid* is still running (handles zombies via /proc or ps)."""
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True  # exists but not ours to signal
    # The process exists; check it is not a zombie using ps (cross-platform enough)
    try:
        import subprocess
        stat = subprocess.run(
            ["ps", "-p", str(pid), "-o", "stat="],
            capture_output=True, text=True, timeout=2,
        )
        state = stat.stdout.strip()
        return bool(state) and not state.startswith("Z")
    except Exception:
        return True  # assume alive if we cannot tell

def _show_log_tail(path, lines=20):
    try:
        with open(path) as f:
            content = f.read().strip()
        tail = "\n".join(content.splitlines()[-lines:])
        sys.stderr.write(f"--- last {lines} lines of {path} ---\n{tail}\n---\n")
    except OSError:
        sys.stderr.write(f"(could not read {path})\n")

for _ in range(120):
    if not _process_alive(sim_pid):
        sys.stderr.write(
            f"Simulator process (pid={sim_pid}) exited before becoming healthy.\n"
        )
        _show_log_tail(log_path)
        sys.exit(1)
    try:
        with urllib.request.urlopen(url, timeout=2) as r:
            data = json.load(r)
        if data.get("status") == "UP":
            sys.exit(0)
    except Exception:
        pass
    time.sleep(1)

sys.stderr.write("Timed out waiting for simulator health.\n")
_show_log_tail(log_path)
sys.exit(1)
PY

java \
  -Dllexsim.log.dir="${ARTIFACT_DIR}/fix-demo-client" \
  -Dfix.demo.host=127.0.0.1 \
  -Dfix.demo.port="${FIX_PORT}" \
  -Dfix.demo.rate="${RATE}" \
  -Dfix.demo.logDir="${ARTIFACT_DIR}/fix-demo-client" \
  -Dfix.demo.rawLoggingEnabled=false \
  -cp "${SIM_ROOT}/build/libs/LLExSimulator-1.0-SNAPSHOT.jar" \
  com.llexsimulator.client.FixDemoClientMain \
  > "${ARTIFACT_DIR}/demo-client.log" 2>&1 &
CLIENT_PID=$!

python3 - <<PY
import json, os, sys, time, urllib.request, subprocess
url         = "http://127.0.0.1:${SIM_WEB_PORT}/api/health"
client_pid  = ${CLIENT_PID}
log_path    = "${ARTIFACT_DIR}/demo-client.log"

def _process_alive(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    try:
        stat = subprocess.run(
            ["ps", "-p", str(pid), "-o", "stat="],
            capture_output=True, text=True, timeout=2,
        )
        state = stat.stdout.strip()
        return bool(state) and not state.startswith("Z")
    except Exception:
        return True

def _show_log_tail(path, lines=20):
    try:
        with open(path) as f:
            content = f.read().strip()
        tail = "\n".join(content.splitlines()[-lines:])
        sys.stderr.write(f"--- last {lines} lines of {path} ---\n{tail}\n---\n")
    except OSError:
        sys.stderr.write(f"(could not read {path})\n")

for _ in range(120):
    if not _process_alive(client_pid):
        sys.stderr.write(
            f"FIX demo client process (pid={client_pid}) exited before session logon.\n"
        )
        _show_log_tail(log_path)
        sys.exit(1)
    try:
        with urllib.request.urlopen(url, timeout=2) as r:
            data = json.load(r)
        if int(data.get("fixSessions", 0)) >= 1:
            sys.exit(0)
    except Exception:
        pass
    time.sleep(1)

sys.stderr.write("Timed out waiting for FIX session logon.\n")
_show_log_tail(log_path)
sys.exit(1)
PY

sleep "${WARMUP_SEC}"
curl -fsS -X POST "http://127.0.0.1:${SIM_WEB_PORT}/api/statistics/reset" > "${ARTIFACT_DIR}/reset.json"

SAMPLES_FILE="${ARTIFACT_DIR}/statistics-samples.jsonl"
MEASURE_DEADLINE=$((SECONDS + DURATION_SEC))
while (( SECONDS < MEASURE_DEADLINE )); do
  now_epoch="$(date +%s)"
  sample_json="$(curl -fsS "http://127.0.0.1:${SIM_WEB_PORT}/api/statistics")"
  printf '{"sampleEpochSec":%s,"stats":%s}\n' "$now_epoch" "$sample_json" >> "$SAMPLES_FILE"
  remaining=$((MEASURE_DEADLINE - SECONDS))
  if (( remaining <= 0 )); then
    break
  fi
  sleep_for="$SAMPLE_INTERVAL_SEC"
  if (( remaining < sleep_for )); then
    sleep_for="$remaining"
  fi
  sleep "$sleep_for"
done

sleep 2
curl -fsS "http://127.0.0.1:${SIM_WEB_PORT}/api/statistics" > "${ARTIFACT_DIR}/statistics.json"
python3 - <<PY
import json
from pathlib import Path
stats = json.loads(Path("${ARTIFACT_DIR}/statistics.json").read_text())
for key in (
    "ordersReceived",
    "throughputPerSec",
    "p50LatencyUs",
    "p75LatencyUs",
    "p90LatencyUs",
    "p99LatencyUs",
    "maxLatencyUs",
    "preValidationQueueP50LatencyUs",
    "preValidationQueueP75LatencyUs",
    "preValidationQueueP90LatencyUs",
    "preValidationQueueMaxLatencyUs",
    "ingressPublishP50LatencyUs",
    "ingressPublishP75LatencyUs",
    "ingressPublishP90LatencyUs",
    "ingressPublishMaxLatencyUs",
    "disruptorQueueP50LatencyUs",
    "disruptorQueueP75LatencyUs",
    "disruptorQueueP90LatencyUs",
    "disruptorQueueMaxLatencyUs",
    "validationP50LatencyUs",
    "validationP75LatencyUs",
    "validationP90LatencyUs",
    "validationMaxLatencyUs",
    "fillStrategyP50LatencyUs",
    "fillStrategyP75LatencyUs",
    "fillStrategyP90LatencyUs",
    "fillStrategyMaxLatencyUs",
    "executionReportP50LatencyUs",
    "executionReportP75LatencyUs",
    "executionReportP90LatencyUs",
    "executionReportMaxLatencyUs",
    "metricsPublishP50LatencyUs",
    "metricsPublishP75LatencyUs",
    "metricsPublishP90LatencyUs",
    "metricsPublishMaxLatencyUs",
    "outboundQueueP50LatencyUs",
    "outboundQueueP75LatencyUs",
    "outboundQueueP90LatencyUs",
    "outboundQueueMaxLatencyUs",
    "outboundSendP50LatencyUs",
    "outboundSendP75LatencyUs",
    "outboundSendP90LatencyUs",
    "outboundSendMaxLatencyUs",
):
    print(f"{key}={stats.get(key)}")

samples_path = Path("${ARTIFACT_DIR}/statistics-samples.jsonl")
if samples_path.exists():
    samples = []
    for raw in samples_path.read_text().splitlines():
        if not raw.strip():
            continue
        payload = json.loads(raw)
        stats_payload = payload.get("stats", {})
        samples.append((
            payload.get("sampleEpochSec"),
            stats_payload.get("p50LatencyUs"),
            stats_payload.get("p75LatencyUs"),
            stats_payload.get("p90LatencyUs"),
            stats_payload.get("p99LatencyUs"),
            stats_payload.get("maxLatencyUs"),
            stats_payload.get("preValidationQueueP90LatencyUs"),
            stats_payload.get("preValidationQueueMaxLatencyUs"),
            stats_payload.get("ingressPublishP90LatencyUs"),
            stats_payload.get("ingressPublishMaxLatencyUs"),
            stats_payload.get("disruptorQueueP90LatencyUs"),
            stats_payload.get("disruptorQueueMaxLatencyUs"),
            stats_payload.get("validationP90LatencyUs"),
            stats_payload.get("validationMaxLatencyUs"),
            stats_payload.get("fillStrategyP90LatencyUs"),
            stats_payload.get("fillStrategyMaxLatencyUs"),
            stats_payload.get("executionReportP90LatencyUs"),
            stats_payload.get("executionReportMaxLatencyUs"),
            stats_payload.get("metricsPublishP90LatencyUs"),
            stats_payload.get("metricsPublishMaxLatencyUs"),
            stats_payload.get("outboundQueueP90LatencyUs"),
            stats_payload.get("outboundQueueMaxLatencyUs"),
            stats_payload.get("outboundSendP90LatencyUs"),
            stats_payload.get("outboundSendMaxLatencyUs"),
        ))
    if samples:
        print("samples=")
        for (
            sample_epoch,
            p50,
            p75,
            p90,
            p99,
            max_latency,
            pre_validation_queue_p90,
            pre_validation_queue_max,
            ingress_publish_p90,
            ingress_publish_max,
            disruptor_queue_p90,
            disruptor_queue_max,
            validation_p90,
            validation_max,
            fill_strategy_p90,
            fill_strategy_max,
            execution_report_p90,
            execution_report_max,
            metrics_publish_p90,
            metrics_publish_max,
            outbound_queue_p90,
            outbound_queue_max,
            outbound_send_p90,
            outbound_send_max,
        ) in samples:
            print(
                f"  {sample_epoch}: p50={p50} p75={p75} p90={p90} p99={p99} max={max_latency} "
                f"preValidationQueueP90/max={pre_validation_queue_p90}/{pre_validation_queue_max} "
                f"ingressPublishP90/max={ingress_publish_p90}/{ingress_publish_max} "
                f"disruptorQueueP90/max={disruptor_queue_p90}/{disruptor_queue_max} "
                f"validationP90/max={validation_p90}/{validation_max} "
                f"fillStrategyP90/max={fill_strategy_p90}/{fill_strategy_max} "
                f"executionReportP90/max={execution_report_p90}/{execution_report_max} "
                f"metricsPublishP90/max={metrics_publish_p90}/{metrics_publish_max} "
                f"outboundQueueP90/max={outbound_queue_p90}/{outbound_queue_max} "
                f"outboundSendP90/max={outbound_send_p90}/{outbound_send_max}"
            )
PY

echo "artifact_dir=${ARTIFACT_DIR}"
echo "metadata_file=${ARTIFACT_DIR}/metadata.txt"

