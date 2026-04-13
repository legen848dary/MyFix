package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TheFixFixVersionTest {

    // -----------------------------------------------------------------------
    // fromCode
    // -----------------------------------------------------------------------

    @Test
    void fromCodeNullOrBlankDefaultsToFix44() {
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode(null));
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode(""));
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode("  "));
    }

    @Test
    void fromCodeUnrecognizedDefaultsToFix44() {
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode("FIX_99"));
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode("UNKNOWN"));
    }

    @Test
    void fromCodeRecognisesAllVersions() {
        assertEquals(TheFixFixVersion.FIX_42, TheFixFixVersion.fromCode("FIX_42"));
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode("FIX_44"));
        assertEquals(TheFixFixVersion.FIX_50, TheFixFixVersion.fromCode("FIX_50"));
        assertEquals(TheFixFixVersion.FIX_52, TheFixFixVersion.fromCode("FIX_52"));
    }

    @Test
    void fromCodeIsCaseInsensitive() {
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromCode("fix_44"));
        assertEquals(TheFixFixVersion.FIX_42, TheFixFixVersion.fromCode("fix_42"));
    }

    @Test
    void fromCodeAcceptsFix50Sp2Alias() {
        assertEquals(TheFixFixVersion.FIX_52, TheFixFixVersion.fromCode("FIX_50_SP2"));
    }

    // -----------------------------------------------------------------------
    // fromBeginString
    // -----------------------------------------------------------------------

    @Test
    void fromBeginStringMapsFix42And44() {
        assertEquals(TheFixFixVersion.FIX_42, TheFixFixVersion.fromBeginString("FIX.4.2", null));
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromBeginString("FIX.4.4", null));
    }

    @Test
    void fromBeginStringMapsFixt11WithoutSp2ToFix50() {
        assertEquals(TheFixFixVersion.FIX_50, TheFixFixVersion.fromBeginString("FIXT.1.1", null));
        assertEquals(TheFixFixVersion.FIX_50, TheFixFixVersion.fromBeginString("FIXT.1.1", "FIX.5.0"));
    }

    @Test
    void fromBeginStringMapsFixt11WithSp2ToFix52() {
        assertEquals(TheFixFixVersion.FIX_52, TheFixFixVersion.fromBeginString("FIXT.1.1", "FIX.5.0SP2"));
    }

    @Test
    void fromBeginStringMapsFix50() {
        assertEquals(TheFixFixVersion.FIX_50, TheFixFixVersion.fromBeginString("FIX.5.0", null));
    }

    @Test
    void fromBeginStringDefaultsToFix44ForUnrecognizedBeginString() {
        assertEquals(TheFixFixVersion.FIX_44, TheFixFixVersion.fromBeginString("UNKNOWN", null));
    }

    @Test
    void fromBeginStringUsesDefaultApplVerIdAsTieBreaker() {
        assertEquals(TheFixFixVersion.FIX_52, TheFixFixVersion.fromBeginString("UNKNOWN", "5.0SP2"));
        assertEquals(TheFixFixVersion.FIX_50, TheFixFixVersion.fromBeginString("UNKNOWN", "5.0"));
    }

    // -----------------------------------------------------------------------
    // toJson / options / accessors
    // -----------------------------------------------------------------------

    @Test
    void toJsonIncludesAllFields() {
        JsonObject json = TheFixFixVersion.FIX_44.toJson();

        assertEquals("FIX_44", json.getString("code"));
        assertEquals("FIX 4.4", json.getString("label"));
        assertEquals("FIX.4.4", json.getString("beginString"));
        assertNotNull(json.getString("defaultApplVerId"));
        assertNotNull(json.getString("dictionaryResource"));
    }

    @Test
    void optionsReturnsAllFourVersions() {
        List<TheFixFixVersion> options = TheFixFixVersion.options();

        assertEquals(4, options.size());
        assertFalse(options.isEmpty());
    }

    @Test
    void accessorsReturnCorrectValuesForFix52() {
        TheFixFixVersion v = TheFixFixVersion.FIX_52;

        assertEquals("FIX_52", v.code());
        assertEquals("FIXT.1.1", v.beginString());
        assertEquals("FIX50SP2.xml", v.dictionaryResource());
        assertNotNull(v.label());
        assertNotNull(v.defaultApplVerId());
    }
}
