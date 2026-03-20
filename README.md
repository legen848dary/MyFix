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

## Run tests

```bash
./gradlew test
./gradlew :TheFixClient:test
./gradlew :TheFixSimulator:test
```

## Next step

The next implementation checkpoint is to containerize `TheFixClient` so the default local stack can bring up both web applications together while keeping the terminal demo FIX client opt-in:

- `TheFixClient` web workstation
- `TheFixSimulator`
- terminal demo FIX client (on demand)

For continuity across chat sessions, see `PROGRESS.md`.

