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
        return !Instant.now().isBefore(expiresAt);
    }
}
