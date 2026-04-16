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

package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixBulkOptionsTest {

    // -----------------------------------------------------------------------
    // normalized
    // -----------------------------------------------------------------------

    @Test
    void normalizedFixedRateUsesDefaultsWhenZeroValuesProvided() {
        TheFixBulkOptions opts = new TheFixBulkOptions("FIXED_RATE", 0, 0, 0, 0).normalized(50);

        assertEquals("FIXED_RATE", opts.mode());
        assertEquals(50, opts.ratePerSecond());
        assertEquals(10, opts.burstSize());
        assertEquals(1_000, opts.burstIntervalMs());
        assertEquals(0, opts.totalOrders());
    }

    @Test
    void normalizedPreservesExplicitValues() {
        TheFixBulkOptions opts = new TheFixBulkOptions("BURST", 200, 25, 500, 1000).normalized(50);

        assertEquals("BURST", opts.mode());
        assertEquals(200, opts.ratePerSecond());
        assertEquals(25, opts.burstSize());
        assertEquals(500, opts.burstIntervalMs());
        assertEquals(1000, opts.totalOrders());
    }

    @Test
    void normalizedClampsTotalOrdersToZeroIfNegative() {
        TheFixBulkOptions opts = new TheFixBulkOptions("FIXED_RATE", 10, 5, 100, -5).normalized(10);

        assertEquals(0, opts.totalOrders());
    }

    @Test
    void normalizedConvertsUnknownModeToFixedRate() {
        TheFixBulkOptions opts = new TheFixBulkOptions("CONTINUOUS", 10, 5, 100, 0).normalized(10);

        assertEquals("FIXED_RATE", opts.mode());
    }

    @Test
    void normalizedIsBurstModeIsCaseInsensitive() {
        TheFixBulkOptions opts = new TheFixBulkOptions("burst", 10, 5, 100, 0).normalized(10);

        assertEquals("BURST", opts.mode());
        assertTrue(opts.isBurstMode());
    }

    // -----------------------------------------------------------------------
    // isBurstMode
    // -----------------------------------------------------------------------

    @Test
    void isBurstModeReturnsTrueOnlyForBurstMode() {
        assertTrue(new TheFixBulkOptions("BURST", 1, 1, 1, 0).isBurstMode());
        assertFalse(new TheFixBulkOptions("FIXED_RATE", 1, 1, 1, 0).isBurstMode());
        assertFalse(new TheFixBulkOptions(null, 1, 1, 1, 0).isBurstMode());
    }

    // -----------------------------------------------------------------------
    // describe
    // -----------------------------------------------------------------------

    @Test
    void describeFixedRateContinuous() {
        String description = new TheFixBulkOptions("FIXED_RATE", 100, 10, 1000, 0).describe();

        assertTrue(description.contains("100"), "should contain rate");
        assertTrue(description.contains("continuously"), "should mention continuous");
    }

    @Test
    void describeFixedRateWithTotalOrders() {
        String description = new TheFixBulkOptions("FIXED_RATE", 100, 10, 1000, 500).describe();

        assertTrue(description.contains("100"));
        assertTrue(description.contains("500"), "should mention total orders");
    }

    @Test
    void describeBurstModeContinuous() {
        String description = new TheFixBulkOptions("BURST", 100, 20, 2000, 0).describe();

        assertTrue(description.contains("20"), "should mention burst size");
        assertTrue(description.contains("2000"), "should mention burst interval");
        assertTrue(description.contains("continuously"));
    }

    @Test
    void describeBurstModeWithTotalOrders() {
        String description = new TheFixBulkOptions("BURST", 100, 20, 2000, 400).describe();

        assertTrue(description.contains("400"), "should mention total orders");
    }
}
