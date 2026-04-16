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
