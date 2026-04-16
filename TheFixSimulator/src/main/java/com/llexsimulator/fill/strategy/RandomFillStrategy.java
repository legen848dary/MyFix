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

package com.llexsimulator.fill.strategy;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.fill.FillBehaviorConfig;
import com.llexsimulator.fill.FillStrategy;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.RejectReason;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomizes both fill quantity (within configured bps range) and
 * fill delay (within configured nanosecond range).
 *
 * <p>Uses {@link ThreadLocalRandom} exclusively — zero allocation, no lock contention.
 */
public final class RandomFillStrategy implements FillStrategy {
    @Override
    public void apply(OrderEvent event, FillBehaviorConfig config) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int  fillPct  = (config.randomMinQtyPctBps >= config.randomMaxQtyPctBps)
                        ? config.randomMaxQtyPctBps
                        : rng.nextInt(config.randomMinQtyPctBps, config.randomMaxQtyPctBps + 1);
        long delayNs  = (config.randomMinDelayNs >= config.randomMaxDelayNs)
                        ? config.randomMaxDelayNs
                        : rng.nextLong(config.randomMinDelayNs, config.randomMaxDelayNs + 1);

        event.fillInstructionEncoder
                .correlationId(event.correlationId)
                .fillBehavior(FillBehaviorType.RANDOM_FILL)
                .fillPctBps(fillPct)
                .numPartialFills((short) 1)
                .delayNs(delayNs)
                .fillPrice(event.nosDecoder.price())
                .rejectReasonCode(RejectReason.SIMULATOR_REJECT)
                .randomMinQtyPct(config.randomMinQtyPctBps)
                .randomMaxQtyPct(config.randomMaxQtyPctBps)
                .randomMinDelayNs(config.randomMinDelayNs)
                .randomMaxDelayNs(config.randomMaxDelayNs);
    }
}
