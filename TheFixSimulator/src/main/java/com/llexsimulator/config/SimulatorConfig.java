/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.llexsimulator.config;

import com.llexsimulator.aeron.AeronRuntimeTuning;

/**
 * Immutable configuration record loaded once at startup.
 * All values are primitives or Strings — no heap churn after construction.
 */
public record SimulatorConfig(
        String fixHost,
        int    fixPort,
        String fixLogDir,
        boolean fixRawMessageLoggingEnabled,
        int    webPort,
        String aeronDir,
        String artioLibraryAeronChannel,
        String metricsAeronChannel,
        int    ringBufferSize,
        String waitStrategy,
        String disruptorWaitStrategy,
        String fixPollerWaitStrategy,
        String metricsSubscriberWaitStrategy,
        String aeronThreadingMode,
        String aeronConductorIdleStrategy,
        String aeronSenderIdleStrategy,
        String aeronReceiverIdleStrategy,
        String aeronSharedIdleStrategy,
        String aeronSharedNetworkIdleStrategy,
        int    orderPoolSize,
        int    metricsPublishInterval,
        boolean benchmarkModeEnabled,
        boolean cancelAmendEnabled
) {
    /** Default configuration with sensible low-latency values. */
    public static SimulatorConfig defaults() {
        return new SimulatorConfig(
                "0.0.0.0", 9880, "logs/quickfixj", false,
                8080, "/tmp/aeron-llexsim",
                AeronRuntimeTuning.DEFAULT_ARTIO_LIBRARY_CHANNEL,
                AeronRuntimeTuning.DEFAULT_METRICS_CHANNEL,
                131072, "BUSY_SPIN",
                "BUSY_SPIN", "BUSY_SPIN", "BUSY_SPIN",
                "DEDICATED", "busy_spin", "noop", "noop", "backoff", "backoff",
                131072, 500, false, true
        );
    }

    public SimulatorConfig withAeronSettings(
            String aeronDir,
            String artioLibraryAeronChannel,
            String metricsAeronChannel) {
        return new SimulatorConfig(
                fixHost,
                fixPort,
                fixLogDir,
                fixRawMessageLoggingEnabled,
                webPort,
                aeronDir,
                artioLibraryAeronChannel,
                metricsAeronChannel,
                ringBufferSize,
                waitStrategy,
                disruptorWaitStrategy,
                fixPollerWaitStrategy,
                metricsSubscriberWaitStrategy,
                aeronThreadingMode,
                aeronConductorIdleStrategy,
                aeronSenderIdleStrategy,
                aeronReceiverIdleStrategy,
                aeronSharedIdleStrategy,
                aeronSharedNetworkIdleStrategy,
                orderPoolSize,
                metricsPublishInterval,
                benchmarkModeEnabled,
                cancelAmendEnabled);
    }

    public SimulatorConfig withBenchmarkThreadingProfile() {
        return new SimulatorConfig(
                fixHost,
                fixPort,
                fixLogDir,
                fixRawMessageLoggingEnabled,
                webPort,
                aeronDir,
                artioLibraryAeronChannel,
                metricsAeronChannel,
                ringBufferSize,
                "BUSY_SPIN",
                "BUSY_SPIN",
                "BUSY_SPIN",
                "BUSY_SPIN",
                "DEDICATED",
                "busy_spin",
                "noop",
                "noop",
                aeronSharedIdleStrategy,
                aeronSharedNetworkIdleStrategy,
                orderPoolSize,
                metricsPublishInterval,
                benchmarkModeEnabled,
                cancelAmendEnabled);
    }
}
