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

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixMessageTypeTest {

    // -----------------------------------------------------------------------
    // fromCode
    // -----------------------------------------------------------------------

    @Test
    void fromCodeNullOrBlankDefaultsToNewOrderSingle() {
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, TheFixMessageType.fromCode(null));
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, TheFixMessageType.fromCode(""));
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, TheFixMessageType.fromCode("  "));
    }

    @Test
    void fromCodeUnrecognizedDefaultsToNewOrderSingle() {
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, TheFixMessageType.fromCode("UNKNOWN"));
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, TheFixMessageType.fromCode("MARKET_ORDER"));
    }

    @Test
    void fromCodeRecognisesAllThreeTypes() {
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE,
                TheFixMessageType.fromCode("NEW_ORDER_SINGLE"));
        assertEquals(TheFixMessageType.ORDER_CANCEL_REPLACE_REQUEST,
                TheFixMessageType.fromCode("ORDER_CANCEL_REPLACE_REQUEST"));
        assertEquals(TheFixMessageType.ORDER_CANCEL_REQUEST,
                TheFixMessageType.fromCode("ORDER_CANCEL_REQUEST"));
    }

    @Test
    void fromCodeIsCaseInsensitive() {
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE,
                TheFixMessageType.fromCode("new_order_single"));
        assertEquals(TheFixMessageType.ORDER_CANCEL_REQUEST,
                TheFixMessageType.fromCode("order_cancel_request"));
    }

    // -----------------------------------------------------------------------
    // Domain flags
    // -----------------------------------------------------------------------

    @Test
    void newOrderSingleDoesNotRequireOrigClOrdIdAndSupportsBulk() {
        assertFalse(TheFixMessageType.NEW_ORDER_SINGLE.requiresOrigClOrdId());
        assertTrue(TheFixMessageType.NEW_ORDER_SINGLE.supportsBulk());
    }

    @Test
    void cancelReplaceRequiresOrigClOrdIdAndDoesNotSupportBulk() {
        assertTrue(TheFixMessageType.ORDER_CANCEL_REPLACE_REQUEST.requiresOrigClOrdId());
        assertFalse(TheFixMessageType.ORDER_CANCEL_REPLACE_REQUEST.supportsBulk());
    }

    @Test
    void cancelRequestRequiresOrigClOrdIdAndDoesNotSupportBulk() {
        assertTrue(TheFixMessageType.ORDER_CANCEL_REQUEST.requiresOrigClOrdId());
        assertFalse(TheFixMessageType.ORDER_CANCEL_REQUEST.supportsBulk());
    }

    // -----------------------------------------------------------------------
    // toJson
    // -----------------------------------------------------------------------

    @Test
    void toJsonIncludesAllFields() {
        JsonObject json = TheFixMessageType.NEW_ORDER_SINGLE.toJson();

        assertEquals("NEW_ORDER_SINGLE", json.getString("code"));
        assertEquals("D", json.getString("msgType"));
        assertEquals("NOS", json.getString("shortLabel"));
        assertFalse(json.getBoolean("requiresOrigClOrdId"));
        assertTrue(json.getBoolean("supportsBulk"));
    }

    @Test
    void toJsonForCancelRequestHasCorrectMsgType() {
        assertEquals("F", TheFixMessageType.ORDER_CANCEL_REQUEST.toJson().getString("msgType"));
    }

    @Test
    void toJsonForAmendHasCorrectMsgType() {
        assertEquals("G", TheFixMessageType.ORDER_CANCEL_REPLACE_REQUEST.toJson().getString("msgType"));
    }

    // -----------------------------------------------------------------------
    // options
    // -----------------------------------------------------------------------

    @Test
    void optionsReturnsAllThreeMessageTypes() {
        List<TheFixMessageType> options = TheFixMessageType.options();

        assertEquals(3, options.size());
    }
}
