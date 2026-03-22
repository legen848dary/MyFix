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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulatorBootstrapCoverageTest {

    @AfterEach
    void clearAeronDirProperty() {
        System.clearProperty("aeron.dir");
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
                "BUSY_SPIN",
                16,
                10,
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
}

