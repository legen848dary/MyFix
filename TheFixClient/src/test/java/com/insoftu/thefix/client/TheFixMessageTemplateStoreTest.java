package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixMessageTemplateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReloadsTemplatesPerProfile() {
        Path databasePath = tempDir.resolve("template-store").resolve("templates-db");
        TheFixMessageTemplateStore store = new TheFixMessageTemplateStore(databasePath);

        store.saveManualTemplate("Default profile", "Growth buy", new JsonObject()
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 150));
        store.saveManualTemplate("Alt profile", "Energy sell", new JsonObject()
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("symbol", "XOM")
                .put("side", "SELL")
                .put("quantity", 25));
        store.close();

        TheFixMessageTemplateStore reloaded = new TheFixMessageTemplateStore(databasePath);
        JsonObject defaultSnapshot = reloaded.snapshot("Default profile");
        JsonObject alternateSnapshot = reloaded.snapshot("Alt profile");

        assertEquals(1, defaultSnapshot.getJsonArray("items").size());
        assertEquals("Growth buy", defaultSnapshot.getJsonArray("items").getJsonObject(0).getString("name"));
        assertEquals(1, alternateSnapshot.getJsonArray("items").size());
        assertEquals("Energy sell", alternateSnapshot.getJsonArray("items").getJsonObject(0).getString("name"));
        reloaded.close();
    }

    @Test
    void autoSaveCreatesTimestampedTemplateEntries() {
        TheFixMessageTemplateStore store = new TheFixMessageTemplateStore(tempDir.resolve("auto-store").resolve("templates-db"));

        store.autoSaveTemplate("Default profile", new JsonObject()
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("symbol", "MSFT")
                .put("side", "BUY")
                .put("quantity", 100));

        JsonObject snapshot = store.snapshot("Default profile");
        assertEquals(1, snapshot.getJsonArray("items").size());
        JsonObject template = snapshot.getJsonArray("items").getJsonObject(0);
        assertTrue(template.getString("name").startsWith("Auto · "));
        assertTrue(template.getBoolean("autoSaved"));
        assertEquals("MSFT", template.getJsonObject("draft").getString("symbol"));
        assertEquals(0, template.getJsonObject("draft").getJsonArray("additionalTags").size());
        store.close();
    }
}

