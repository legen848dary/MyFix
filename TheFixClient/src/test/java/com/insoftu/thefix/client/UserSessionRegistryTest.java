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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSessionRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loginWithValidCredentialsCreatesASession() {
        UserSessionRegistry registry = createRegistry();

        Optional<UserSession> session = registry.login("trader1", "trader1");

        assertTrue(session.isPresent());
        assertNotNull(session.get().token());
        assertEquals("trader1", session.get().user().username());
        registry.close();
    }

    @Test
    void loginWithInvalidCredentialsReturnsEmpty() {
        UserSessionRegistry registry = createRegistry();

        Optional<UserSession> session = registry.login("trader1", "wrongpassword");

        assertFalse(session.isPresent());
        registry.close();
    }

    @Test
    void validTokenReturnsSessionAndSlidesExpiry() throws Exception {
        UserSessionRegistry registry = createRegistry();
        UserSession original = registry.login("trader1", "trader1").orElseThrow();

        Thread.sleep(5);
        Optional<UserSession> validated = registry.validate(original.token());

        assertTrue(validated.isPresent());
        // Expiry should be refreshed (same or later)
        assertFalse(validated.get().expiresAt().isBefore(original.expiresAt()));
        registry.close();
    }

    @Test
    void unknownTokenReturnsEmpty() {
        UserSessionRegistry registry = createRegistry();

        Optional<UserSession> session = registry.validate("no-such-token");

        assertFalse(session.isPresent());
        registry.close();
    }

    @Test
    void nullTokenReturnsEmpty() {
        UserSessionRegistry registry = createRegistry();

        assertFalse(registry.validate(null).isPresent());
        registry.close();
    }

    @Test
    void logoutInvalidatesToken() {
        UserSessionRegistry registry = createRegistry();
        UserSession session = registry.login("trader2", "trader2").orElseThrow();

        boolean loggedOut = registry.logout(session.token());

        assertTrue(loggedOut);
        assertFalse(registry.validate(session.token()).isPresent());
        registry.close();
    }

    @Test
    void logoutUnknownTokenReturnsFalse() {
        UserSessionRegistry registry = createRegistry();

        assertFalse(registry.logout("nonexistent-token"));
        registry.close();
    }

    @Test
    void reLoginReusesSameSessionForSameUser() {
        UserSessionRegistry registry = createRegistry();
        UserSession first = registry.login("trader1", "trader1").orElseThrow();
        UserSession second = registry.login("trader1", "trader1").orElseThrow();

        assertEquals(first.token(), second.token());
        assertEquals(1, registry.activeSessions());
        registry.close();
    }

    @Test
    void differentUsersGetIsolatedWorkbenchStates() {
        UserSessionRegistry registry = createRegistry();
        UserSession t1 = registry.login("trader1", "trader1").orElseThrow();
        UserSession t2 = registry.login("trader2", "trader2").orElseThrow();

        assertNotSame(t1.workbenchState(), t2.workbenchState());
        assertEquals(2, registry.activeSessions());
        registry.close();
    }

    @Test
    void closeEvictsAllSessions() {
        UserSessionRegistry registry = createRegistry();
        UserSession session = registry.login("admin", "admin").orElseThrow();
        String token = session.token();

        registry.close();

        assertFalse(registry.validate(token).isPresent());
        assertEquals(0, registry.activeSessions());
    }

    /**
     * Verifies that usernames containing path-traversal characters (e.g. {@code ..}, {@code /})
     * are rejected when creating a new workbench state directory, preventing directory-escape attacks.
     * The test uses a custom AuthModule so the invalid username passes authentication.
     */
    @Test
    void loginRejectsPathTraversalUsername() {
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0", 0, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                tempDir.toString(), false, 480, 1
        );
        // Craft an AuthModule that accepts any non-blank credentials to let us reach createWorkbenchState().
        AuthModule permissiveModule = new AuthModule() {
            @Override
            public Optional<AuthenticatedUser> authenticate(String username, String password) {
                if (username != null && !username.isBlank()) {
                    return Optional.of(new AuthenticatedUser(username, username, "TRADER"));
                }
                return Optional.empty();
            }
            @Override
            public String name() { return "permissive"; }
        };

        UserSessionRegistry registry = new UserSessionRegistry(config, permissiveModule);

        // Path-traversal sequences must be rejected.
        assertThrows(IllegalArgumentException.class, () -> registry.login("../admin", "x"));
        assertThrows(IllegalArgumentException.class, () -> registry.login("../../etc/passwd", "x"));
        assertThrows(IllegalArgumentException.class, () -> registry.login("sub/dir", "x")); // slash not allowed
        assertThrows(IllegalArgumentException.class, () -> registry.login("user name", "x")); // space not allowed
        assertThrows(IllegalArgumentException.class, () -> registry.login("user@host", "x")); // @ not allowed

        // Uppercase letters are safe as directory names and must be accepted.
        assertFalse(registry.login("ADMIN", "x").isEmpty());
        assertFalse(registry.login("Trader1", "x").isEmpty());

        registry.close();
    }

    /**
     * A user that logs in a second time after the first session has been evicted (expired) should
     * get a fresh session rather than having stale tokens accumulate.
     */
    @Test
    void expiredSessionIsEvictedAndNewSessionCreatedOnReLogin() {
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0", 0, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                tempDir.toString(), false,
                0,  // zero-minute timeout → sessions expire immediately
                1
        );
        UserSessionRegistry registry = new UserSessionRegistry(config, new DemoAuthModule());

        UserSession first = registry.login("trader1", "trader1").orElseThrow();
        // The zero-minute timeout means the first session is already expired.
        assertTrue(first.isExpired(), "Expected session to have expired immediately with 0-minute timeout");

        // A second login should evict the first (expired) session and produce a new token.
        UserSession second = registry.login("trader1", "trader1").orElseThrow();
        assertFalse(second.token().equals(first.token()),
                "Expected a new token after the original session expired");
        // There must be exactly one session — no stale accumulation.
        assertEquals(1, registry.activeSessions());
        registry.close();
    }

    /**
     * After logout, a second login for the same user must produce a new token
     * rather than seeing the evicted mapping as "still active".
     */
    @Test
    void afterLogoutReLoginCreatesNewSession() {
        UserSessionRegistry registry = createRegistry();
        UserSession first = registry.login("trader1", "trader1").orElseThrow();

        registry.logout(first.token());

        UserSession second = registry.login("trader1", "trader1").orElseThrow();
        // Logout must have cleared the username index so the new login creates a fresh token.
        assertFalse(first.token().equals(second.token()),
                "Expected a fresh token after logout");
        assertEquals(1, registry.activeSessions());
        registry.close();
    }

    /**
     * Verifies the boundary condition in {@code isExpired()}: a session whose
     * {@code expiresAt} equals exactly {@code Instant.now()} must be treated as expired
     * (i.e. {@code !now.isBefore(expiresAt)} rather than {@code now.isAfter(expiresAt)}).
     */
    @Test
    void sessionExpiredAtBoundaryIsConsideredExpired() {
        // A zero-minute timeout creates sessions that expire at (or before) now.
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0", 0, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                tempDir.toString(), false, 0, 1
        );
        UserSessionRegistry registry = new UserSessionRegistry(config, new DemoAuthModule());

        UserSession session = registry.login("trader1", "trader1").orElseThrow();
        assertTrue(session.isExpired(), "Zero-minute session should be expired immediately");
        registry.close();
    }

    @Test
    void maskTokenForLogHidesSensitiveValues() {
        assertEquals("<null>", UserSessionRegistry.maskTokenForLog(null));
        assertEquals("<empty>", UserSessionRegistry.maskTokenForLog("   "));
        assertEquals("***", UserSessionRegistry.maskTokenForLog("12345678"));
        assertEquals("1234...cdef", UserSessionRegistry.maskTokenForLog("1234567890abcdef"));
    }

    private UserSessionRegistry createRegistry() {
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0", 0, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                tempDir.toString(), false, 480, 1
        );
        return new UserSessionRegistry(config, new DemoAuthModule());
    }
}
