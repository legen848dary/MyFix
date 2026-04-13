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
