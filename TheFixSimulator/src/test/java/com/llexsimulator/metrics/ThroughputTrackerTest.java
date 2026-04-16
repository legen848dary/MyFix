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
