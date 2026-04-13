package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAuthModuleTest {

    private final DemoAuthModule module = new DemoAuthModule();

    @Test
    void nameIsDemo() {
        assertEquals("Demo", module.name());
    }

    @Test
    void validAdminCredentialsAuthenticateSuccessfully() {
        Optional<AuthenticatedUser> result = module.authenticate("admin", "admin");

        assertTrue(result.isPresent());
        assertEquals("admin", result.get().username());
        assertEquals("ADMIN", result.get().role());
    }

    @Test
    void validTraderCredentialsAuthenticateSuccessfully() {
        Optional<AuthenticatedUser> result = module.authenticate("trader1", "trader1");

        assertTrue(result.isPresent());
        assertEquals("trader1", result.get().username());
        assertEquals("TRADER", result.get().role());
        assertEquals("Alex Trader", result.get().displayName());
    }

    @Test
    void usernameMatchingIsCaseInsensitive() {
        Optional<AuthenticatedUser> result = module.authenticate("TRADER2", "trader2");

        assertTrue(result.isPresent());
        assertEquals("trader2", result.get().username());
    }

    @Test
    void wrongPasswordReturnsEmpty() {
        Optional<AuthenticatedUser> result = module.authenticate("admin", "wrongpassword");

        assertFalse(result.isPresent());
    }

    @Test
    void unknownUsernameReturnsEmpty() {
        Optional<AuthenticatedUser> result = module.authenticate("nobody", "nobody");

        assertFalse(result.isPresent());
    }

    @Test
    void nullUsernameReturnsEmpty() {
        assertFalse(module.authenticate(null, "admin").isPresent());
    }

    @Test
    void nullPasswordReturnsEmpty() {
        assertFalse(module.authenticate("admin", null).isPresent());
    }

    @Test
    void allFourDemoUsersAreAuthenticated() {
        assertTrue(module.authenticate("admin", "admin").isPresent());
        assertTrue(module.authenticate("trader1", "trader1").isPresent());
        assertTrue(module.authenticate("trader2", "trader2").isPresent());
        assertTrue(module.authenticate("trader3", "trader3").isPresent());
    }
}
