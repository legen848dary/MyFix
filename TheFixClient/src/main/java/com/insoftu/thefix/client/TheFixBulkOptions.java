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

