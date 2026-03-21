package com.llexsimulator.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Sliding 1-second throughput tracker without a background thread.
 *
 * <p>The hot path only performs a single volatile increment from the Disruptor
 * thread. Throughput windows are rolled forward lazily when the UI or metrics
 * publisher reads them, which keeps the datapath lean and also guarantees the
 * GUI can refresh the value even when no new orders arrive during a given poll.
 */
public final class ThroughputTracker {

    private final LongSupplier nanoTimeSupplier;

    private final AtomicLong totalCount = new AtomicLong();
    private volatile long lastPerSecond;

    private long lastObservedTotal;
    private long lastObservedAtNs;

    public ThroughputTracker() {
        this(System::nanoTime);
    }

    ThroughputTracker(LongSupplier nanoTimeSupplier) {
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.lastObservedAtNs = nanoTimeSupplier.getAsLong();
    }

    public void increment() {
        totalCount.incrementAndGet();
    }

    public long getPerSecond() {
        return snapshotPerSecond();
    }

    public long snapshotPerSecond() {
        return snapshotPerSecond(nanoTimeSupplier.getAsLong());
    }

    synchronized long snapshotPerSecond(long nowNs) {
        long elapsedNs = nowNs - lastObservedAtNs;
        if (elapsedNs < 1_000_000_000L) {
            return lastPerSecond;
        }

        long observedTotal = totalCount.get();
        long delta = observedTotal - lastObservedTotal;
        lastPerSecond = Math.round((delta * 1_000_000_000d) / Math.max(1L, elapsedNs));
        lastObservedTotal = observedTotal;
        lastObservedAtNs = nowNs;
        return lastPerSecond;
    }

    public void reset() {
        totalCount.set(0L);
        lastPerSecond = 0L;
        lastObservedTotal = 0L;
        lastObservedAtNs = nanoTimeSupplier.getAsLong();
    }
}

