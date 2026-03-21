package com.llexsimulator.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThroughputTrackerTest {

    @Test
    void rollsThroughputWindowOnDemandWithoutSleep() {
        AtomicLong clock = new AtomicLong(0L);
        ThroughputTracker tracker = new ThroughputTracker(clock::get);

        tracker.increment();
        tracker.increment();
        tracker.increment();
        assertEquals(0L, tracker.snapshotPerSecond(clock.get()));

        clock.set(1_000_000_000L);
        assertEquals(3L, tracker.snapshotPerSecond(clock.get()));

        tracker.increment();
        tracker.increment();
        clock.set(2_000_000_000L);
        assertEquals(2L, tracker.snapshotPerSecond(clock.get()));
    }
}
