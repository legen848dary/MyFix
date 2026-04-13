package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
