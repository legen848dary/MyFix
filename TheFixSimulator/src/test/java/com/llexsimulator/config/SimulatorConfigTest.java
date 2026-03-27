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
