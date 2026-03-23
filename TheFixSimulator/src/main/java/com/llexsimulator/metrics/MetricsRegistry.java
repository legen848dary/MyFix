package com.llexsimulator.metrics;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.Arrays;

/**
 * Central metrics store — all operations are lock-free and zero-GC.
 *
 * <p>Latency is tracked with a <em>rolling-window</em> {@link Histogram}: each
 * time {@link #snapshot()} is called (once per metrics-publish cycle from the
 * Disruptor thread), percentiles are computed from the current window, stored
 * in {@code volatile} longs, and the histogram is reset for the next window.
 * This means rolling percentile latency metrics always reflect <em>recent</em> activity only —
 * JVM warm-up spikes and one-off GC pauses do not permanently inflate the
 * reported numbers.
 *
 * <p>The {@code volatile} percentile longs can be read safely from any thread
 * (REST handler, WebSocket subscriber) without locking.
 */
public final class MetricsRegistry {

    private static final long MAX_TRACKABLE_NS = 10_000_000_000L; // 10 s

    // ── Rolling window histogram (disruptor thread only) ─────────────────────
    private final Histogram rollingWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram preValidationQueueWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram ingressPublishWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram disruptorQueueWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram validationWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram fillStrategyWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram executionReportWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Histogram metricsPublishWindow = new Histogram(MAX_TRACKABLE_NS, 3);
    private final Recorder outboundQueueWindow = new Recorder(MAX_TRACKABLE_NS, 3);
    private final Recorder outboundSendWindow = new Recorder(MAX_TRACKABLE_NS, 3);
    private Histogram outboundQueueInterval;
    private Histogram outboundSendInterval;

    // ── Last-computed percentiles — written by disruptor, read by any thread ─
    private volatile long lastP50ns  = 0L;
    private volatile long lastP75ns  = 0L;
    private volatile long lastP80ns  = 0L;
    private volatile long lastP90ns  = 0L;
    private volatile long lastP99ns  = 0L;
    private volatile long lastMaxns  = 0L;
    private volatile long lastPreValidationQueueP50ns = 0L;
    private volatile long lastPreValidationQueueP75ns = 0L;
    private volatile long lastPreValidationQueueP90ns = 0L;
    private volatile long lastPreValidationQueueMaxns = 0L;
    private volatile long lastIngressPublishP50ns = 0L;
    private volatile long lastIngressPublishP75ns = 0L;
    private volatile long lastIngressPublishP90ns = 0L;
    private volatile long lastIngressPublishMaxns = 0L;
    private volatile long lastDisruptorQueueP50ns = 0L;
    private volatile long lastDisruptorQueueP75ns = 0L;
    private volatile long lastDisruptorQueueP90ns = 0L;
    private volatile long lastDisruptorQueueMaxns = 0L;
    private volatile long lastValidationP50ns = 0L;
    private volatile long lastValidationP75ns = 0L;
    private volatile long lastValidationP90ns = 0L;
    private volatile long lastValidationMaxns = 0L;
    private volatile long lastFillStrategyP50ns = 0L;
    private volatile long lastFillStrategyP75ns = 0L;
    private volatile long lastFillStrategyP90ns = 0L;
    private volatile long lastFillStrategyMaxns = 0L;
    private volatile long lastExecutionReportP50ns = 0L;
    private volatile long lastExecutionReportP75ns = 0L;
    private volatile long lastExecutionReportP90ns = 0L;
    private volatile long lastExecutionReportMaxns = 0L;
    private volatile long lastMetricsPublishP50ns = 0L;
    private volatile long lastMetricsPublishP75ns = 0L;
    private volatile long lastMetricsPublishP90ns = 0L;
    private volatile long lastMetricsPublishMaxns = 0L;
    private volatile long lastOutboundQueueP50ns = 0L;
    private volatile long lastOutboundQueueP75ns = 0L;
    private volatile long lastOutboundQueueP90ns = 0L;
    private volatile long lastOutboundQueueMaxns = 0L;
    private volatile long lastOutboundSendP50ns = 0L;
    private volatile long lastOutboundSendP75ns = 0L;
    private volatile long lastOutboundSendP90ns = 0L;
    private volatile long lastOutboundSendMaxns = 0L;

    // ── Counters ──────────────────────────────────────────────────────────────
    private volatile long ordersReceived;
    private volatile long fillsSent;
    private volatile long rejectsSent;
    private volatile long cancelsSent;
    private volatile long execReportsSent;

    // Pre-allocated snapshot array: [orders, execReports, fills, rejects, cancels, p50, p99, p999, max, tps]
    private final long[] snapshotBuf = new long[10];

    private final ThroughputTracker throughputTracker;

    public MetricsRegistry() {
        this.throughputTracker = new ThroughputTracker();
    }

    // ── Hot-path recording (disruptor thread only) ────────────────────────────

    public void recordLatency(long nanos) {
        rollingWindow.recordValue(Math.min(nanos, MAX_TRACKABLE_NS));
        throughputTracker.increment();
    }

    public void recordStageLatencies(long preValidationQueueNs, long ingressPublishNs, long disruptorQueueNs,
                                     long validationNs, long fillStrategyNs,
                                     long executionReportNs, long metricsPublishNs) {
        preValidationQueueWindow.recordValue(Math.min(preValidationQueueNs, MAX_TRACKABLE_NS));
        ingressPublishWindow.recordValue(Math.min(ingressPublishNs, MAX_TRACKABLE_NS));
        disruptorQueueWindow.recordValue(Math.min(disruptorQueueNs, MAX_TRACKABLE_NS));
        validationWindow.recordValue(Math.min(validationNs, MAX_TRACKABLE_NS));
        fillStrategyWindow.recordValue(Math.min(fillStrategyNs, MAX_TRACKABLE_NS));
        executionReportWindow.recordValue(Math.min(executionReportNs, MAX_TRACKABLE_NS));
        metricsPublishWindow.recordValue(Math.min(metricsPublishNs, MAX_TRACKABLE_NS));
    }

    public void recordOutboundLatencies(long queueNs, long sendNs) {
        outboundQueueWindow.recordValue(Math.min(queueNs, MAX_TRACKABLE_NS));
        outboundSendWindow.recordValue(Math.min(sendNs, MAX_TRACKABLE_NS));
    }

    public void incrementOrdersReceived()  { ordersReceived++; }
    public void incrementFills()           { fillsSent++;   execReportsSent++; }
    public void incrementRejects()         { rejectsSent++; execReportsSent++; }
    public void incrementCancels()         { cancelsSent++; execReportsSent++; }

    // ── Snapshot (called from disruptor thread in MetricsPublishHandler) ─────

    /**
     * Computes percentiles from the current rolling window, stores them in
     * volatile longs, then resets the window. Returns a pre-allocated
     * {@code long[10]} — no heap allocation.
     */
    public long[] snapshot() {
        // Flip the rolling window: compute then reset
        if (rollingWindow.getTotalCount() > 0) {
            lastP50ns  = rollingWindow.getValueAtPercentile(50.0);
            lastP75ns  = rollingWindow.getValueAtPercentile(75.0);
            lastP80ns  = rollingWindow.getValueAtPercentile(80.0);
            lastP90ns  = rollingWindow.getValueAtPercentile(90.0);
            lastP99ns  = rollingWindow.getValueAtPercentile(99.0);
            lastMaxns  = rollingWindow.getMaxValue();
            rollingWindow.reset();
        }
        if (preValidationQueueWindow.getTotalCount() > 0) {
            lastPreValidationQueueP50ns = preValidationQueueWindow.getValueAtPercentile(50.0);
            lastPreValidationQueueP75ns = preValidationQueueWindow.getValueAtPercentile(75.0);
            lastPreValidationQueueP90ns = preValidationQueueWindow.getValueAtPercentile(90.0);
            lastPreValidationQueueMaxns = preValidationQueueWindow.getMaxValue();
            preValidationQueueWindow.reset();
        }
        if (ingressPublishWindow.getTotalCount() > 0) {
            lastIngressPublishP50ns = ingressPublishWindow.getValueAtPercentile(50.0);
            lastIngressPublishP75ns = ingressPublishWindow.getValueAtPercentile(75.0);
            lastIngressPublishP90ns = ingressPublishWindow.getValueAtPercentile(90.0);
            lastIngressPublishMaxns = ingressPublishWindow.getMaxValue();
            ingressPublishWindow.reset();
        }
        if (disruptorQueueWindow.getTotalCount() > 0) {
            lastDisruptorQueueP50ns = disruptorQueueWindow.getValueAtPercentile(50.0);
            lastDisruptorQueueP75ns = disruptorQueueWindow.getValueAtPercentile(75.0);
            lastDisruptorQueueP90ns = disruptorQueueWindow.getValueAtPercentile(90.0);
            lastDisruptorQueueMaxns = disruptorQueueWindow.getMaxValue();
            disruptorQueueWindow.reset();
        }
        if (validationWindow.getTotalCount() > 0) {
            lastValidationP50ns = validationWindow.getValueAtPercentile(50.0);
            lastValidationP75ns = validationWindow.getValueAtPercentile(75.0);
            lastValidationP90ns = validationWindow.getValueAtPercentile(90.0);
            lastValidationMaxns = validationWindow.getMaxValue();
            validationWindow.reset();
        }
        if (fillStrategyWindow.getTotalCount() > 0) {
            lastFillStrategyP50ns = fillStrategyWindow.getValueAtPercentile(50.0);
            lastFillStrategyP75ns = fillStrategyWindow.getValueAtPercentile(75.0);
            lastFillStrategyP90ns = fillStrategyWindow.getValueAtPercentile(90.0);
            lastFillStrategyMaxns = fillStrategyWindow.getMaxValue();
            fillStrategyWindow.reset();
        }
        if (executionReportWindow.getTotalCount() > 0) {
            lastExecutionReportP50ns = executionReportWindow.getValueAtPercentile(50.0);
            lastExecutionReportP75ns = executionReportWindow.getValueAtPercentile(75.0);
            lastExecutionReportP90ns = executionReportWindow.getValueAtPercentile(90.0);
            lastExecutionReportMaxns = executionReportWindow.getMaxValue();
            executionReportWindow.reset();
        }
        if (metricsPublishWindow.getTotalCount() > 0) {
            lastMetricsPublishP50ns = metricsPublishWindow.getValueAtPercentile(50.0);
            lastMetricsPublishP75ns = metricsPublishWindow.getValueAtPercentile(75.0);
            lastMetricsPublishP90ns = metricsPublishWindow.getValueAtPercentile(90.0);
            lastMetricsPublishMaxns = metricsPublishWindow.getMaxValue();
            metricsPublishWindow.reset();
        }

        outboundQueueInterval = intervalHistogram(outboundQueueWindow, outboundQueueInterval);
        if (outboundQueueInterval.getTotalCount() > 0) {
            lastOutboundQueueP50ns = outboundQueueInterval.getValueAtPercentile(50.0);
            lastOutboundQueueP75ns = outboundQueueInterval.getValueAtPercentile(75.0);
            lastOutboundQueueP90ns = outboundQueueInterval.getValueAtPercentile(90.0);
            lastOutboundQueueMaxns = outboundQueueInterval.getMaxValue();
        }

        outboundSendInterval = intervalHistogram(outboundSendWindow, outboundSendInterval);
        if (outboundSendInterval.getTotalCount() > 0) {
            lastOutboundSendP50ns = outboundSendInterval.getValueAtPercentile(50.0);
            lastOutboundSendP75ns = outboundSendInterval.getValueAtPercentile(75.0);
            lastOutboundSendP90ns = outboundSendInterval.getValueAtPercentile(90.0);
            lastOutboundSendMaxns = outboundSendInterval.getMaxValue();
        }

        snapshotBuf[0] = ordersReceived;
        snapshotBuf[1] = execReportsSent;
        snapshotBuf[2] = fillsSent;
        snapshotBuf[3] = rejectsSent;
        snapshotBuf[4] = cancelsSent;
        snapshotBuf[5] = lastP80ns;
        snapshotBuf[6] = lastP90ns;
        snapshotBuf[7] = lastP99ns;
        snapshotBuf[8] = lastMaxns;
        snapshotBuf[9] = throughputTracker.snapshotPerSecond();
        return snapshotBuf;
    }

    // ── Reset (called from REST thread — minor race on rollingWindow is ───────
    //    acceptable: reset is user-triggered and infrequent) ──────────────────

    public void reset() {
        rollingWindow.reset();
        preValidationQueueWindow.reset();
        ingressPublishWindow.reset();
        disruptorQueueWindow.reset();
        validationWindow.reset();
        fillStrategyWindow.reset();
        executionReportWindow.reset();
        metricsPublishWindow.reset();
        outboundQueueInterval = intervalHistogram(outboundQueueWindow, outboundQueueInterval);
        outboundQueueInterval.reset();
        outboundSendInterval = intervalHistogram(outboundSendWindow, outboundSendInterval);
        outboundSendInterval.reset();
        lastP50ns = lastP75ns = lastP80ns = lastP90ns = lastP99ns = lastMaxns = 0L;
        lastPreValidationQueueP50ns = lastPreValidationQueueP75ns = lastPreValidationQueueP90ns = lastPreValidationQueueMaxns = 0L;
        lastIngressPublishP50ns = lastIngressPublishP75ns = lastIngressPublishP90ns = lastIngressPublishMaxns = 0L;
        lastDisruptorQueueP50ns = lastDisruptorQueueP75ns = lastDisruptorQueueP90ns = lastDisruptorQueueMaxns = 0L;
        lastValidationP50ns = lastValidationP75ns = lastValidationP90ns = lastValidationMaxns = 0L;
        lastFillStrategyP50ns = lastFillStrategyP75ns = lastFillStrategyP90ns = lastFillStrategyMaxns = 0L;
        lastExecutionReportP50ns = lastExecutionReportP75ns = lastExecutionReportP90ns = lastExecutionReportMaxns = 0L;
        lastMetricsPublishP50ns = lastMetricsPublishP75ns = lastMetricsPublishP90ns = lastMetricsPublishMaxns = 0L;
        lastOutboundQueueP50ns = lastOutboundQueueP75ns = lastOutboundQueueP90ns = lastOutboundQueueMaxns = 0L;
        lastOutboundSendP50ns = lastOutboundSendP75ns = lastOutboundSendP90ns = lastOutboundSendMaxns = 0L;
        ordersReceived = 0L;
        fillsSent = 0L;
        rejectsSent = 0L;
        cancelsSent = 0L;
        execReportsSent = 0L;
        throughputTracker.reset();
        Arrays.fill(snapshotBuf, 0L);
    }

    // ── REST-safe accessors (volatile reads — any thread) ────────────────────
    public long getOrdersReceived()  { return ordersReceived; }
    public long getFillsSent()       { return fillsSent; }
    public long getRejectsSent()     { return rejectsSent; }
    public long getCancelsSent()     { return cancelsSent; }
    public long getExecReportsSent() { return execReportsSent; }
    public long getP50Ns()           { return lastP50ns; }
    public long getP75Ns()           { return lastP75ns; }
    public long getP80Ns()           { return lastP80ns; }
    public long getP90Ns()           { return lastP90ns; }
    public long getP99Ns()           { return lastP99ns; }
    public long getMaxNs()           { return lastMaxns; }
    public long getPreValidationQueueP50Ns() { return lastPreValidationQueueP50ns; }
    public long getPreValidationQueueP75Ns() { return lastPreValidationQueueP75ns; }
    public long getPreValidationQueueP90Ns() { return lastPreValidationQueueP90ns; }
    public long getPreValidationQueueMaxNs() { return lastPreValidationQueueMaxns; }
    public long getIngressPublishP50Ns() { return lastIngressPublishP50ns; }
    public long getIngressPublishP75Ns() { return lastIngressPublishP75ns; }
    public long getIngressPublishP90Ns() { return lastIngressPublishP90ns; }
    public long getIngressPublishMaxNs() { return lastIngressPublishMaxns; }
    public long getDisruptorQueueP50Ns() { return lastDisruptorQueueP50ns; }
    public long getDisruptorQueueP75Ns() { return lastDisruptorQueueP75ns; }
    public long getDisruptorQueueP90Ns() { return lastDisruptorQueueP90ns; }
    public long getDisruptorQueueMaxNs() { return lastDisruptorQueueMaxns; }
    public long getValidationP50Ns() { return lastValidationP50ns; }
    public long getValidationP75Ns() { return lastValidationP75ns; }
    public long getValidationP90Ns() { return lastValidationP90ns; }
    public long getValidationMaxNs() { return lastValidationMaxns; }
    public long getFillStrategyP50Ns() { return lastFillStrategyP50ns; }
    public long getFillStrategyP75Ns() { return lastFillStrategyP75ns; }
    public long getFillStrategyP90Ns() { return lastFillStrategyP90ns; }
    public long getFillStrategyMaxNs() { return lastFillStrategyMaxns; }
    public long getExecutionReportP50Ns() { return lastExecutionReportP50ns; }
    public long getExecutionReportP75Ns() { return lastExecutionReportP75ns; }
    public long getExecutionReportP90Ns() { return lastExecutionReportP90ns; }
    public long getExecutionReportMaxNs() { return lastExecutionReportMaxns; }
    public long getMetricsPublishP50Ns() { return lastMetricsPublishP50ns; }
    public long getMetricsPublishP75Ns() { return lastMetricsPublishP75ns; }
    public long getMetricsPublishP90Ns() { return lastMetricsPublishP90ns; }
    public long getMetricsPublishMaxNs() { return lastMetricsPublishMaxns; }
    public long getOutboundQueueP50Ns() { return lastOutboundQueueP50ns; }
    public long getOutboundQueueP75Ns() { return lastOutboundQueueP75ns; }
    public long getOutboundQueueP90Ns() { return lastOutboundQueueP90ns; }
    public long getOutboundQueueMaxNs() { return lastOutboundQueueMaxns; }
    public long getOutboundSendP50Ns() { return lastOutboundSendP50ns; }
    public long getOutboundSendP75Ns() { return lastOutboundSendP75ns; }
    public long getOutboundSendP90Ns() { return lastOutboundSendP90ns; }
    public long getOutboundSendMaxNs() { return lastOutboundSendMaxns; }
    public long getThroughputPerSec(){ return throughputTracker.getPerSecond(); }

    private static Histogram intervalHistogram(Recorder recorder, Histogram reuse) {
        return reuse == null ? recorder.getIntervalHistogram() : recorder.getIntervalHistogram(reuse);
    }
}
