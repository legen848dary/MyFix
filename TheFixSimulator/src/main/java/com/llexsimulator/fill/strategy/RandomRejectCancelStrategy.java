package com.llexsimulator.fill.strategy;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.fill.FillBehaviorConfig;
import com.llexsimulator.fill.FillStrategy;
import com.llexsimulator.sbe.FillBehaviorType;

import java.util.concurrent.ThreadLocalRandom;

/** Randomly rejects or cancels each order, with an optional random delay window. */
public final class RandomRejectCancelStrategy implements FillStrategy {
    @Override
    public void apply(OrderEvent event, FillBehaviorConfig config) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        long delayNs = (config.randomMinDelayNs >= config.randomMaxDelayNs)
                ? config.randomMaxDelayNs
                : rng.nextLong(config.randomMinDelayNs, config.randomMaxDelayNs + 1);

        FillBehaviorType resolvedBehavior = rng.nextBoolean()
                ? FillBehaviorType.REJECT
                : FillBehaviorType.NO_FILL_IOC_CANCEL;

        event.fillInstructionEncoder
                .correlationId(event.correlationId)
                .fillBehavior(resolvedBehavior)
                .fillPctBps(0)
                .numPartialFills((short) 0)
                .delayNs(delayNs)
                .fillPrice(0L)
                .rejectReasonCode(config.rejectReason)
                .randomMinQtyPct(0)
                .randomMaxQtyPct(0)
                .randomMinDelayNs(config.randomMinDelayNs)
                .randomMaxDelayNs(config.randomMaxDelayNs);
    }
}
