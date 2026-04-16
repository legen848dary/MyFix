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

package com.llexsimulator.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import quickfix.Log;
import quickfix.SessionID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCoverageTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("fix.demo.side");
        System.clearProperty("fix.demo.orderQty");
        System.clearProperty("fix.demo.price");
        System.clearProperty("fix.demo.host");
    }

    @Test
    void noOpQuickFixLogFactoryReturnsReusableNoOpLog() {
        NoOpQuickFixLogFactory factory = new NoOpQuickFixLogFactory();
        Log log = factory.create(new SessionID("FIX.4.4", "A", "B"));

        log.clear();
        log.onIncoming("incoming");
        log.onOutgoing("outgoing");
        log.onEvent("event");
        log.onErrorEvent("error");

        assertNotNull(log);
        assertEquals(log, factory.create(new SessionID("FIX.4.4", "X", "Y")));
    }

    @Test
    void fixDemoClientConfigRejectsInvalidPropertyShapes() {
        System.setProperty("fix.demo.side", "HOLD");
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.from(new String[0]));

        System.setProperty("fix.demo.side", "BUY");
        System.setProperty("fix.demo.orderQty", "0");
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.from(new String[0]));

        System.setProperty("fix.demo.orderQty", "10");
        System.setProperty("fix.demo.price", "abc");
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.from(new String[0]));

        System.clearProperty("fix.demo.price");
        System.setProperty("fix.demo.host", "   ");
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.from(new String[0]));
    }

    @Test
    void resolveRateRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.resolveRatePerSecond("0", null, null));
        assertThrows(IllegalArgumentException.class, () -> FixDemoClientConfig.resolveRatePerSecond("abc", null, null));
    }
}
