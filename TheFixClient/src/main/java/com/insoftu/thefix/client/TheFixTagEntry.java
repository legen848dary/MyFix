package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

record TheFixTagEntry(int tag, String name, String value, boolean custom) {
    static List<TheFixTagEntry> fromJsonArray(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        List<TheFixTagEntry> tags = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject item = array.getJsonObject(i);
            if (item == null) {
                continue;
            }
            Object rawTag = item.getValue("tag");
            int tag = rawTag instanceof Number number ? number.intValue() : parseInt(item.getString("tag"));
            tags.add(new TheFixTagEntry(
                    tag,
                    item.getString("name", ""),
                    item.getString("value", ""),
                    item.getBoolean("custom", false)
            ));
        }
        return List.copyOf(tags);
    }

    JsonObject toJson() {
        return new JsonObject()
                .put("tag", tag)
                .put("name", name)
                .put("value", value)
                .put("custom", custom);
    }

    boolean hasValue() {
        return value != null && !value.isBlank();
    }

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
