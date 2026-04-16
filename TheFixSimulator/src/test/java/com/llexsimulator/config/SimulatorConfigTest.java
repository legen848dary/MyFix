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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatorConfigTest {

    @Test
    void benchmarkThreadingProfileForcesAggressivePerThreadSettings() {
        SimulatorConfig config = new SimulatorConfig(
                "0.0.0.0",
                9880,
                "logs/quickfixj",
                false,
                8080,
                "/tmp/aeron",
                "aeron:ipc",
                "aeron:ipc",
                131072,
                "SLEEPING",
                "SLEEPING",
                "BACKOFF",
                "SLEEPING",
                "SHARED",
                "backoff",
                "backoff",
                "backoff",
                "yielding",
                "yielding",
                131072,
                500,
                true,
                true
        );

        SimulatorConfig benchmark = config.withBenchmarkThreadingProfile();

        assertEquals("BUSY_SPIN", benchmark.waitStrategy());
        assertEquals("BUSY_SPIN", benchmark.disruptorWaitStrategy());
        assertEquals("BUSY_SPIN", benchmark.fixPollerWaitStrategy());
        assertEquals("BUSY_SPIN", benchmark.metricsSubscriberWaitStrategy());
        assertEquals("DEDICATED", benchmark.aeronThreadingMode());
        assertEquals("busy_spin", benchmark.aeronConductorIdleStrategy());
        assertEquals("noop", benchmark.aeronSenderIdleStrategy());
        assertEquals("noop", benchmark.aeronReceiverIdleStrategy());
    }
}
