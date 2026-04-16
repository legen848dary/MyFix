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

import com.llexsimulator.fill.strategy.*;
import com.llexsimulator.sbe.FillBehaviorType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Singleton registry of all {@link FillStrategy} implementations.
 * Each strategy instance is pre-created once at startup — zero allocation on the hot path.
 */
public final class FillStrategyFactory {

    private static final Map<FillBehaviorType, FillStrategy> STRATEGIES =
            new EnumMap<>(FillBehaviorType.class);

    static {
        STRATEGIES.put(FillBehaviorType.IMMEDIATE_FULL_FILL,   new ImmediateFillStrategy());
        STRATEGIES.put(FillBehaviorType.PARTIAL_FILL,          new PartialFillStrategy());
        STRATEGIES.put(FillBehaviorType.DELAYED_FILL,          new DelayedFillStrategy());
        STRATEGIES.put(FillBehaviorType.REJECT,                new RejectStrategy());
        STRATEGIES.put(FillBehaviorType.PARTIAL_THEN_CANCEL,   new PartialFillThenCancelStrategy());
        STRATEGIES.put(FillBehaviorType.PRICE_IMPROVEMENT,     new PriceImprovementFillStrategy());
        STRATEGIES.put(FillBehaviorType.FILL_AT_ARRIVAL_PRICE, new FillAtArrivalPriceStrategy());
        STRATEGIES.put(FillBehaviorType.RANDOM_FILL,           new RandomFillStrategy());
        STRATEGIES.put(FillBehaviorType.NO_FILL_IOC_CANCEL,    new IocCancelStrategy());
        STRATEGIES.put(FillBehaviorType.valueOf("RANDOM_REJECT_CANCEL"), new RandomRejectCancelStrategy());
    }

    private FillStrategyFactory() {}

    public static FillStrategy getStrategy(FillBehaviorType type) {
        FillStrategy s = STRATEGIES.get(type);
        if (s == null) {
            throw new IllegalArgumentException("No strategy registered for: " + type);
        }
        return s;
    }
}
