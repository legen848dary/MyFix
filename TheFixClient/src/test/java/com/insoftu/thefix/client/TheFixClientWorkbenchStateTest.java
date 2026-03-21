package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientWorkbenchStateTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotIncludesConfiguredFixSessionDefaults() {
        TheFixClientWorkbenchState state = createState();

        JsonObject snapshot = state.snapshot();
        assertFalse(snapshot.getJsonObject("session").getBoolean("connected"));
        assertEquals("localhost", snapshot.getJsonObject("session").getString("host"));
        assertEquals("THEFIX_TRDR01", snapshot.getJsonObject("session").getString("senderCompId"));
        assertEquals("Default profile", snapshot.getJsonObject("settings").getString("activeProfileName"));
        state.close();
    }

    @Test
    void previewOrderReturnsWarningsForInvalidInput() {
        TheFixClientWorkbenchState state = createState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("symbol", "")
                .put("side", "BUY")
                .put("quantity", 0)
                .put("price", 0.0));

        assertEquals("INVALID", preview.getString("status"));
        String warnings = preview.getJsonArray("warnings").encode();
        assertTrue(warnings.contains("Symbol is required."));
        assertTrue(warnings.contains("Quantity must be greater than zero."));
        assertTrue(warnings.contains("Limit price must be greater than zero."));
        state.close();
    }

    @Test
    void snapshotIncludesRegionalReferenceDataForTheRedesignedWorkstation() {
        TheFixClientWorkbenchState state = createState();

        JsonObject snapshot = state.snapshot();
        JsonObject referenceData = snapshot.getJsonObject("referenceData");

        assertNotNull(referenceData);
        assertFalse(referenceData.getJsonArray("regions").isEmpty());
        assertFalse(referenceData.getJsonArray("markets").isEmpty());
        assertFalse(referenceData.getJsonArray("orderTypes").isEmpty());
        assertFalse(referenceData.getJsonArray("priceTypes").isEmpty());
        state.close();
    }

    @Test
    void previewSupportsMarketOrdersWithoutRequiringALimitPrice() {
        TheFixClientWorkbenchState state = createState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("region", "EMEA")
                .put("market", "XLON")
                .put("symbol", "BP.L")
                .put("side", "BUY")
                .put("quantity", 50)
                .put("orderType", "MARKET")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("currency", "GBP")
                .put("price", 0.0));

        assertEquals("READY_PENDING_CONNECTION", preview.getString("status"));
        assertTrue(preview.getJsonArray("warnings").isEmpty());
        assertTrue(preview.getString("routeSummary").contains("GBP"));
        state.close();
    }

    @Test
    void previewStopOrdersRequireAStopPrice() {
        TheFixClientWorkbenchState state = createState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", "AAPL")
                .put("side", "SELL")
                .put("quantity", 20)
                .put("orderType", "STOP_LIMIT")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("currency", "USD")
                .put("price", 195.25)
                .put("stopPrice", 0.0));

        assertEquals("INVALID", preview.getString("status"));
        assertTrue(preview.getJsonArray("warnings").encode().contains("Stop price"));
        state.close();
    }

    private static TheFixClientConfig testConfig() {
        return new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "localhost",
                9880,
                "FIX.4.4",
                "THEFIX_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                "build/test-quickfixj",
                false
        );
    }

    private TheFixClientWorkbenchState createState() {
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(testConfig());
        store.updateStoragePath(tempDir.toString());
        return new TheFixClientWorkbenchState(testConfig(), store);
    }
}

