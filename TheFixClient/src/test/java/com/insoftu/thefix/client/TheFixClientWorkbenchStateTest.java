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
        assertEquals("Default profile", snapshot.getJsonObject("sessionProfiles").getString("activeProfileName"));
        assertTrue(snapshot.getJsonObject("settings").getInteger("profileCount") >= 1);
        state.close();
    }

    @Test
    void settingsAndSessionProfilesSnapshotsArePublishedSeparately() {
        TheFixClientWorkbenchState state = createState();

        JsonObject settingsSnapshot = state.settingsSnapshot();
        JsonObject sessionProfilesSnapshot = state.sessionProfilesSnapshot();

        assertNotNull(settingsSnapshot.getJsonObject("settings"));
        assertEquals(tempDir.toString(), settingsSnapshot.getJsonObject("settings").getString("storagePath"));
        assertNotNull(sessionProfilesSnapshot.getJsonObject("sessionProfiles"));
        assertEquals("Default profile", sessionProfilesSnapshot.getJsonObject("sessionProfiles").getString("activeProfileName"));
        state.close();
    }

    @Test
    void snapshotCanTargetSpecificProfilesAndTrackMultipleRuntimeSessions() {
        TheFixClientWorkbenchState state = createState();

        state.saveSettingsProfile(new JsonObject()
                .put("name", "LDN session")
                .put("senderCompId", "THEFIX_LDN01")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.1")
                .put("fixPort", 9988)
                .put("fixVersionCode", "FIX_44")
                .put("resetOnLogon", true)
                .put("sessionStartTime", "06:00:00")
                .put("sessionEndTime", "16:30:00")
                .put("heartBtIntSec", 30)
                .put("reconnectIntervalSec", 5)
                .put("activate", false));

        state.connect(new JsonObject().put("profileName", "Default profile"));
        state.connect(new JsonObject().put("profileName", "LDN session"));

        JsonObject ldnSnapshot = state.snapshot("LDN session");

        assertEquals("LDN session", ldnSnapshot.getJsonObject("session").getString("profileName"));
        assertEquals(9988, ldnSnapshot.getJsonObject("session").getInteger("port"));
        assertEquals(2, ldnSnapshot.getJsonObject("settings").getInteger("profileCount"));
        assertEquals(2, ldnSnapshot.getJsonArray("runtimeSessions").size());
        assertTrue(ldnSnapshot.getJsonArray("runtimeSessions").stream()
                .map(JsonObject.class::cast)
                .anyMatch(item -> "LDN session".equals(item.getString("profileName"))));
        state.close();
    }

    @Test
    void deletingADisconnectedProfileRemovesItFromThePublishedSnapshot() {
        TheFixClientWorkbenchState state = createState();

        state.saveSettingsProfile(new JsonObject()
                .put("name", "LDN session")
                .put("senderCompId", "THEFIX_LDN01")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.1")
                .put("fixPort", 9988)
                .put("fixVersionCode", "FIX_44")
                .put("activate", false));

        JsonObject response = state.deleteSettingsProfile(new JsonObject().put("name", "LDN session"));

        assertTrue(response.getJsonObject("actionResult").getBoolean("success"));
        assertFalse(response.getJsonObject("sessionProfiles").getJsonArray("profiles").stream()
                .map(JsonObject.class::cast)
                .anyMatch(item -> "LDN session".equals(item.getString("name"))));
        state.close();
    }

    @Test
    void deletingAConnectedProfileIsRejectedUntilTheSessionIsDisconnected() {
        TheFixClientWorkbenchState state = createState();

        state.saveSettingsProfile(new JsonObject()
                .put("name", "LDN session")
                .put("senderCompId", "THEFIX_LDN01")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.1")
                .put("fixPort", 9988)
                .put("fixVersionCode", "FIX_44")
                .put("activate", false));
        state.connect(new JsonObject().put("profileName", "LDN session"));

        JsonObject response = state.deleteSettingsProfile(new JsonObject().put("name", "LDN session"));

        assertFalse(response.getJsonObject("actionResult").getBoolean("success"));
        assertTrue(response.getJsonObject("actionResult").getString("message").contains("Disconnect"));
        state.close();
    }

    @Test
    void resettingSequenceNumbersRequiresALiveSession() {
        TheFixClientWorkbenchState state = createState();

        JsonObject response = state.resetSequenceNumbers(new JsonObject().put("profileName", "Default profile"));

        assertFalse(response.getJsonObject("actionResult").getBoolean("success"));
        assertTrue(response.getJsonObject("actionResult").getString("message").contains("Connect"));
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
        assertFalse(referenceData.getJsonArray("messageTypes").isEmpty());
        assertTrue(referenceData.getJsonArray("fixVersions").encode().contains("FIX_52"));
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

    @Test
    void previewCancelMessagesRequireOrigClOrdId() {
        TheFixClientWorkbenchState state = createState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("messageType", "ORDER_CANCEL_REQUEST")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", "AAPL")
                .put("side", "SELL")
                .put("quantity", 0)
                .put("currency", "USD"));

        assertEquals("INVALID", preview.getString("status"));
        assertTrue(preview.getJsonArray("warnings").encode().contains("OrigClOrdID"));
        state.close();
    }

    @Test
    void previewRejectsDuplicateAdditionalTags() {
        TheFixClientWorkbenchState state = createState();

        JsonObject preview = state.previewOrder(new JsonObject()
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 10)
                .put("orderType", "LIMIT")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("currency", "USD")
                .put("price", 100.10)
                .put("additionalTags", new io.vertx.core.json.JsonArray()
                        .add(new JsonObject().put("tag", 9001).put("name", "ClientAlgo").put("value", "A"))
                        .add(new JsonObject().put("tag", 9001).put("name", "ClientAlgo").put("value", "B"))));

        assertEquals("INVALID", preview.getString("status"));
        assertTrue(preview.getJsonArray("warnings").encode().contains("Duplicate additional FIX tag 9001"));
        state.close();
    }

    @Test
    void manualTemplateSavePersistsTheCurrentDraft() {
        TheFixClientWorkbenchState state = createState();

        JsonObject response = state.saveMessageTemplate(new JsonObject()
                .put("name", "Opening buy template")
                .put("draft", new JsonObject()
                        .put("messageType", "NEW_ORDER_SINGLE")
                        .put("symbol", "AAPL")
                        .put("side", "BUY")
                        .put("quantity", 250)
                        .put("price", 101.25)));

        JsonObject templateSnapshot = response.getJsonObject("templates");
        assertNotNull(templateSnapshot);
        assertEquals(1, templateSnapshot.getJsonArray("items").size());
        JsonObject template = templateSnapshot.getJsonArray("items").getJsonObject(0);
        assertEquals("Opening buy template", template.getString("name"));
        assertEquals("AAPL", template.getJsonObject("draft").getString("symbol"));
        state.close();
    }

    @Test
    void sendOrderDoesNotPersistATemplateWithoutExplicitSave() {
        TheFixClientWorkbenchState state = createState();

        state.sendOrder(new JsonObject()
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", "MSFT")
                .put("side", "BUY")
                .put("quantity", 100)
                .put("orderType", "LIMIT")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("currency", "USD")
                .put("price", 101.75)
                .put("stopPrice", 0.0));

        JsonObject templateSnapshot = state.templateSnapshot().getJsonObject("templates");
        assertNotNull(templateSnapshot);
        assertEquals(0, templateSnapshot.getJsonArray("items").size());
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
        return new TheFixClientWorkbenchState(testConfig(), store, new TheFixMessageTemplateStore(tempDir.resolve("templates-db").resolve("message-templates")));
    }
}
