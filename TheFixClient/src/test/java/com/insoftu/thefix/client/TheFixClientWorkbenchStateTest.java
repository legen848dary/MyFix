package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientWorkbenchStateTest {

    @Test
    void snapshotIncludesConfiguredFixSessionDefaults() {
        TheFixClientWorkbenchState state = new TheFixClientWorkbenchState(testConfig());

        JsonObject snapshot = state.snapshot();
        assertFalse(snapshot.getJsonObject("session").getBoolean("connected"));
        assertEquals("localhost", snapshot.getJsonObject("session").getString("host"));
        assertEquals("HSBC_TRDR01", snapshot.getJsonObject("session").getString("senderCompId"));
        state.close();
    }

    @Test
    void previewOrderReturnsWarningsForInvalidInput() {
        TheFixClientWorkbenchState state = new TheFixClientWorkbenchState(testConfig());

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("symbol", "")
                .put("side", "BUY")
                .put("quantity", 0)
                .put("price", 0.0));

        assertEquals("INVALID", preview.getString("status"));
        JsonArray warnings = preview.getJsonArray("warnings");
        assertTrue(warnings.size() >= 2);
        state.close();
    }

    private static TheFixClientConfig testConfig() {
        return new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "localhost",
                9880,
                "FIX.4.4",
                "HSBC_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                "build/test-quickfixj",
                false
        );
    }
}

