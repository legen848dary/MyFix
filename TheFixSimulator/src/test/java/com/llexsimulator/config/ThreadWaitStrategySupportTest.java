package com.llexsimulator.config;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadWaitStrategySupportTest {

    @Test
    void resolvesKnownDisruptorStrategies() {
        assertInstanceOf(BusySpinWaitStrategy.class,
                ThreadWaitStrategySupport.resolveDisruptorWaitStrategy("busy_spin", "disruptor"));
        assertInstanceOf(YieldingWaitStrategy.class,
                ThreadWaitStrategySupport.resolveDisruptorWaitStrategy("yielding", "disruptor"));
        assertInstanceOf(BlockingWaitStrategy.class,
                ThreadWaitStrategySupport.resolveDisruptorWaitStrategy("blocking", "disruptor"));
        assertInstanceOf(SleepingWaitStrategy.class,
                ThreadWaitStrategySupport.resolveDisruptorWaitStrategy("sleep", "disruptor"));
    }

    @Test
    void fallsBackToSleepingForUnknownDisruptorStrategy() {
        WaitStrategy strategy = ThreadWaitStrategySupport.resolveDisruptorWaitStrategy("mystery", "disruptor");
        assertInstanceOf(SleepingWaitStrategy.class, strategy);
    }

    @Test
    void resolvesKnownLoopIdleStrategies() {
        assertInstanceOf(NoOpIdleStrategy.class,
                ThreadWaitStrategySupport.resolveLoopIdleStrategy("noop", new BusySpinIdleStrategy()));
        assertInstanceOf(BusySpinIdleStrategy.class,
                ThreadWaitStrategySupport.resolveLoopIdleStrategy("busy_spin", new NoOpIdleStrategy()));
        assertInstanceOf(BackoffIdleStrategy.class,
                ThreadWaitStrategySupport.resolveLoopIdleStrategy("backoff", new NoOpIdleStrategy()));
        assertInstanceOf(SleepingIdleStrategy.class,
                ThreadWaitStrategySupport.resolveLoopIdleStrategy("sleeping", new NoOpIdleStrategy()));
        assertInstanceOf(YieldingIdleStrategy.class,
                ThreadWaitStrategySupport.resolveLoopIdleStrategy("yielding", new NoOpIdleStrategy()));
    }

    @Test
    void fallsBackToProvidedIdleStrategyForBlankOrUnknownLoopStrategy() {
        IdleStrategy fallback = new NoOpIdleStrategy();
        assertSame(fallback, ThreadWaitStrategySupport.resolveLoopIdleStrategy(null, fallback));
        assertSame(fallback, ThreadWaitStrategySupport.resolveLoopIdleStrategy("   ", fallback));
        assertSame(fallback, ThreadWaitStrategySupport.resolveLoopIdleStrategy("mystery", fallback));
    }

    @Test
    void detectsBusySpinCaseInsensitively() {
        assertTrue(ThreadWaitStrategySupport.isBusySpin("BUSY_SPIN"));
        assertTrue(ThreadWaitStrategySupport.isBusySpin("busy-spin"));
        assertFalse(ThreadWaitStrategySupport.isBusySpin("sleeping"));
        assertFalse(ThreadWaitStrategySupport.isBusySpin(null));
    }
}
