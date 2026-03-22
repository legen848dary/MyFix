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

