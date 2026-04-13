package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixSessionProfileTest {

    private static TheFixClientConfig testConfig() {
        return new TheFixClientConfig(
                "0.0.0.0", 8081, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                "build/test-quickfixj", false);
    }

    // -----------------------------------------------------------------------
    // defaultProfile
    // -----------------------------------------------------------------------

    @Test
    void defaultProfilePopulatesFieldsFromConfig() {
        TheFixSessionProfile profile = TheFixSessionProfile.defaultProfile(testConfig());

        assertEquals(TheFixSessionProfile.DEFAULT_PROFILE_NAME, profile.name());
        assertEquals("THEFIX_TRDR01", profile.senderCompId());
        assertEquals("LLEXSIM", profile.targetCompId());
        assertEquals("localhost", profile.fixHost());
        assertEquals(9880, profile.fixPort());
        assertEquals(30, profile.heartBtIntSec());
        assertEquals(5, profile.reconnectIntervalSec());
        assertEquals("build/test-quickfixj", profile.quickFixLogDir());
        assertFalse(profile.rawMessageLoggingEnabled());
    }

    // -----------------------------------------------------------------------
    // fromJson — happy path
    // -----------------------------------------------------------------------

    @Test
    void fromJsonOverridesDefaultsFromConfig() {
        JsonObject json = new JsonObject()
                .put("name", "NYC session")
                .put("senderCompId", "TRADER01")
                .put("targetCompId", "SIMEX")
                .put("fixHost", "192.168.1.10")
                .put("fixPort", 7070)
                .put("fixVersionCode", "FIX_42")
                .put("resetOnLogon", false)
                .put("sessionStartTime", "07:00:00")
                .put("sessionEndTime", "17:00:00")
                .put("heartBtIntSec", 15)
                .put("reconnectIntervalSec", 10)
                .put("quickFixLogDir", "/tmp/fix")
                .put("rawMessageLoggingEnabled", true);

        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(json, testConfig());

        assertEquals("NYC session", profile.name());
        assertEquals("TRADER01", profile.senderCompId());
        assertEquals("SIMEX", profile.targetCompId());
        assertEquals("192.168.1.10", profile.fixHost());
        assertEquals(7070, profile.fixPort());
        assertEquals("FIX_42", profile.fixVersionCode());
        assertFalse(profile.resetOnLogon());
        assertEquals("07:00:00", profile.sessionStartTime());
        assertEquals("17:00:00", profile.sessionEndTime());
        assertEquals(15, profile.heartBtIntSec());
        assertEquals(10, profile.reconnectIntervalSec());
        assertEquals("/tmp/fix", profile.quickFixLogDir());
        assertTrue(profile.rawMessageLoggingEnabled());
    }

    // -----------------------------------------------------------------------
    // fromJson — sanitization / fallback
    // -----------------------------------------------------------------------

    @Test
    void fromJsonNullJsonFallsBackToConfigDefaults() {
        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(null, testConfig());

        assertEquals(TheFixSessionProfile.DEFAULT_PROFILE_NAME, profile.name());
        assertEquals("THEFIX_TRDR01", profile.senderCompId());
        assertEquals("localhost", profile.fixHost());
        assertEquals(9880, profile.fixPort());
    }

    @Test
    void fromJsonBlankNameFallsBackToDefaultProfileName() {
        JsonObject json = new JsonObject().put("name", "");

        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(json, testConfig());

        assertEquals(TheFixSessionProfile.DEFAULT_PROFILE_NAME, profile.name());
    }

    @Test
    void fromJsonPortOutOfRangeFallsBackToConfigPort() {
        JsonObject jsonAbove = new JsonObject().put("name", "test").put("fixPort", 70000);
        JsonObject jsonBelow = new JsonObject().put("name", "test").put("fixPort", 0);

        assertEquals(9880, TheFixSessionProfile.fromJson(jsonAbove, testConfig()).fixPort());
        assertEquals(9880, TheFixSessionProfile.fromJson(jsonBelow, testConfig()).fixPort());
    }

    @Test
    void fromJsonPortAsStringIsParsedCorrectly() {
        JsonObject json = new JsonObject().put("name", "test").put("fixPort", "8888");

        assertEquals(8888, TheFixSessionProfile.fromJson(json, testConfig()).fixPort());
    }

    @Test
    void fromJsonInvalidSessionTimeFallsBackToDefault() {
        JsonObject json = new JsonObject()
                .put("name", "test")
                .put("sessionStartTime", "not-a-time")
                .put("sessionEndTime", "25:99:00");

        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(json, testConfig());

        // default start/end from defaultProfile is "00:00:00"
        assertEquals("00:00:00", profile.sessionStartTime());
        assertEquals("00:00:00", profile.sessionEndTime());
    }

    @Test
    void fromJsonValidSessionTimeIsPreserved() {
        JsonObject json = new JsonObject()
                .put("name", "test")
                .put("sessionStartTime", "08:00:00")
                .put("sessionEndTime", "20:30:00");

        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(json, testConfig());

        assertEquals("08:00:00", profile.sessionStartTime());
        assertEquals("20:30:00", profile.sessionEndTime());
    }

    @Test
    void fromJsonUnknownFixVersionCodeDefaultsToFix44() {
        JsonObject json = new JsonObject().put("name", "test").put("fixVersionCode", "FIX_99");

        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(json, testConfig());

        assertEquals("FIX_44", profile.fixVersionCode());
    }

    // -----------------------------------------------------------------------
    // Derived accessors
    // -----------------------------------------------------------------------

    @Test
    void beginStringAndDefaultApplVerIdAreDerivedFromVersion() {
        TheFixSessionProfile fix44 = TheFixSessionProfile.fromJson(
                new JsonObject().put("name", "t").put("fixVersionCode", "FIX_44"), testConfig());

        assertEquals("FIX.4.4", fix44.beginString());
        assertEquals("FIX.4.4", fix44.defaultApplVerId());
    }

    @Test
    void storeDirAndRawLogDirAreSubpathsOfQuickFixLogDir() {
        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(
                new JsonObject().put("name", "t").put("quickFixLogDir", "/logs/fix"), testConfig());

        assertTrue(profile.storeDir().toString().contains("/logs/fix"));
        assertTrue(profile.rawLogDir().toString().contains("/logs/fix"));
        assertTrue(profile.storeDir().toString().endsWith("store"));
        assertTrue(profile.rawLogDir().toString().endsWith("messages"));
    }

    // -----------------------------------------------------------------------
    // toJson round-trip
    // -----------------------------------------------------------------------

    @Test
    void toJsonRoundTripsAllFields() {
        JsonObject source = new JsonObject()
                .put("name", "EU session")
                .put("senderCompId", "S1")
                .put("targetCompId", "T1")
                .put("fixHost", "10.0.0.1")
                .put("fixPort", 6060)
                .put("fixVersionCode", "FIX_52")
                .put("resetOnLogon", false)
                .put("sessionStartTime", "09:00:00")
                .put("sessionEndTime", "18:00:00")
                .put("heartBtIntSec", 60)
                .put("reconnectIntervalSec", 15)
                .put("quickFixLogDir", "/logs")
                .put("rawMessageLoggingEnabled", true);

        JsonObject json = TheFixSessionProfile.fromJson(source, testConfig()).toJson();

        assertEquals("EU session", json.getString("name"));
        assertEquals("S1", json.getString("senderCompId"));
        assertEquals("T1", json.getString("targetCompId"));
        assertEquals("10.0.0.1", json.getString("fixHost"));
        assertEquals(6060, json.getInteger("fixPort"));
        assertEquals("FIX_52", json.getString("fixVersionCode"));
        assertFalse(json.getBoolean("resetOnLogon"));
        assertEquals("09:00:00", json.getString("sessionStartTime"));
        assertEquals("18:00:00", json.getString("sessionEndTime"));
        assertEquals(60, json.getInteger("heartBtIntSec"));
        assertEquals(15, json.getInteger("reconnectIntervalSec"));
        assertEquals("/logs", json.getString("quickFixLogDir"));
        assertTrue(json.getBoolean("rawMessageLoggingEnabled"));
        // derived fields
        assertNotNull(json.getString("beginString"));
        assertNotNull(json.getString("defaultApplVerId"));
        assertNotNull(json.getString("fixVersionLabel"));
    }
}
