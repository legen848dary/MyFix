package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixOrderStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReturnsEmptyArrayWhenNoSnapshotExists() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);

        JsonArray result = store.load("trader1", "Default profile");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        store.close();
    }

    @Test
    void persistAndLoadRoundTrip() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);
        JsonArray orders = new JsonArray()
                .add(new JsonObject().put("clOrdId", "ORD-001").put("symbol", "AAPL").put("status", "Sent"))
                .add(new JsonObject().put("clOrdId", "ORD-002").put("symbol", "MSFT").put("status", "Filled"));

        store.persist("trader1", "Default profile", orders);
        JsonArray loaded = store.load("trader1", "Default profile");

        assertEquals(2, loaded.size());
        assertEquals("ORD-001", loaded.getJsonObject(0).getString("clOrdId"));
        assertEquals("ORD-002", loaded.getJsonObject(1).getString("clOrdId"));
        store.close();
    }

    @Test
    void persistUpsertOverwritesPreviousSnapshot() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);
        store.persist("trader1", "Default profile", new JsonArray()
                .add(new JsonObject().put("clOrdId", "ORD-001").put("status", "Sent")));
        store.persist("trader1", "Default profile", new JsonArray()
                .add(new JsonObject().put("clOrdId", "ORD-002").put("status", "Filled")));

        JsonArray loaded = store.load("trader1", "Default profile");

        assertEquals(1, loaded.size());
        assertEquals("ORD-002", loaded.getJsonObject(0).getString("clOrdId"));
        store.close();
    }

    @Test
    void snapshotsAreIsolatedByUsername() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);
        store.persist("trader1", "Default profile", new JsonArray()
                .add(new JsonObject().put("clOrdId", "T1-001")));
        store.persist("trader2", "Default profile", new JsonArray()
                .add(new JsonObject().put("clOrdId", "T2-001")));

        JsonArray t1 = store.load("trader1", "Default profile");
        JsonArray t2 = store.load("trader2", "Default profile");

        assertEquals("T1-001", t1.getJsonObject(0).getString("clOrdId"));
        assertEquals("T2-001", t2.getJsonObject(0).getString("clOrdId"));
        store.close();
    }

    @Test
    void snapshotsAreIsolatedByProfileName() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);
        store.persist("trader1", "London", new JsonArray()
                .add(new JsonObject().put("clOrdId", "LDN-001")));
        store.persist("trader1", "New York", new JsonArray()
                .add(new JsonObject().put("clOrdId", "NY-001")));

        assertEquals("LDN-001", store.load("trader1", "London").getJsonObject(0).getString("clOrdId"));
        assertEquals("NY-001", store.load("trader1", "New York").getJsonObject(0).getString("clOrdId"));
        store.close();
    }

    @Test
    void blankUsernameOrProfileIsIgnoredOnPersist() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);
        JsonArray orders = new JsonArray().add(new JsonObject().put("clOrdId", "X-001"));

        // These should not throw, just silently no-op.
        store.persist(null, "Default profile", orders);
        store.persist("", "Default profile", orders);
        store.persist("trader1", null, orders);
        store.persist("trader1", "", orders);

        assertTrue(store.load("trader1", "Default profile").isEmpty());
        store.close();
    }

    @Test
    void loadDoesNotThrowForNullArguments() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);

        JsonArray r1 = store.load(null, "Default profile");
        JsonArray r2 = store.load("trader1", null);

        assertFalse(r1 == null);
        assertFalse(r2 == null);
        store.close();
    }

    @Test
    void persistingNullOrdersStoresEmptyArray() {
        TheFixOrderStore store = new TheFixOrderStore(tempDir.resolve("orders").resolve("test-orders"), 1);

        store.persist("trader1", "Default profile", null);
        JsonArray loaded = store.load("trader1", "Default profile");

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
        store.close();
    }
}
