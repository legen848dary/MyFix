# MyFix

`MyFix` is now a Gradle multi-module Java project with separate client and simulator applications.

## Modules

- `TheFixClient` — starter module for the FIX client implementation.
- `TheFixSimulator` — starter module that will receive the simulator code from the existing repository.

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

## Run tests

```bash
./gradlew test
./gradlew :TheFixClient:test
./gradlew :TheFixSimulator:test
```

## Next step

When you share the GitHub repository URL for the existing simulator code, the code can be moved into `TheFixSimulator` and the build can be adjusted to match its dependencies and entry points.

