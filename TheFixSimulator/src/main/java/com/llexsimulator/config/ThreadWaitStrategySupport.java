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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class ThreadWaitStrategySupport {

    private static final Logger log = LoggerFactory.getLogger(ThreadWaitStrategySupport.class);

    private ThreadWaitStrategySupport() {}

    public static WaitStrategy resolveDisruptorWaitStrategy(String raw, String strategyOwner) {
        String normalized = normalize(raw);
        return switch (normalized) {
            case "BUSY_SPIN" -> new BusySpinWaitStrategy();
            case "YIELDING" -> new YieldingWaitStrategy();
            case "BLOCKING" -> new BlockingWaitStrategy();
            case "SLEEPING", "SLEEP" -> new SleepingWaitStrategy(0, 100_000L);
            default -> {
                log.warn("Unsupported wait strategy '{}' for {} — falling back to SLEEPING", raw, strategyOwner);
                yield new SleepingWaitStrategy(0, 100_000L);
            }
        };
    }

    public static IdleStrategy resolveLoopIdleStrategy(String raw, IdleStrategy defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        return switch (normalize(raw)) {
            case "NOOP", "NO_OP" -> new NoOpIdleStrategy();
            case "BUSY_SPIN" -> new BusySpinIdleStrategy();
            case "BACKOFF", "BACK_OFF" -> new BackoffIdleStrategy();
            case "SLEEP", "SLEEPING" -> new SleepingIdleStrategy();
            case "YIELD", "YIELDING" -> new YieldingIdleStrategy();
            default -> {
                log.warn("Unsupported loop idle strategy '{}' — falling back to {}", raw,
                        defaultValue.getClass().getSimpleName());
                yield defaultValue;
            }
        };
    }

    public static boolean isBusySpin(String raw) {
        return "BUSY_SPIN".equals(normalize(raw));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
