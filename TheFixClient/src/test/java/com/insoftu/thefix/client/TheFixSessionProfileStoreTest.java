package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixSessionProfileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storeSeedsDefaultProfileAndPublishesVersionOptions() {
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(testConfig());
        store.updateStoragePath(tempDir.toString());

        JsonObject snapshot = store.snapshot();

        assertEquals("Default profile", snapshot.getString("activeProfileName"));
        assertFalse(snapshot.getJsonArray("profiles").isEmpty());
        assertTrue(snapshot.getJsonArray("fixVersionOptions").size() >= 3);
    }

    @Test
    void storePersistsProfilesAcrossReloadsAtCustomPath() {
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(testConfig());
        store.updateStoragePath(tempDir.toString());
        store.saveProfile(new JsonObject()
                .put("name", "LDN session")
                .put("senderCompId", "THEFIX_LDN01")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.1")
                .put("fixPort", 9880)
                .put("fixVersionCode", "FIX_44")
                .put("resetOnLogon", true)
                .put("sessionStartTime", "06:00:00")
                .put("sessionEndTime", "16:30:00")
                .put("heartBtIntSec", 30)
                .put("reconnectIntervalSec", 5)
                .put("activate", true));

        TheFixSessionProfileStore reloaded = new TheFixSessionProfileStore(testConfig());
        reloaded.updateStoragePath(tempDir.toString());

        JsonObject snapshot = reloaded.snapshot();
        assertEquals("LDN session", snapshot.getString("activeProfileName"));
        assertTrue(snapshot.getJsonArray("profiles").stream()
                .map(JsonObject.class::cast)
                .anyMatch(profile -> "LDN session".equals(profile.getString("name"))));
    }

    @Test
    void storeCanRenameAndDeleteProfilesWhileKeepingOneAvailable() {
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(testConfig());
        store.updateStoragePath(tempDir.toString());
        store.saveProfile(new JsonObject()
                .put("name", "LDN session")
                .put("senderCompId", "THEFIX_LDN01")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.1")
                .put("fixPort", 9880)
                .put("fixVersionCode", "FIX_44")
                .put("activate", false));

        store.saveProfile(new JsonObject()
                .put("originalName", "LDN session")
                .put("name", "LDN session 2")
                .put("senderCompId", "THEFIX_LDN02")
                .put("targetCompId", "LLEXSIM")
                .put("fixHost", "127.0.0.2")
                .put("fixPort", 9988)
                .put("fixVersionCode", "FIX_44")
                .put("activate", false));

        JsonObject renamedSnapshot = store.snapshot();
        assertFalse(renamedSnapshot.getJsonArray("profiles").stream()
                .map(JsonObject.class::cast)
                .anyMatch(profile -> "LDN session".equals(profile.getString("name"))));
        assertTrue(renamedSnapshot.getJsonArray("profiles").stream()
                .map(JsonObject.class::cast)
                .anyMatch(profile -> "LDN session 2".equals(profile.getString("name"))));

        assertTrue(store.deleteProfile("LDN session 2"));
        assertFalse(store.deleteProfile("Default profile"));
        assertEquals(1, store.snapshot().getJsonArray("profiles").size());
    }

    @Test
    void defaultStorageIsIsolatedPerConfiguredQuickFixDirectory() {
        TheFixClientConfig firstConfig = new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "127.0.0.1",
                9880,
                "FIX.4.4",
                "THEFIX_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                tempDir.resolve("client-a-quickfixj").toString(),
                false
        );
        TheFixClientConfig secondConfig = new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "127.0.0.1",
                9881,
                "FIX.4.4",
                "THEFIX_TRDR02",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                tempDir.resolve("client-b-quickfixj").toString(),
                false
        );

        TheFixSessionProfileStore firstStore = new TheFixSessionProfileStore(firstConfig);
        TheFixSessionProfileStore secondStore = new TheFixSessionProfileStore(secondConfig);

        JsonObject firstSnapshot = firstStore.snapshot();
        JsonObject secondSnapshot = secondStore.snapshot();

        assertEquals(firstConfig.fixPort(), firstSnapshot.getJsonObject("profileDraft").getInteger("fixPort"));
        assertEquals(secondConfig.fixPort(), secondSnapshot.getJsonObject("profileDraft").getInteger("fixPort"));
        assertFalse(firstSnapshot.getString("storagePath").equals(secondSnapshot.getString("storagePath")));
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
                "build/test-profile-store-quickfixj",
                false
        );
    }
}

