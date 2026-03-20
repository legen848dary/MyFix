# MyFix

`MyFix` is now a Gradle multi-module Java project with separate client and simulator applications.

## Modules

- `TheFixClient` — trader-facing web workstation shell for the future FIX client, currently served on `http://localhost:8081`.
- `TheFixSimulator` — imported simulator platform with its own web UI on `http://localhost:8080`, FIX acceptor on `tcp://localhost:9880`, and existing Docker/demo-client tooling.

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

## Run tests

```bash
./gradlew test
./gradlew :TheFixClient:test
./gradlew :TheFixSimulator:test
```

## Next step

The next implementation checkpoint is to wire `TheFixClient` to the real FIX demo-client flow used by `TheFixSimulator`, then containerize `TheFixClient` so all three components can run together locally:

- `TheFixClient` web workstation
- `TheFixSimulator`
- terminal demo FIX client

For continuity across chat sessions, see `PROGRESS.md`.

