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

record TheFixBulkOptions(String mode, int ratePerSecond, int burstSize, int burstIntervalMs, int totalOrders) {
    TheFixBulkOptions normalized(int defaultRatePerSecond) {
        String normalizedMode = "BURST".equalsIgnoreCase(mode) ? "BURST" : "FIXED_RATE";
        int normalizedRate = ratePerSecond > 0 ? ratePerSecond : defaultRatePerSecond;
        int normalizedBurstSize = burstSize > 0 ? burstSize : 10;
        int normalizedBurstIntervalMs = burstIntervalMs > 0 ? burstIntervalMs : 1_000;
        int normalizedTotalOrders = Math.max(0, totalOrders);
        return new TheFixBulkOptions(normalizedMode, normalizedRate, normalizedBurstSize, normalizedBurstIntervalMs, normalizedTotalOrders);
    }

    boolean isBurstMode() {
        return "BURST".equals(mode);
    }

    String describe() {
        String cadence = isBurstMode()
                ? burstSize + " orders every " + burstIntervalMs + " ms"
                : ratePerSecond + " orders/sec";
        return totalOrders > 0 ? cadence + " for " + totalOrders + " orders" : cadence + " continuously";
    }
}
