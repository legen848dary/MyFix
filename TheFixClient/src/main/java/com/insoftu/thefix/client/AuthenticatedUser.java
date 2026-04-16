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

/**
 * Represents a successfully authenticated user.
 *
 * @param username    the canonical identifier used to isolate per-user storage
 * @param displayName human-readable full name shown in the UI
 * @param role        coarse-grained role, e.g. {@code "TRADER"} or {@code "ADMIN"}
 */
public record AuthenticatedUser(String username, String displayName, String role) {

    public AuthenticatedUser {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        username = username.trim().toLowerCase(java.util.Locale.ROOT);
        displayName = displayName.trim();
        role = role.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
