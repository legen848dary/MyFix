# Latency Baseline Tracker

This file records the last verified commit that stayed under the latency target so we do not lose track while iterating on performance or features.

## Last known good baseline

- Verified on: 2026-03-23
- Commit hash: `ad8e928b592aab9e27a63053eae383e19668af78`
- Benchmark mode: direct JVM, no Docker
- Benchmark rate: `500 msg/s`
- p50 latency: `4 µs`
- p90 latency: `41 µs`
- Benchmark artifact: `TheFixSimulator/logs/direct-jvm-benchmark-reports/20260323-205843`

## Update rule

Only update this file after:
1. rebuilding the simulator,
2. rerunning the direct-JVM benchmark,
3. confirming `p90 < 100 µs` at `500 msg/s`, and
4. confirming the build is green.

Keep the previous good entry if a later change regresses latency.

