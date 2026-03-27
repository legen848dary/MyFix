package com.llexsimulator;

import com.llexsimulator.aeron.AeronContext;
import com.llexsimulator.aeron.AeronRuntimeTuning;
import com.llexsimulator.aeron.MetricsPublisher;
import com.llexsimulator.aeron.MetricsSubscriber;
import com.llexsimulator.config.ConfigLoader;
import com.llexsimulator.config.SimulatorConfig;
import com.llexsimulator.disruptor.DisruptorPipeline;
import com.llexsimulator.engine.FixEngineManager;
import com.llexsimulator.engine.FixSessionApplication;
import com.llexsimulator.web.WebServer;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulatorBootstrapCoverageTest {

    @AfterEach
    void clearAeronDirProperty() {
        System.clearProperty("aeron.dir");
        System.clearProperty("aeron.threading.mode");
        System.clearProperty("aeron.conductor.idle.strategy");
        System.clearProperty("aeron.sender.idle.strategy");
        System.clearProperty("aeron.receiver.idle.strategy");
        System.clearProperty("aeron.shared.idle.strategy");
        System.clearProperty("aeron.shared.network.idle.strategy");
    }

    @Test
    void startInBenchmarkModeSkipsMetricsSubscriberAndStopClosesSubsystems() throws Exception {
        SimulatorConfig config = new SimulatorConfig(
                "127.0.0.1",
                9999,
                "build/test-fix-logs",
                false,
                8088,
                "/tmp/aeron-unit",
                "aeron:ipc?term-length=8388608",
                "aeron:ipc?term-length=65536",
                1024,
                "SLEEPING",
                "SLEEPING",
                "BACKOFF",
                "SLEEPING",
                "SHARED",
                "backoff",
                "backoff",
                "backoff",
                "backoff",
                "backoff",
                16,
                10,
                true,
                true);

        try (MockedStatic<ConfigLoader> configLoader = org.mockito.Mockito.mockStatic(ConfigLoader.class);
             MockedStatic<AeronRuntimeTuning> tuning = org.mockito.Mockito.mockStatic(AeronRuntimeTuning.class);
             MockedConstruction<AeronContext> aeronContext = org.mockito.Mockito.mockConstruction(AeronContext.class);
             MockedConstruction<MetricsPublisher> metricsPublisher = org.mockito.Mockito.mockConstruction(MetricsPublisher.class);
             MockedConstruction<DisruptorPipeline> disruptorPipeline = org.mockito.Mockito.mockConstruction(DisruptorPipeline.class);
             MockedConstruction<WebServer> webServer = org.mockito.Mockito.mockConstruction(WebServer.class, (constructed, context) -> {
                 Vertx vertx = mock(Vertx.class);
                 when(constructed.getVertx()).thenReturn(vertx);
             });
             MockedConstruction<MetricsSubscriber> metricsSubscriber = org.mockito.Mockito.mockConstruction(MetricsSubscriber.class);
             MockedConstruction<FixSessionApplication> fixApp = org.mockito.Mockito.mockConstruction(FixSessionApplication.class);
             MockedConstruction<FixEngineManager> fixEngineManager = org.mockito.Mockito.mockConstruction(FixEngineManager.class)) {

            configLoader.when(ConfigLoader::load).thenReturn(config);
            tuning.when(() -> AeronRuntimeTuning.resolve(config)).thenReturn(config);

            SimulatorBootstrap bootstrap = new SimulatorBootstrap();
            bootstrap.start();

            assertEquals(config.aeronDir(), System.getProperty("aeron.dir"));
            assertEquals("DEDICATED", System.getProperty("aeron.threading.mode"));
            assertEquals("busy_spin", System.getProperty("aeron.conductor.idle.strategy"));
            assertEquals("noop", System.getProperty("aeron.sender.idle.strategy"));
            assertEquals("noop", System.getProperty("aeron.receiver.idle.strategy"));
            assertEquals(1, aeronContext.constructed().size());
            assertEquals(1, metricsPublisher.constructed().size());
            assertEquals(1, disruptorPipeline.constructed().size());
            assertEquals(1, webServer.constructed().size());
            assertEquals(1, fixApp.constructed().size());
            assertEquals(1, fixEngineManager.constructed().size());
            assertTrue(metricsSubscriber.constructed().isEmpty(), "Benchmark mode should skip live metrics subscriber startup");
            verify(disruptorPipeline.constructed().getFirst()).start();
            verify(fixEngineManager.constructed().getFirst()).start();

            bootstrap.stop();

            verify(fixEngineManager.constructed().getFirst()).stop();
            verify(webServer.constructed().getFirst()).stop();
            verify(disruptorPipeline.constructed().getFirst()).shutdown();
            verify(metricsPublisher.constructed().getFirst()).close();
            verify(aeronContext.constructed().getFirst()).close();
        }
    }

    @Test
    void stopContinuesWhenSubsystemShutdownThrows() throws Exception {
        SimulatorBootstrap bootstrap = new SimulatorBootstrap();
        FixEngineManager fixEngineManager = mock(FixEngineManager.class);
        MetricsSubscriber metricsSubscriber = mock(MetricsSubscriber.class);
        WebServer webServer = mock(WebServer.class);
        DisruptorPipeline disruptorPipeline = mock(DisruptorPipeline.class);
        MetricsPublisher metricsPublisher = mock(MetricsPublisher.class);
        AeronContext aeronContext = mock(AeronContext.class);

        doThrow(new RuntimeException("fix")).when(fixEngineManager).stop();
        doThrow(new RuntimeException("metrics")).when(metricsSubscriber).stop();
        doThrow(new RuntimeException("web")).when(webServer).stop();
        doThrow(new RuntimeException("disruptor")).when(disruptorPipeline).shutdown();
        doThrow(new RuntimeException("publisher")).when(metricsPublisher).close();
        doThrow(new RuntimeException("aeron")).when(aeronContext).close();

        setField(bootstrap, "fixEngineManager", fixEngineManager);
        setField(bootstrap, "metricsSubscriber", metricsSubscriber);
        setField(bootstrap, "webServer", webServer);
        setField(bootstrap, "disruptorPipeline", disruptorPipeline);
        setField(bootstrap, "metricsPublisher", metricsPublisher);
        setField(bootstrap, "aeronContext", aeronContext);

        bootstrap.stop();

        assertEquals(null, readField(bootstrap, "fixEngineManager"));
        assertEquals(null, readField(bootstrap, "metricsSubscriber"));
        assertEquals(null, readField(bootstrap, "webServer"));
        assertEquals(null, readField(bootstrap, "disruptorPipeline"));
        assertEquals(null, readField(bootstrap, "metricsPublisher"));
        assertEquals(null, readField(bootstrap, "aeronContext"));
    }

    @Test
    void stopInterruptsMetricsThreadIfItRemainsAlive() throws Exception {
        SimulatorBootstrap bootstrap = new SimulatorBootstrap();
        MetricsSubscriber metricsSubscriber = mock(MetricsSubscriber.class);
        Thread metricsThread = Thread.ofPlatform().name("metrics-subscriber-test").unstarted(() -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        metricsThread.start();

        setField(bootstrap, "metricsSubscriber", metricsSubscriber);
        setField(bootstrap, "metricsSubscriberThread", metricsThread);

        bootstrap.stop();

        assertTrue(!metricsThread.isAlive(), "Expected stop() to interrupt and join a lingering metrics thread");
        assertEquals(null, readField(bootstrap, "metricsSubscriber"));
        assertEquals(null, readField(bootstrap, "metricsSubscriberThread"));
    }

    @Test
    void stopRestoresInterruptFlagWhenJoinIsInterrupted() throws Exception {
        SimulatorBootstrap bootstrap = new SimulatorBootstrap();
        MetricsSubscriber metricsSubscriber = mock(MetricsSubscriber.class);
        Thread metricsThread = Thread.ofPlatform().name("metrics-subscriber-interrupt-test").unstarted(() -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        metricsThread.start();

        setField(bootstrap, "metricsSubscriber", metricsSubscriber);
        setField(bootstrap, "metricsSubscriberThread", metricsThread);

        Thread.currentThread().interrupt();
        try {
            bootstrap.stop();
            assertTrue(Thread.currentThread().isInterrupted(), "Expected stop() to restore the interrupt flag");
        } finally {
            Thread.interrupted();
            metricsThread.interrupt();
            metricsThread.join(2_000L);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
