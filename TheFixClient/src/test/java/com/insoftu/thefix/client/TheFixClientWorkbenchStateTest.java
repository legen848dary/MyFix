package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientWorkbenchStateTest {

    @Test
    void connectAndDisconnectUpdateSessionState() {
        TheFixClientWorkbenchState state = new TheFixClientWorkbenchState();

        JsonObject connected = state.connect();
        assertTrue(connected.getJsonObject("session").getBoolean("connected"));
        assertEquals("Desk primed", connected.getJsonObject("kpis").getString("readyState"));

        JsonObject disconnected = state.disconnect();
        assertFalse(disconnected.getJsonObject("session").getBoolean("connected"));
        assertEquals("UI ready", disconnected.getJsonObject("kpis").getString("readyState"));
    }

    @Test
    void previewOrderReturnsWarningsForInvalidInput() {
        TheFixClientWorkbenchState state = new TheFixClientWorkbenchState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("symbol", "")
                .put("side", "BUY")
                .put("quantity", 0)
                .put("price", 0.0));

        assertEquals("INVALID", preview.getString("status"));
        JsonArray warnings = preview.getJsonArray("warnings");
        assertTrue(warnings.size() >= 2);
    }
}

