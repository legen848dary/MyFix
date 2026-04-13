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
