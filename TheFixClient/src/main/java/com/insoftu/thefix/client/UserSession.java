package com.insoftu.thefix.client;

import java.time.Instant;

/**
 * Lightweight value type representing a single active user session.
 *
 * @param token          opaque bearer token (UUID); included in every API request
 * @param user           the authenticated user
 * @param workbenchState the per-user FIX workbench — never shared between sessions
 * @param expiresAt      instant at which the token becomes invalid;
 *                       refreshed on each successful API call
 */
record UserSession(
        String token,
        AuthenticatedUser user,
        TheFixClientWorkbenchState workbenchState,
        Instant expiresAt
) {
    UserSession {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (workbenchState == null) {
            throw new IllegalArgumentException("workbenchState must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
    }

    /** Returns a copy of this session with an updated expiry. */
    UserSession withExpiresAt(Instant newExpiresAt) {
        return new UserSession(token, user, workbenchState, newExpiresAt);
    }

    boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
