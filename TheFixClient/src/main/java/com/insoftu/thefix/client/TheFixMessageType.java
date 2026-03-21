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
