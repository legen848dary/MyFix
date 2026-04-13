# Copilot Instructions for MyFix

## Project overview

Gradle multi-module Java 21 monorepo. Two modules:

- **`TheFixSimulator`** (`com.llexsimulator`) — ultra-low-latency FIX acceptor (Artio + Aeron IPC + LMAX Disruptor pipeline). Serves a Vert.x web UI on `http://localhost:8080`, accepts FIX connections on `tcp://localhost:9880`. Shadow JAR entry point: `com.llexsimulator.Main`.
- **`TheFixClient`** (`com.insoftu.thefix.client`) — trader FIX workstation. QuickFIX/J initiator embedded in a Vert.x web server on `http://localhost:8081`. Entry point: `com.insoftu.thefix.client.TheFixClientApplication`. `TheFixClient` depends on `TheFixSimulator` as a Gradle project dependency (reuses simulator's `FixDemoClientConfig` and related classes).

## Build, test, and run

```bash
# Full validation (always green before committing)
./gradlew --no-daemon clean build test

# Run tests for a single module
./gradlew --no-daemon :TheFixSimulator:test
./gradlew --no-daemon :TheFixClient:test

# Run a single test class
./gradlew --no-daemon :TheFixSimulator:test --tests com.llexsimulator.aeron.MetricsSubscriberTest
./gradlew --no-daemon :TheFixClient:test --tests com.insoftu.thefix.client.TheFixClientServerRoutingTest

# Simulator strict coverage gate (required to stay green)
./gradlew --no-daemon :TheFixSimulator:cleanTest :TheFixSimulator:test :TheFixSimulator:jacocoTestCoverageVerification --rerun-tasks

# Run modules individually
./gradlew :TheFixClient:run
./gradlew :TheFixSimulator:run

# Web stack (both apps together)
./scripts/start_web_stack.sh          # direct JVM
./scripts/start_web_stack_docker.sh   # Docker
./scripts/status_web_stack.sh
./scripts/stop_web_stack.sh

# Quick latency benchmark (500 msg/s, prints p50/p75/p90)
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh --build
```

## TheFixSimulator architecture

The hot path is: **FIX acceptor → Disruptor ring buffer → handler chain → FIX outbound sender**

Startup order in `SimulatorBootstrap` (order is critical):
1. Config (`ConfigLoader` + `AeronRuntimeTuning`)
2. Aeron IPC transport (`AeronContext`, `MetricsPublisher`)
3. Metrics registry (`MetricsRegistry`)
4. Order repository — pre-allocated off-heap pool (`OrderRepository`)
5. Fill profile manager (`FillProfileManager`)
6. Disruptor pipeline — pre-allocated ring buffer (`DisruptorPipeline`)
7. Vert.x web server (`WebServer`)
8. Metrics subscriber thread — Aeron IPC → WebSocket bridge (`MetricsSubscriber`)
9. FIX engine last — `FixEngineManager` (QuickFIX/J acceptor wrapping Artio)

**Disruptor handler chain** (inside `CompositeOrderEventHandler`):
`ValidationHandler` → `FillStrategyHandler` → `ExecutionReportHandler` → `MetricsPublishHandler`

**Code generation at build time** (runs before `compileJava`):
- SBE flyweight codecs from `src/main/resources/sbe/fix-messages.xml` → `build/generated/sources/sbe/`
- Artio FIX44 codecs from the QuickFIX/J FIX44 dictionary XML → `build/generated/sources/artio/`

Generated packages (`com/llexsimulator/sbe/**` and `uk/co/real_logic/artio/**`) are excluded from JaCoCo coverage.

## TheFixClient architecture

`TheFixClientApplication` → `TheFixClientServer` (Vert.x HTTP + REST router) → `TheFixClientWorkbenchState` (stateful FIX service wrapper).

Configuration is resolved via `TheFixClientConfig.fromSystemProperties()` using a layered lookup: primary system property → primary env var → legacy fallback property → legacy fallback env var → hardcoded default.

FIX sessions are scoped per saved profile. Multiple simultaneous runtimes are tracked in `TheFixClientWorkbenchState`; the `/api/overview` response includes a `runtimeSessions` map.

## Key conventions

### JaCoCo 100% coverage gate (simulator only)
`TheFixSimulator` enforces 100% METHOD and LINE coverage via `jacocoTestCoverageVerification` on a curated set of classes. Excluded classes are listed in `handwrittenCoverageExcludes` in `TheFixSimulator/build.gradle.kts`. When adding new non-excluded classes, tests must reach 100% method and line coverage or the build will fail. If a class legitimately cannot be unit-tested (e.g., external-system orchestration), add it to `handwrittenCoverageExcludes`.

### FillStrategy implementations must be stateless and allocation-free
`FillStrategy` is a `@FunctionalInterface`. All implementations **must be stateless and allocation-free** — a single instance is shared across all Disruptor processing cycles. Mutable per-call state goes into `event.fillInstructionBuffer` via the pre-allocated SBE `FillInstructionEncoder`.

### Wait strategy configuration is per-thread
Simulator wait strategies are configured individually: `wait.strategy.disruptor`, `wait.strategy.fix.poller`, `wait.strategy.metrics.subscriber`, with `wait.strategy` as fallback. For ultra-low latency, set the hot-path threads (`disruptor`, `fix.poller`) to `BUSY_SPIN` and leave background threads (`metrics.subscriber`) at `SLEEPING`.

### Simulator config via properties file
`TheFixSimulator` reads `config/simulator.properties` (Docker mount) or `src/main/resources/simulator.properties` (classpath fallback). The JaCoCo-excluded `ConfigLoader` and `SimulatorConfig` classes handle this. Never hard-code simulator parameters — always go through `SimulatorConfig`.

### JVM flags required at runtime
Both modules require `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` and several `--add-opens` flags (already wired into the Gradle `run` and `test` tasks). The simulator additionally needs `-Daeron.dir`, `-Daeron.ipc.term.buffer.length`, and `-Dagrona.disable.bounds.checks=true`.

### Benchmark mode
When `benchmark.mode.enabled=true` in simulator config, live Aeron/WebSocket metrics fan-out is disabled and the aggressive threading profile (`BUSY_SPIN` + Aeron `DEDICATED`) is forced. REST/health endpoints remain available.

### Latency baseline
Accepted p90 threshold is **< 10 µs at 500 msg/s**. Baselines are recorded in `LATENCY_BASELINE.md` and tagged as `latency-known-good-<commit>`. Only update via the guarded script (`--update-baseline`), which enforces clean worktree, green build, and regression check.

### TheFixClient config resolution pattern
`TheFixClientConfig` uses a 4-tier fallback: `(primaryProperty, primaryEnv, legacyProperty, legacyEnv, default)`. Keep this pattern when adding new config fields.

### Web static assets
`TheFixClient` serves static files from the `web/` resource directory via Vert.x `StaticHandler`. SPA routes (`/home`, `/neworder`, `/orders`, `/blotter`, etc.) are rerouted to `/index.html`. Trailing-slash variants return HTTP 308.
