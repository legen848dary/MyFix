# Latency Baseline Tracker

This file records the last verified commit that stayed under the latency target so we do not lose track while iterating on performance or features.

## Last known good baseline

- Verified on: 2026-03-24
- Commit hash: `f11136783c5dcc22dfdba3d3de30d0e99158b145`
- Benchmark mode: direct JVM, no Docker
- Benchmark rate: `500 msg/s`
- p50 latency: `4 µs`
- p90 latency: `5 µs`
- Benchmark artifact: `TheFixSimulator/logs/direct-jvm-benchmark-reports/20260324-013856`

## Update rule

Only update this file after:
1. running from a clean committed git worktree with an upstream branch configured,
2. rebuilding the simulator,
3. rerunning the direct-JVM benchmark,
4. confirming `p90 < 100 µs` at `500 msg/s`, and
5. confirming the build is green.

Keep the previous good entry if a later change regresses latency.
