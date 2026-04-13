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
