/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixTagEntryTest {

    // -----------------------------------------------------------------------
    // fromJsonArray
    // -----------------------------------------------------------------------

    @Test
    void fromJsonArrayNullReturnsEmptyList() {
        assertTrue(TheFixTagEntry.fromJsonArray(null).isEmpty());
    }

    @Test
    void fromJsonArrayEmptyReturnsEmptyList() {
        assertTrue(TheFixTagEntry.fromJsonArray(new JsonArray()).isEmpty());
    }

    @Test
    void fromJsonArrayNullItemsAreSkipped() {
        JsonArray array = new JsonArray().addNull();
        assertTrue(TheFixTagEntry.fromJsonArray(array).isEmpty());
    }

    @Test
    void fromJsonArrayParsesNumericTagDirectly() {
        JsonArray array = new JsonArray()
                .add(new JsonObject().put("tag", 9001).put("name", "Strategy").put("value", "VWAP").put("custom", true));

        List<TheFixTagEntry> entries = TheFixTagEntry.fromJsonArray(array);

        assertEquals(1, entries.size());
        assertEquals(9001, entries.getFirst().tag());
        assertEquals("Strategy", entries.getFirst().name());
        assertEquals("VWAP", entries.getFirst().value());
        assertTrue(entries.getFirst().custom());
    }

    @Test
    void fromJsonArrayParsesStringTag() {
        JsonArray array = new JsonArray()
                .add(new JsonObject().put("tag", "58").put("name", "Text").put("value", "Hello"));

        List<TheFixTagEntry> entries = TheFixTagEntry.fromJsonArray(array);

        assertEquals(1, entries.size());
        assertEquals(58, entries.getFirst().tag());
    }

    @Test
    void fromJsonArrayDefaultsBlankStringTagToZero() {
        JsonArray array = new JsonArray()
                .add(new JsonObject().put("tag", "").put("name", "Bad").put("value", "x"));

        List<TheFixTagEntry> entries = TheFixTagEntry.fromJsonArray(array);

        assertEquals(0, entries.getFirst().tag());
    }

    @Test
    void fromJsonArrayDefaultsNonNumericStringTagToZero() {
        JsonArray array = new JsonArray()
                .add(new JsonObject().put("tag", "notanumber").put("name", "Bad"));

        List<TheFixTagEntry> entries = TheFixTagEntry.fromJsonArray(array);

        assertEquals(0, entries.getFirst().tag());
    }

    @Test
    void fromJsonArrayDefaultsMissingNameAndValueToEmptyStrings() {
        JsonArray array = new JsonArray().add(new JsonObject().put("tag", 100));

        TheFixTagEntry entry = TheFixTagEntry.fromJsonArray(array).getFirst();

        assertEquals("", entry.name());
        assertEquals("", entry.value());
        assertFalse(entry.custom());
    }

    @Test
    void fromJsonArrayReturnImmutableList() {
        JsonArray array = new JsonArray().add(new JsonObject().put("tag", 1).put("value", "v"));
        List<TheFixTagEntry> list = TheFixTagEntry.fromJsonArray(array);
        try {
            list.add(new TheFixTagEntry(2, "", "", false));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected immutable list to throw on add");
    }

    // -----------------------------------------------------------------------
    // hasValue
    // -----------------------------------------------------------------------

    @Test
    void hasValueReturnsTrueForNonBlankValue() {
        assertTrue(new TheFixTagEntry(1, "X", "hello", false).hasValue());
    }

    @Test
    void hasValueReturnsFalseForBlankValue() {
        assertFalse(new TheFixTagEntry(1, "X", "", false).hasValue());
        assertFalse(new TheFixTagEntry(1, "X", "   ", false).hasValue());
    }

    @Test
    void hasValueReturnsFalseForNullValue() {
        assertFalse(new TheFixTagEntry(1, "X", null, false).hasValue());
    }

    // -----------------------------------------------------------------------
    // toJson
    // -----------------------------------------------------------------------

    @Test
    void toJsonRoundTrips() {
        TheFixTagEntry entry = new TheFixTagEntry(9001, "Algo", "TWAP", true);
        JsonObject json = entry.toJson();

        assertEquals(9001, json.getInteger("tag"));
        assertEquals("Algo", json.getString("name"));
        assertEquals("TWAP", json.getString("value"));
        assertTrue(json.getBoolean("custom"));
    }
}
