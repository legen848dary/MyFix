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

/**
 * Strategy interface for fill behavior.
 *
 * <p>Implementations MUST be stateless and allocation-free — a single instance
 * is shared across all Disruptor processing cycles. All mutable per-call state
 * is written into {@code event.fillInstructionBuffer} via the pre-allocated
 * SBE {@code FillInstructionEncoder}.
 */
@FunctionalInterface
public interface FillStrategy {

    /**
     * Populate the {@code FillInstruction} in the event's fill buffer
     * according to this strategy and the supplied config snapshot.
     *
     * @param event  the current Disruptor ring-buffer slot (pre-allocated, reused)
     * @param config a volatile read of the current fill-behavior configuration
     */
    void apply(OrderEvent event, FillBehaviorConfig config);
}
