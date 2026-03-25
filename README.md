# MyFix

`MyFix` is now a Gradle multi-module Java project with separate client and simulator applications.

## Modules

- `TheFixClient` — trader-facing web workstation with a live QuickFIX/J initiator flow, served on `http://localhost:8081`.
- `TheFixSimulator` — imported simulator platform with its own web UI on `http://localhost:8080`, FIX acceptor on `tcp://localhost:9880`, and Docker/demo-client tooling.

## Project layout

- `build.gradle.kts` — shared root build configuration for all modules
- `settings.gradle.kts` — root project settings and module inclusion
- `TheFixClient/` — client module sources, tests, and module build file
- `TheFixSimulator/` — simulator module sources, tests, and module build file

## Prerequisites

- A compatible JDK installed
- `zsh`, `bash`, or another shell capable of running the Gradle wrapper

## Verify the multi-module setup

```bash
./gradlew projects
./gradlew build
```

## Run individual modules

```bash
./gradlew :TheFixClient:run
./gradlew :TheFixSimulator:run
```

- `TheFixClient` UI: `http://localhost:8081`
- `TheFixSimulator` UI: `http://localhost:8080`

`TheFixClient` can now:

- establish a live FIX session to the simulator
- preview and route manual orders
- start and stop demo-rate order flow from the browser
- shut down the FIX service cleanly when the web server exits

## Start both web apps in one command

### Docker web stack

```bash
./scripts/start_web_stack_docker.sh
./scripts/status_web_stack.sh
./scripts/stop_web_stack_docker.sh
```

This starts/stops:

- `TheFixSimulator` on `http://localhost:8080`
- `TheFixClient` on `http://localhost:8081`

### Direct JVM web stack

```bash
./scripts/start_web_stack.sh
./scripts/status_web_stack.sh
./scripts/stop_web_stack.sh
```

This builds the required artifacts and runs both apps directly on the host JVM.

`./scripts/status_web_stack.sh` reports the current Docker and direct-JVM stack state, including health endpoints, ports, pid/container ownership, and log locations.

## Deploy to a native Linux droplet

Use the native deployment flow when you want both web apps running directly on the droplet JVM behind Nginx + Let's Encrypt.

### Shortest exact sequence

```bash
./scripts/setup_native_droplet.sh <droplet-host-or-ip> <ssh-private-key-path-or-glob> <ssh-user> --app-user <app-runtime-user>
./scripts/deploy_native_stack.sh <droplet-host-or-ip> <ssh-private-key-path-or-glob> <ssh-user> --app-user <app-runtime-user> --sim-host <simulator-domain> --client-host <client-domain> --email <letsencrypt-email>
```

### Safer first deploy without TLS

```bash
./scripts/deploy_native_stack.sh <droplet-host-or-ip> <ssh-private-key-path-or-glob> <ssh-user> --app-user <app-runtime-user> --sim-host <simulator-domain> --client-host <client-domain> --email <letsencrypt-email> --skip-certbot
./scripts/deploy_native_stack.sh <droplet-host-or-ip> <ssh-private-key-path-or-glob> <ssh-user> --app-user <app-runtime-user> --sim-host <simulator-domain> --client-host <client-domain> --email <letsencrypt-email>
```

Notes:

- Run the scripts from the repository root on your local machine.
- The scripts are designed for SSH/bootstrap as a privileged user and service runtime as a normal Linux user.
- By default, the simulator FIX listener stays droplet-local while the two web UIs are published through Nginx on ports 80/443.

### Optional terminal demo FIX client

The terminal demo FIX client remains opt-in and is not part of the default web stack:

```bash
cd TheFixSimulator
./scripts/local_fix_demo_client.sh start 50
./scripts/local_fix_demo_client.sh stop
```

## Run tests

```bash
./gradlew test
./gradlew :TheFixClient:test
./gradlew :TheFixSimulator:test
```

## Quick latency check

Use the dedicated wrapper when you want a fast 500 msg/s direct-JVM check that prints only the key latency percentiles:

```bash
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh
```

Optional build-first run:

```bash
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh --build
```

## Update the known-good latency baseline

When a benchmark run is clean and acceptable, use the guarded baseline-update mode:

```bash
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh --build --update-baseline
```

What this guarded mode does:

- requires a clean committed git worktree on a named branch with an upstream
- reruns the direct-JVM 500 msg/s benchmark and validates the artifact metadata
- requires the accepted benchmark result to stay under `p90 < 10 µs`
- refuses to overwrite a faster recorded baseline unless you explicitly opt in with `--allow-regression`
- verifies the root build is green
- updates `LATENCY_BASELINE.md` as a datetime-sorted history table with:
  - datetime
  - commit hash
  - msg/s
  - p50 / p75 / p90 latency
- pushes both the baseline commit and the known-good tag

If you intentionally need to record a slower but still accepted baseline, use:

```bash
bash ./TheFixSimulator/scripts/run_benchmark_direct_jvm_500.sh --build --update-baseline --allow-regression
```

For rollback, use the immutable tag recorded in `LATENCY_BASELINE.md`:

```bash
git checkout <known-good-tag>
```

## Next step

The next implementation checkpoint is to polish and extend the new combined web-stack workflows while keeping the terminal demo FIX client opt-in.

For continuity across chat sessions, see `PROGRESS.md`.

