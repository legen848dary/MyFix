package com.llexsimulator.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsRegistryTest {

    @Test
    void recordsCountersLatencySnapshotsAndReset() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.incrementOrdersReceived();
        registry.incrementOrdersReceived();
        registry.incrementFills();
        registry.incrementRejects();
        registry.recordLatency(123_000L);
        registry.recordLatency(20_000_000_000L); // clamped to MAX_TRACKABLE_NS (10 s)
        registry.recordStageLatencies(10_000L, 11_000L, 12_000L, 20_000L, 30_000L, 40_000L, 50_000L);
        registry.recordOutboundLatencies(60_000L, 70_000L);

        long[] snapshot = registry.snapshot();

        // Counter assertions
        assertEquals(2L, registry.getOrdersReceived());
        assertEquals(2L, registry.getExecReportsSent());
        assertEquals(1L, registry.getFillsSent());
        assertEquals(1L, registry.getRejectsSent());

        // Snapshot buffer assertions
        assertEquals(2L, snapshot[0]); // ordersReceived
        assertEquals(2L, snapshot[1]); // execReportsSent
        assertEquals(1L, snapshot[2]); // fillsSent
        assertEquals(1L, snapshot[3]); // rejectsSent

        assertTrue(registry.getP50Ns() >= 123_000L,
                "p50 should reflect the lower sample; got " + registry.getP50Ns());
        assertTrue(registry.getP75Ns() >= 123_000L,
                "p75 should reflect the lower sample; got " + registry.getP75Ns());

        // snapshot[7] = p99ns; p99 of [123_000, 10_000_000_000] = 10_000_000_000
        assertEquals(registry.getP99Ns(), snapshot[7]);
        assertEquals(registry.getMaxNs(), snapshot[8]);
        assertTrue(registry.getP99Ns() >= 10_000_000_000L,
                "p99 should be clamped max value; got " + registry.getP99Ns());
        assertTrue(registry.getMaxNs() >= 10_000_000_000L,
                "max should reflect the clamped upper sample; got " + registry.getMaxNs());

        assertTrue(registry.getPreValidationQueueP50Ns() >= 10_000L);
        assertTrue(registry.getPreValidationQueueMaxNs() >= 10_000L);
        assertTrue(registry.getIngressPublishP50Ns() >= 11_000L);
        assertTrue(registry.getIngressPublishMaxNs() >= 11_000L);
        assertTrue(registry.getDisruptorQueueP50Ns() >= 12_000L);
        assertTrue(registry.getDisruptorQueueMaxNs() >= 12_000L);
        assertTrue(registry.getValidationP50Ns() >= 20_000L);
        assertTrue(registry.getValidationMaxNs() >= 20_000L);
        assertTrue(registry.getFillStrategyP50Ns() >= 30_000L);
        assertTrue(registry.getFillStrategyMaxNs() >= 30_000L);
        assertTrue(registry.getExecutionReportP50Ns() >= 40_000L);
        assertTrue(registry.getExecutionReportMaxNs() >= 40_000L);
        assertTrue(registry.getMetricsPublishP50Ns() >= 50_000L);
        assertTrue(registry.getMetricsPublishMaxNs() >= 50_000L);
        assertTrue(registry.getOutboundQueueP50Ns() >= 60_000L);
        assertTrue(registry.getOutboundQueueMaxNs() >= 60_000L);
        assertTrue(registry.getOutboundSendP50Ns() >= 70_000L);
        assertTrue(registry.getOutboundSendMaxNs() >= 70_000L);

        // After reset everything zeroes out
        registry.reset();

        assertEquals(0L, registry.getOrdersReceived());
        assertEquals(0L, registry.getExecReportsSent());
        assertEquals(0L, registry.getFillsSent());
        assertEquals(0L, registry.getRejectsSent());
        assertEquals(0L, registry.getP50Ns());
        assertEquals(0L, registry.getP75Ns());
        assertEquals(0L, registry.getP99Ns());
        assertEquals(0L, registry.getMaxNs());
        assertEquals(0L, registry.getPreValidationQueueP90Ns());
        assertEquals(0L, registry.getIngressPublishP90Ns());
        assertEquals(0L, registry.getDisruptorQueueP90Ns());
        assertEquals(0L, registry.getMetricsPublishP90Ns());
        assertEquals(0L, registry.getOutboundQueueP90Ns());
        assertEquals(0L, registry.getOutboundSendP90Ns());
        assertEquals(0L, registry.snapshot()[0]);
    }
}
