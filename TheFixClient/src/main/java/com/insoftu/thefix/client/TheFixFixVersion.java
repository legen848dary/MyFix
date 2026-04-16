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

enum TheFixFixVersion {
    FIX_42("FIX_42", "FIX 4.2", "FIX.4.2", "FIX.4.2", "FIX42.xml"),
    FIX_44("FIX_44", "FIX 4.4", "FIX.4.4", "FIX.4.4", "FIX44.xml"),
    FIX_50("FIX_50", "FIX 5.0", "FIXT.1.1", "FIX.5.0", "FIX50.xml"),
    FIX_52("FIX_52", "FIX 5.2", "FIXT.1.1", "FIX.5.0SP2", "FIX50SP2.xml");

    private final String code;
    private final String label;
    private final String beginString;
    private final String defaultApplVerId;
    private final String dictionaryResource;

    TheFixFixVersion(String code, String label, String beginString, String defaultApplVerId, String dictionaryResource) {
        this.code = code;
        this.label = label;
        this.beginString = beginString;
        this.defaultApplVerId = defaultApplVerId;
        this.dictionaryResource = dictionaryResource;
    }

    String code() {
        return code;
    }

    String label() {
        return label;
    }

    String beginString() {
        return beginString;
    }

    String defaultApplVerId() {
        return defaultApplVerId;
    }

    String dictionaryResource() {
        return dictionaryResource;
    }

    JsonObject toJson() {
        return new JsonObject()
                .put("code", code)
                .put("label", label)
                .put("beginString", beginString)
                .put("defaultApplVerId", defaultApplVerId)
                .put("dictionaryResource", dictionaryResource);
    }

    static TheFixFixVersion fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return FIX_44;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.US);
        if ("FIX_50_SP2".equals(normalized)) {
            return FIX_52;
        }
        return Arrays.stream(values())
                .filter(version -> version.code.equals(normalized))
                .findFirst()
                .orElse(FIX_44);
    }

    static TheFixFixVersion fromBeginString(String beginString, String defaultApplVerId) {
        if ("FIXT.1.1".equalsIgnoreCase(beginString)) {
            if (defaultApplVerId != null && defaultApplVerId.toUpperCase(Locale.US).contains("SP2")) {
                return FIX_52;
            }
            return FIX_50;
        }
        if ("FIX.4.2".equalsIgnoreCase(beginString)) {
            return FIX_42;
        }
        if ("FIX.4.4".equalsIgnoreCase(beginString)) {
            return FIX_44;
        }
        if ("FIX.5.0".equalsIgnoreCase(beginString)) {
            return FIX_50;
        }
        if (defaultApplVerId != null && defaultApplVerId.toUpperCase(Locale.US).contains("5.0")) {
            return defaultApplVerId.toUpperCase(Locale.US).contains("SP2") ? FIX_52 : FIX_50;
        }
        return FIX_44;
    }

    static List<TheFixFixVersion> options() {
        return List.of(values());
    }
}
