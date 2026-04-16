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

package com.llexsimulator.aeron;

import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class AeronContextTest {

    @Test
    void resolvesThreadingModeCaseInsensitively() {
        assertSame(ThreadingMode.SHARED, AeronContext.resolveThreadingMode("shared", ThreadingMode.DEDICATED));
        assertSame(ThreadingMode.SHARED_NETWORK, AeronContext.resolveThreadingMode("SHARED_NETWORK", ThreadingMode.DEDICATED));
        assertSame(ThreadingMode.DEDICATED, AeronContext.resolveThreadingMode(null, ThreadingMode.DEDICATED));
    }

    @Test
    void fallsBackForUnknownThreadingMode() {
        assertSame(ThreadingMode.DEDICATED, AeronContext.resolveThreadingMode("not-a-mode", ThreadingMode.DEDICATED));
    }

    @Test
    void resolvesKnownIdleStrategies() {
        assertInstanceOf(NoOpIdleStrategy.class,
                AeronContext.resolveIdleStrategy("noop", new BusySpinIdleStrategy()));
        assertInstanceOf(BusySpinIdleStrategy.class,
                AeronContext.resolveIdleStrategy("busy_spin", new NoOpIdleStrategy()));
        assertInstanceOf(BackoffIdleStrategy.class,
                AeronContext.resolveIdleStrategy("backoff", new NoOpIdleStrategy()));
        assertInstanceOf(SleepingIdleStrategy.class,
                AeronContext.resolveIdleStrategy("sleeping", new NoOpIdleStrategy()));
        assertInstanceOf(YieldingIdleStrategy.class,
                AeronContext.resolveIdleStrategy("yielding", new NoOpIdleStrategy()));
    }

    @Test
    void fallsBackForUnknownIdleStrategy() {
        NoOpIdleStrategy fallback = new NoOpIdleStrategy();
        assertSame(fallback, AeronContext.resolveIdleStrategy("mystery", fallback));
    }
}
