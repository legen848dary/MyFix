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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum TheFixMessageType {
    NEW_ORDER_SINGLE("NEW_ORDER_SINGLE", "New Order Single", "NOS", "D", false, true),
    ORDER_CANCEL_REPLACE_REQUEST("ORDER_CANCEL_REPLACE_REQUEST", "Amend / Cancel Replace", "AMEND", "G", true, false),
    ORDER_CANCEL_REQUEST("ORDER_CANCEL_REQUEST", "Order Cancel Request", "CANCEL", "F", true, false);

    private final String code;
    private final String label;
    private final String shortLabel;
    private final String msgType;
    private final boolean requiresOrigClOrdId;
    private final boolean supportsBulk;

    TheFixMessageType(String code, String label, String shortLabel, String msgType, boolean requiresOrigClOrdId, boolean supportsBulk) {
        this.code = code;
        this.label = label;
        this.shortLabel = shortLabel;
        this.msgType = msgType;
        this.requiresOrigClOrdId = requiresOrigClOrdId;
        this.supportsBulk = supportsBulk;
    }

    String code() {
        return code;
    }

    String label() {
        return label;
    }

    String shortLabel() {
        return shortLabel;
    }

    String msgType() {
        return msgType;
    }

    boolean requiresOrigClOrdId() {
        return requiresOrigClOrdId;
    }

    boolean supportsBulk() {
        return supportsBulk;
    }

    JsonObject toJson() {
        return new JsonObject()
                .put("code", code)
                .put("label", label)
                .put("shortLabel", shortLabel)
                .put("msgType", msgType)
                .put("requiresOrigClOrdId", requiresOrigClOrdId)
                .put("supportsBulk", supportsBulk);
    }

    static TheFixMessageType fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return NEW_ORDER_SINGLE;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.US);
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(NEW_ORDER_SINGLE);
    }

    static List<TheFixMessageType> options() {
        return List.of(values());
    }
}
