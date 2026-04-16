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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Demo {@link AuthModule} that authenticates against a small set of hardcoded
 * users.  Intended purely for local testing and development.  Replace this
 * with a production-grade implementation (e.g., LDAP, OAuth 2, DB-backed) by
 * implementing {@link AuthModule} and passing the new instance to
 * {@link UserSessionRegistry}.
 *
 * <p>Preconfigured demo accounts:
 * <ul>
 *   <li>{@code admin} / {@code admin}  — role ADMIN</li>
 *   <li>{@code trader1} / {@code trader1} — role TRADER</li>
 *   <li>{@code trader2} / {@code trader2} — role TRADER</li>
 *   <li>{@code trader3} / {@code trader3} — role TRADER</li>
 * </ul>
 */
public final class DemoAuthModule implements AuthModule {

    private static final Map<String, AuthenticatedUser> USERS;

    static {
        USERS = new LinkedHashMap<>();
        register("admin", "admin", "System Administrator", "ADMIN");
        register("trader1", "trader1", "Alex Trader", "TRADER");
        register("trader2", "trader2", "Blake Trader", "TRADER");
        register("trader3", "trader3", "Casey Trader", "TRADER");
    }

    private static void register(String username, String password, String displayName, String role) {
        String key = username.trim().toLowerCase(java.util.Locale.ROOT) + ':' + password;
        USERS.put(key, new AuthenticatedUser(username, displayName, role));
    }

    @Override
    public String name() {
        return "Demo";
    }

    /**
     * Authenticates the supplied credentials against the preconfigured demo
     * user table.  Comparison is case-insensitive for the username but
     * case-sensitive for the password (mirrors real-world conventions).
     *
     * @param username the user identifier (never {@code null})
     * @param password the raw password (never {@code null})
     * @return the matching {@link AuthenticatedUser} or empty
     */
    @Override
    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        String key = username.trim().toLowerCase(java.util.Locale.ROOT) + ':' + password;
        return Optional.ofNullable(USERS.get(key));
    }
}
