package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum TheFixFixVersion {
    FIX_42("FIX_42", "FIX 4.2", "FIX.4.2", "FIX.4.2"),
    FIX_44("FIX_44", "FIX 4.4", "FIX.4.4", "FIX.4.4"),
    FIX_50_SP2("FIX_50_SP2", "FIX 5.0 SP2", "FIXT.1.1", "FIX.5.0SP2");

    private final String code;
    private final String label;
    private final String beginString;
    private final String defaultApplVerId;

    TheFixFixVersion(String code, String label, String beginString, String defaultApplVerId) {
        this.code = code;
        this.label = label;
        this.beginString = beginString;
        this.defaultApplVerId = defaultApplVerId;
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

    JsonObject toJson() {
        return new JsonObject()
                .put("code", code)
                .put("label", label)
                .put("beginString", beginString)
                .put("defaultApplVerId", defaultApplVerId);
    }

    static TheFixFixVersion fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return FIX_44;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.US);
        return Arrays.stream(values())
                .filter(version -> version.code.equals(normalized))
                .findFirst()
                .orElse(FIX_44);
    }

    static TheFixFixVersion fromBeginString(String beginString, String defaultApplVerId) {
        if ("FIXT.1.1".equalsIgnoreCase(beginString)) {
            return FIX_50_SP2;
        }
        if ("FIX.4.2".equalsIgnoreCase(beginString)) {
            return FIX_42;
        }
        if ("FIX.4.4".equalsIgnoreCase(beginString)) {
            return FIX_44;
        }
        if (defaultApplVerId != null && defaultApplVerId.toUpperCase(Locale.US).contains("5.0")) {
            return FIX_50_SP2;
        }
        return FIX_44;
    }

    static List<TheFixFixVersion> options() {
        return List.of(values());
    }
}

