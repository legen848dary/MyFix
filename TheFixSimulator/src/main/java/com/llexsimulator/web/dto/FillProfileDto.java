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

package com.llexsimulator.web.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** REST payload for creating / updating a fill-behavior profile. */
public final class FillProfileDto {

    public String name;
    public String description;
    /** Must match a {@link com.llexsimulator.sbe.FillBehaviorType} name. */
    public String behaviorType;
    /** Fill percentage in basis points (0–10000). */
    public int    fillPctBps;
    public int    numPartialFills;
    /** Delay in milliseconds (converted to nanoseconds internally). */
    public long   delayMs;
    /** Must match a {@link com.llexsimulator.sbe.RejectReason} name, or null. */
    public String rejectReason;
    public int    randomMinQtyPct;
    public int    randomMaxQtyPct;
    public long   randomMinDelayMs;
    public long   randomMaxDelayMs;
    public int    priceImprovementBps;

    public FillProfileDto() {}

    @JsonCreator
    public FillProfileDto(
            @JsonProperty("name")               String name,
            @JsonProperty("description")        String description,
            @JsonProperty("behaviorType")       String behaviorType,
            @JsonProperty("fillPctBps")         int    fillPctBps,
            @JsonProperty("numPartialFills")    int    numPartialFills,
            @JsonProperty("delayMs")            long   delayMs,
            @JsonProperty("rejectReason")       String rejectReason,
            @JsonProperty("randomMinQtyPct")    int    randomMinQtyPct,
            @JsonProperty("randomMaxQtyPct")    int    randomMaxQtyPct,
            @JsonProperty("randomMinDelayMs")   long   randomMinDelayMs,
            @JsonProperty("randomMaxDelayMs")   long   randomMaxDelayMs,
            @JsonProperty("priceImprovementBps") int   priceImprovementBps) {
        this.name               = name;
        this.description        = description;
        this.behaviorType       = behaviorType;
        this.fillPctBps         = fillPctBps;
        this.numPartialFills    = numPartialFills;
        this.delayMs            = delayMs;
        this.rejectReason       = rejectReason;
        this.randomMinQtyPct    = randomMinQtyPct;
        this.randomMaxQtyPct    = randomMaxQtyPct;
        this.randomMinDelayMs   = randomMinDelayMs;
        this.randomMaxDelayMs   = randomMaxDelayMs;
        this.priceImprovementBps = priceImprovementBps;
    }
}
