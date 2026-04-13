#!/usr/bin/env bash
# Run all tests across both modules (TheFixClient + TheFixSimulator).
# TheFixSimulator enforces a JaCoCo 100% METHOD+LINE coverage gate.
# Exit code mirrors Gradle: 0 = all green, non-zero = failure.
set -e
./gradlew --no-daemon clean test jacocoTestReport
echo ""
echo "All tests passed."
