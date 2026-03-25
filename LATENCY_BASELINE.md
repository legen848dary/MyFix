# Latency Baseline Tracker

This file records the accepted direct-JVM latency checkpoints so we do not lose track while iterating on performance or features.

Guarded baseline updates create immutable rollback tags named `latency-known-good-<commit>`.

## Baseline history

| Datetime (HKT) | Commit hash | Msg/s | p50 (µs) | p75 (µs) | p90 (µs) |
| --- | --- | ---: | ---: | ---: | ---: |
| 2026-03-26 00:37:37 HKT | `aa8248c18c9bb4e996282785b61dcd2ae8de7d24` | 500 | 4 | 5 | 6 |
| 2026-03-25 23:44:44 HKT | `67f611f8ee6499668cb18c36fcf5748aa1e1b16d` | 500 | 4 | 4 | 5 |
| --- | `---` | ---: | ---: | ---: | ---: |

## Update rule

Only update this file after:
1. running from a clean committed git worktree on a named branch with an upstream configured,
2. rerunning the exact direct-JVM 500 msg/s benchmark,
3. confirming the benchmark artifact metadata is present and matches the expected mode/rate,
4. confirming `p90 < 10 µs` at `500 msg/s`,
5. confirming the root build is green, and
6. confirming the new baseline does not regress versus the latest recorded `p90` unless `--allow-regression` is used intentionally.

Use the known-good tag for rollback, for example:

```bash
git checkout latency-known-good-aa8248c
```
