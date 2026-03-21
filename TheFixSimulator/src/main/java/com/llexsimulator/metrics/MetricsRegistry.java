package com.llexsimulator.metrics;

import org.HdrHistogram.Histogram;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics store — all operations are lock-free and zero-GC.
 *
 * <p>Latency is tracked with a <em>rolling-window</em> {@link Histogram}: each
 * time {@link #snapshot()} is called (once per metrics-publish cycle from the
 * Disruptor thread), percentiles are computed from the current window, stored
 * in {@code volatile} longs, and the histogram is reset for the next window.
 * This means p50/p99/p999/max always reflect <em>recent</em> activity only —
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

    // ── Last-computed percentiles — written by disruptor, read by any thread ─
    private volatile long lastP80ns  = 0L;
    private volatile long lastP90ns  = 0L;
    private volatile long lastP99ns  = 0L;

    // ── Counters ──────────────────────────────────────────────────────────────
    private final AtomicLong ordersReceived = new AtomicLong();
    private final AtomicLong fillsSent = new AtomicLong();
    private final AtomicLong rejectsSent = new AtomicLong();
    private final AtomicLong cancelsSent = new AtomicLong();
    private final AtomicLong execReportsSent = new AtomicLong();

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

    public void incrementOrdersReceived()  { ordersReceived.incrementAndGet(); }
    public void incrementFills()           { fillsSent.incrementAndGet();   execReportsSent.incrementAndGet(); }
    public void incrementRejects()         { rejectsSent.incrementAndGet(); execReportsSent.incrementAndGet(); }
    public void incrementCancels()         { cancelsSent.incrementAndGet(); execReportsSent.incrementAndGet(); }

    // ── Snapshot (called from disruptor thread in MetricsPublishHandler) ─────

    /**
     * Computes percentiles from the current rolling window, stores them in
     * volatile longs, then resets the window. Returns a pre-allocated
     * {@code long[10]} — no heap allocation.
     */
    public long[] snapshot() {
        // Flip the rolling window: compute then reset
        if (rollingWindow.getTotalCount() > 0) {
            lastP80ns  = rollingWindow.getValueAtPercentile(80.0);
            lastP90ns  = rollingWindow.getValueAtPercentile(90.0);
            lastP99ns  = rollingWindow.getValueAtPercentile(99.0);
            rollingWindow.reset();
        }

        snapshotBuf[0] = ordersReceived.get();
        snapshotBuf[1] = execReportsSent.get();
        snapshotBuf[2] = fillsSent.get();
        snapshotBuf[3] = rejectsSent.get();
        snapshotBuf[4] = cancelsSent.get();
        snapshotBuf[5] = lastP80ns;
        snapshotBuf[6] = lastP90ns;
        snapshotBuf[7] = lastP99ns;
        snapshotBuf[8] = 0L; // reserved
        snapshotBuf[9] = throughputTracker.snapshotPerSecond();
        return snapshotBuf;
    }

    // ── Reset (called from REST thread — minor race on rollingWindow is ───────
    //    acceptable: reset is user-triggered and infrequent) ──────────────────

    public void reset() {
        rollingWindow.reset();
        lastP80ns = lastP90ns = lastP99ns = 0L;
        ordersReceived.set(0L);
        fillsSent.set(0L);
        rejectsSent.set(0L);
        cancelsSent.set(0L);
        execReportsSent.set(0L);
        throughputTracker.reset();
        Arrays.fill(snapshotBuf, 0L);
    }

    // ── REST-safe accessors (volatile reads — any thread) ────────────────────
    public long getOrdersReceived()  { return ordersReceived.get(); }
    public long getFillsSent()       { return fillsSent.get(); }
    public long getRejectsSent()     { return rejectsSent.get(); }
    public long getCancelsSent()     { return cancelsSent.get(); }
    public long getExecReportsSent() { return execReportsSent.get(); }
    public long getP80Ns()           { return lastP80ns; }
    public long getP90Ns()           { return lastP90ns; }
    public long getP99Ns()           { return lastP99ns; }
    public long getThroughputPerSec(){ return throughputTracker.getPerSecond(); }
}
