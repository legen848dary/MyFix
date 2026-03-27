package com.llexsimulator.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("fix.port");
        System.clearProperty("wait.strategy");
        System.clearProperty("wait.strategy.fix.poller");
        System.clearProperty("aeron.threading.mode");
    }

    @Test
    void prefersExternalConfigOverLocalConfig() throws IOException {
        Path externalConfig = tempDir.resolve("external.properties");
        Path localConfig = tempDir.resolve("local.properties");

        Files.writeString(externalConfig,
                "fix.port=9991\nwait.strategy=SLEEPING\nwait.strategy.fix.poller=BUSY_SPIN\n" +
                        "aeron.threading.mode=SHARED\nbenchmark.mode.enabled=true\nfix.cancel.amend.enabled=false\n");
        Files.writeString(localConfig, "fix.port=9992\nwait.strategy=BUSY_SPIN\nbenchmark.mode.enabled=false\nfix.cancel.amend.enabled=true\n");

        SimulatorConfig config = ConfigLoader.load(externalConfig, localConfig);

        assertEquals(9991, config.fixPort());
        assertEquals("SLEEPING", config.waitStrategy());
        assertEquals("SLEEPING", config.disruptorWaitStrategy());
        assertEquals("BUSY_SPIN", config.fixPollerWaitStrategy());
        assertEquals("SLEEPING", config.metricsSubscriberWaitStrategy());
        assertEquals("SHARED", config.aeronThreadingMode());
        assertTrue(config.benchmarkModeEnabled());
        assertFalse(config.cancelAmendEnabled());
    }

    @Test
    void systemPropertiesOverrideLoadedConfig() throws IOException {
        Path localConfig = tempDir.resolve("local.properties");
        Files.writeString(localConfig, "fix.host=127.0.0.1\nfix.port=1234\nwait.strategy.fix.poller=backoff\n");
        System.setProperty("fix.port", "9999");
        System.setProperty("wait.strategy.fix.poller", "yielding");

        SimulatorConfig config = ConfigLoader.load(tempDir.resolve("missing-external.properties"), localConfig);

        assertEquals("127.0.0.1", config.fixHost());
        assertEquals(9999, config.fixPort());
        assertEquals("yielding", config.fixPollerWaitStrategy());
    }

    @Test
    void fallsBackToClasspathResourceWhenFilesAreMissing() {
        SimulatorConfig config = ConfigLoader.load(
                tempDir.resolve("missing-external.properties"),
                tempDir.resolve("missing-local.properties"));

        assertEquals(9880, config.fixPort());
        assertFalse(config.fixRawMessageLoggingEnabled());
        assertEquals("BUSY_SPIN", config.waitStrategy());
        assertEquals("BUSY_SPIN", config.disruptorWaitStrategy());
        assertEquals("BUSY_SPIN", config.fixPollerWaitStrategy());
        assertEquals("SLEEPING", config.metricsSubscriberWaitStrategy());
        assertEquals("DEDICATED", config.aeronThreadingMode());
        assertEquals("busy_spin", config.aeronConductorIdleStrategy());
        assertEquals("noop", config.aeronSenderIdleStrategy());
        assertEquals("noop", config.aeronReceiverIdleStrategy());
        assertEquals("backoff", config.aeronSharedIdleStrategy());
        assertEquals("backoff", config.aeronSharedNetworkIdleStrategy());
        assertEquals("aeron:ipc?term-length=8388608", config.artioLibraryAeronChannel());
        assertEquals("aeron:ipc?term-length=65536", config.metricsAeronChannel());
        assertFalse(config.benchmarkModeEnabled());
        assertTrue(config.cancelAmendEnabled());
    }
}
