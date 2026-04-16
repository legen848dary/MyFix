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

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientFixServiceBulkFlowDisconnectTest {

    @TempDir
    Path tempDir;

    @Test
    void disconnectStopsActiveBulkFlowBeforeReturningToStandby() {
        try (TheFixClientFixService service = new TheFixClientFixService(testConfig(), new TheFixSessionProfileStore(testConfig()))) {
            service.startAutoFlow(testOrder(), new TheFixBulkOptions("FIXED_RATE", 5, 10, 1_000, 0));

            JsonObject activeSnapshot = service.sessionSnapshot();
            assertTrue(activeSnapshot.getBoolean("autoFlowActive"));

            service.disconnect();

            JsonObject disconnectedSnapshot = service.sessionSnapshot();
            assertFalse(disconnectedSnapshot.getBoolean("autoFlowActive"));
            assertTrue(service.recentEventsJson().encode().contains("Bulk flow stopped"));
        }
    }

    private TheFixClientConfig testConfig() {
        return new TheFixClientConfig(
                "127.0.0.1",
                8081,
                "127.0.0.1",
                9880,
                "FIX.4.4",
                "CLIENT",
                "SIM",
                "FIX.4.4",
                30,
                1,
                25,
                tempDir.resolve("quickfix").toString(),
                false,
                480,
                1
        );
    }

    private static TheFixOrderRequest testOrder() {
        return new TheFixOrderRequest(
                "NEW_ORDER_SINGLE",
                "",
                "",
                "AAPL",
                "BUY",
                100,
                100.25d,
                0d,
                "DAY",
                "LIMIT",
                "PER_UNIT",
                "AMERICAS",
                "XNAS",
                "USD",
                java.util.List.of()
        );
    }
}
