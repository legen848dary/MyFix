package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;

import java.time.Instant;

record TheFixMessageTemplate(
        long id,
        String profileName,
        String name,
        String messageType,
        JsonObject draft,
        boolean autoSaved,
        Instant updatedAt
) {
    JsonObject toJson() {
        return new JsonObject()
                .put("id", id)
                .put("profileName", profileName)
                .put("name", name)
                .put("messageType", messageType)
                .put("draft", draft == null ? new JsonObject() : draft.copy())
                .put("autoSaved", autoSaved)
                .put("updatedAt", updatedAt == null ? Instant.now().toString() : updatedAt.toString());
    }
}

