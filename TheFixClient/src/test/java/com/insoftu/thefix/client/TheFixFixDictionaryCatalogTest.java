package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixFixDictionaryCatalogTest {

    private final TheFixFixDictionaryCatalog catalog = new TheFixFixDictionaryCatalog();

    // -----------------------------------------------------------------------
    // Snapshot structure
    // -----------------------------------------------------------------------

    @Test
    void snapshotHasVersionsAndMessageTypesArrays() {
        JsonObject snapshot = catalog.snapshot();

        assertNotNull(snapshot.getJsonArray("versions"));
        assertNotNull(snapshot.getJsonArray("messageTypes"));
    }

    @Test
    void snapshotHasFourVersionEntries() {
        JsonArray versions = catalog.snapshot().getJsonArray("versions");

        assertEquals(4, versions.size());
    }

    @Test
    void snapshotVersionCodesMatchExpectedFourVersions() {
        JsonArray versions = catalog.snapshot().getJsonArray("versions");

        boolean hasFix42 = false, hasFix44 = false, hasFix50 = false, hasFix52 = false;
        for (int i = 0; i < versions.size(); i++) {
            String code = versions.getJsonObject(i).getString("code");
            if ("FIX_42".equals(code)) hasFix42 = true;
            if ("FIX_44".equals(code)) hasFix44 = true;
            if ("FIX_50".equals(code)) hasFix50 = true;
            if ("FIX_52".equals(code)) hasFix52 = true;
        }
        assertTrue(hasFix42, "Expected FIX_42 in versions");
        assertTrue(hasFix44, "Expected FIX_44 in versions");
        assertTrue(hasFix50, "Expected FIX_50 in versions");
        assertTrue(hasFix52, "Expected FIX_52 in versions");
    }

    @Test
    void snapshotHasThreeMessageTypeEntries() {
        JsonArray messageTypes = catalog.snapshot().getJsonArray("messageTypes");

        assertEquals(3, messageTypes.size());
    }

    // -----------------------------------------------------------------------
    // FIX 4.4 version content (dictionary file is present on classpath)
    // -----------------------------------------------------------------------

    @Test
    void fix44VersionHasTagsArray() {
        JsonObject fix44 = versionByCode("FIX_44");

        assertNotNull(fix44);
        JsonArray tags = fix44.getJsonArray("tags");
        assertNotNull(tags);
        assertFalse(tags.isEmpty(), "FIX 4.4 dictionary should have at least one tag");
    }

    @Test
    void fix44VersionHasMessagesForSupportedMessageTypes() {
        JsonObject fix44 = versionByCode("FIX_44");
        assertNotNull(fix44);

        JsonObject messages = fix44.getJsonObject("messages");
        assertNotNull(messages);
        // NEW_ORDER_SINGLE maps to "D" in FIX 4.4
        assertNotNull(messages.getJsonArray("NEW_ORDER_SINGLE"),
                "Expected NEW_ORDER_SINGLE fields in FIX 4.4 messages");
        assertFalse(messages.getJsonArray("NEW_ORDER_SINGLE").isEmpty(),
                "FIX 4.4 NewOrderSingle should have at least one required field");
    }

    @Test
    void fix44VersionTagsHaveTagNumberNameAndType() {
        JsonObject fix44 = versionByCode("FIX_44");
        assertNotNull(fix44);
        JsonArray tags = fix44.getJsonArray("tags");
        assertFalse(tags.isEmpty());

        JsonObject firstTag = tags.getJsonObject(0);
        assertTrue(firstTag.getInteger("tag", -1) > 0, "tag number must be positive");
        assertFalse(firstTag.getString("name", "").isBlank(), "tag name must not be blank");
        assertFalse(firstTag.getString("type", "").isBlank(), "tag type must not be blank");
    }

    @Test
    void fix44VersionIncludesBeginStringAndDefaultApplVerId() {
        JsonObject fix44 = versionByCode("FIX_44");
        assertNotNull(fix44);

        assertEquals("FIX.4.4", fix44.getString("beginString"));
        assertNotNull(fix44.getString("defaultApplVerId"));
    }

    // -----------------------------------------------------------------------
    // All versions have required structural fields
    // -----------------------------------------------------------------------

    @Test
    void allVersionsHaveCodeLabelTagsAndMessagesFields() {
        JsonArray versions = catalog.snapshot().getJsonArray("versions");

        for (int i = 0; i < versions.size(); i++) {
            JsonObject version = versions.getJsonObject(i);
            String code = version.getString("code");
            assertFalse(code == null || code.isBlank(), "version code must not be blank at index " + i);
            assertFalse(version.getString("label", "").isBlank(), "label missing for " + code);
            assertNotNull(version.getJsonArray("tags"), "tags missing for " + code);
            assertNotNull(version.getJsonObject("messages"), "messages missing for " + code);
        }
    }

    @Test
    void snapshotReturnsCopyEachTime() {
        JsonObject snapshotA = catalog.snapshot();
        JsonObject snapshotB = catalog.snapshot();

        // Mutate one copy — the other must be unaffected
        snapshotA.put("injected", "poison");
        assertFalse(snapshotB.containsKey("injected"),
                "snapshot() should return independent copies");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private JsonObject versionByCode(String code) {
        JsonArray versions = catalog.snapshot().getJsonArray("versions");
        for (int i = 0; i < versions.size(); i++) {
            JsonObject v = versions.getJsonObject(i);
            if (code.equals(v.getString("code"))) {
                return v;
            }
        }
        return null;
    }
}
