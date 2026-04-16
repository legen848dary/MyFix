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

import java.util.Optional;

/**
 * Pluggable authentication contract for the FIX client workstation.
 *
 * <p>Implement this interface to integrate any credential store (LDAP, OAuth,
 * database, etc.).  The runtime picks the active module at startup; swapping
 * the implementation requires no changes to the routing or session layer.
 *
 * <pre>{@code
 * // Registering a custom module:
 * AuthModule myModule = new MyLdapAuthModule(...);
 * UserSessionRegistry registry = new UserSessionRegistry(config, myModule);
 * }</pre>
 */
public interface AuthModule {

    /**
     * Returns the human-readable name of this authentication module,
     * e.g. {@code "Demo"} or {@code "LDAP"}.
     */
    String name();

    /**
     * Attempts to authenticate the supplied credentials.
     *
     * @param username the user identifier (never {@code null})
     * @param password the raw password (never {@code null})
     * @return an {@link Optional} containing the authenticated user when
     *         credentials are valid, or {@link Optional#empty()} otherwise
     */
    Optional<AuthenticatedUser> authenticate(String username, String password);
}
