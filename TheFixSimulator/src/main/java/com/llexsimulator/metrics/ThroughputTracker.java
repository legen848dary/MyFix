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

package com.llexsimulator.metrics;

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

    private long totalCount;
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
        totalCount++;
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

        long observedTotal = totalCount;
        long delta = observedTotal - lastObservedTotal;
        lastPerSecond = Math.round((delta * 1_000_000_000d) / elapsedNs);
        lastObservedTotal = observedTotal;
        lastObservedAtNs = nowNs;
        return lastPerSecond;
    }

    public void reset() {
        totalCount = 0L;
        lastPerSecond = 0L;
        lastObservedTotal = 0L;
        lastObservedAtNs = nanoTimeSupplier.getAsLong();
    }
}
