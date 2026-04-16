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

package com.llexsimulator.fill;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.fill.strategy.RandomRejectCancelStrategy;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.RejectReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomRejectCancelStrategyTest {

    @Test
    void emitsOnlyRejectOrCancelOutcomesWithinConfiguredDelayRange() {
        RandomRejectCancelStrategy strategy = new RandomRejectCancelStrategy();
        FillBehaviorConfig config = new FillBehaviorConfig();
        config.rejectReason = RejectReason.INVALID_PRICE;
        config.randomMinDelayNs = 1_000_000L;
        config.randomMaxDelayNs = 5_000_000L;

        boolean sawReject = false;
        boolean sawCancel = false;

        for (int i = 0; i < 200; i++) {
            OrderEvent event = new OrderEvent();
            event.correlationId = i + 1L;
            event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder);

            strategy.apply(event, config);

            event.fillInstructionDecoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerDecoder);

            FillBehaviorType behavior = event.fillInstructionDecoder.fillBehavior();
            long delayNs = event.fillInstructionDecoder.delayNs();

            assertTrue(behavior == FillBehaviorType.REJECT || behavior == FillBehaviorType.NO_FILL_IOC_CANCEL);
            assertTrue(delayNs >= config.randomMinDelayNs && delayNs <= config.randomMaxDelayNs);
            assertEquals(RejectReason.INVALID_PRICE, event.fillInstructionDecoder.rejectReasonCode());

            sawReject |= behavior == FillBehaviorType.REJECT;
            sawCancel |= behavior == FillBehaviorType.NO_FILL_IOC_CANCEL;
        }

        assertTrue(sawReject, "Expected at least one random reject outcome");
        assertTrue(sawCancel, "Expected at least one random cancel outcome");
    }

    @Test
    void usesCollapsedDelayWindowWhenMinIsGreaterThanOrEqualToMax() {
        RandomRejectCancelStrategy strategy = new RandomRejectCancelStrategy();
        FillBehaviorConfig config = new FillBehaviorConfig();
        config.randomMinDelayNs = 9L;
        config.randomMaxDelayNs = 9L;

        OrderEvent event = new OrderEvent();
        event.correlationId = 999L;
        event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder);

        strategy.apply(event, config);

        event.fillInstructionDecoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerDecoder);
        assertEquals(9L, event.fillInstructionDecoder.delayNs());
    }
}
