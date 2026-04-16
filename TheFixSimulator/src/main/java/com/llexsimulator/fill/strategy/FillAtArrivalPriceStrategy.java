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

/**
 * Fills at the price captured at order arrival time ({@code arrivalTimeNs} snapshot).
 * Useful for simulating market-impact scenarios where the execution price
 * equals the market price at the moment the order was received.
 */
public final class FillAtArrivalPriceStrategy implements FillStrategy {
    @Override
    public void apply(OrderEvent event, FillBehaviorConfig config) {
        // For this simulator, "arrival price" = the order's own limit price
        // since we don't have a live market feed. This can be overridden by
        // setting fillPriceOverride in the config.
        long fillPrice = config.fillPriceOverride != 0
                         ? config.fillPriceOverride
                         : event.nosDecoder.price();

        event.fillInstructionEncoder
                .correlationId(event.correlationId)
                .fillBehavior(FillBehaviorType.FILL_AT_ARRIVAL_PRICE)
                .fillPctBps(10_000)
                .numPartialFills((short) 1)
                .delayNs(0L)
                .fillPrice(fillPrice)
                .rejectReasonCode(RejectReason.SIMULATOR_REJECT)
                .randomMinQtyPct(0)
                .randomMaxQtyPct(0)
                .randomMinDelayNs(0L)
                .randomMaxDelayNs(0L);
    }
}
