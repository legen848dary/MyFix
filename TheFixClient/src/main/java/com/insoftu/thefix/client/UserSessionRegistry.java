package com.insoftu.thefix.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages the lifecycle of authenticated user sessions.
 *
 * <p>Each successful login creates an isolated {@link UserSession} that owns:
 * <ul>
 *   <li>a unique bearer token (UUID)</li>
 *   <li>the {@link AuthenticatedUser} identity</li>
 *   <li>a dedicated {@link TheFixClientWorkbenchState} backed by per-user
 *       storage under {@code <quickFixLogDir>/users/<username>/}</li>
 * </ul>
 *
 * <p>Sessions slide: every successful token validation resets the idle timer.
 * Stale sessions are reaped lazily on each token lookup and eagerly on logout.
 *
 * <p>Thread-safety: all public methods are safe for concurrent use.
 */
final class UserSessionRegistry implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(UserSessionRegistry.class);
    /**
     * Allowlist for username path segments: letters (upper/lower), digits, hyphen, underscore.
     * Prevents path-traversal attacks when a username is used as a filesystem directory name.
     * Dots, slashes, backslashes, and other special characters are explicitly excluded.
     */
    private static final Pattern SAFE_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final TheFixClientConfig config;
    private final AuthModule authModule;
    private final Duration sessionTimeout;
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    UserSessionRegistry(TheFixClientConfig config, AuthModule authModule) {
        this.config = config;
        this.authModule = authModule;
        this.sessionTimeout = Duration.ofMinutes(config.sessionTimeoutMinutes());
    }

    /**
     * Attempts to authenticate the supplied credentials and, on success,
     * creates (or reconnects to) a session for that user.
     *
     * <p>If the user already has an active session, the existing session is
     * reused and its expiry is refreshed, so that a second browser tab or
     * re-login does not wipe in-flight FIX sessions.
     *
     * @return the session, or empty when credentials are invalid
     */
    Optional<UserSession> login(String username, String password) {
        Optional<AuthenticatedUser> authenticated = authModule.authenticate(username, password);
        if (authenticated.isEmpty()) {
            return Optional.empty();
        }
        AuthenticatedUser user = authenticated.get();

        // Evict any expired sessions for this user to prevent accumulation of stale state.
        sessions.entrySet().removeIf(entry -> {
            UserSession s = entry.getValue();
            if (s.user().username().equals(user.username()) && s.isExpired()) {
                closeQuietly(s);
                log.debug("user={} evicted stale expired session token={}", user.username(), entry.getKey());
                return true;
            }
            return false;
        });

        // Reuse any existing non-expired session for this user so the FIX
        // state (open connections, order blotter) survives a page refresh / re-login.
        for (UserSession existing : sessions.values()) {
            if (existing.user().username().equals(user.username()) && !existing.isExpired()) {
                UserSession refreshed = existing.withExpiresAt(nextExpiry());
                sessions.put(refreshed.token(), refreshed);
                log.info("auth module={} user={} re-login, session refreshed token={}", authModule.name(), user.username(), refreshed.token());
                return Optional.of(refreshed);
            }
        }

        String token = UUID.randomUUID().toString();
        TheFixClientWorkbenchState state = createWorkbenchState(user.username());
        UserSession session = new UserSession(token, user, state, nextExpiry());
        sessions.put(token, session);
        log.info("auth module={} user={} login ok token={}", authModule.name(), user.username(), token);
        return Optional.of(session);
    }

    /**
     * Validates a bearer token and, if valid, slides the session expiry.
     *
     * @return the live session, or empty when the token is missing/expired
     */
    Optional<UserSession> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        UserSession session = sessions.get(token);
        if (session == null || session.isExpired()) {
            if (session != null) {
                evict(token, session);
            }
            return Optional.empty();
        }
        // Slide expiry on activity.
        UserSession refreshed = session.withExpiresAt(nextExpiry());
        sessions.put(token, refreshed);
        return Optional.of(refreshed);
    }

    /**
     * Invalidates the token and closes the associated workbench state.
     *
     * @return {@code true} if the token was known and removed
     */
    boolean logout(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        UserSession session = sessions.remove(token);
        if (session == null) {
            return false;
        }
        closeQuietly(session);
        log.info("user={} logged out token={}", session.user().username(), token);
        return true;
    }

    /** Returns the number of currently active sessions (including potentially stale ones). */
    int activeSessions() {
        return sessions.size();
    }

    @Override
    public void close() {
        for (UserSession session : sessions.values()) {
            closeQuietly(session);
        }
        sessions.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private TheFixClientWorkbenchState createWorkbenchState(String username) {
        if (!SAFE_USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username '" + username + "' contains characters not allowed in filesystem paths. " +
                    "Allowed: a-z, A-Z, 0-9, hyphen, underscore (max 64 chars).");
        }
        Path baseDir = Path.of(config.quickFixLogDir(), "users").toAbsolutePath().normalize();
        Path userBase = baseDir.resolve(username).normalize();
        // Belt-and-suspenders: verify the resolved path is still within the expected parent.
        if (!userBase.startsWith(baseDir)) {
            throw new IllegalArgumentException(
                    "Resolved user directory escapes the expected base path for username '" + username + "'.");
        }
        TheFixClientConfig userConfig = config.withQuickFixLogDir(userBase.toString());
        TheFixSessionProfileStore profileStore = new TheFixSessionProfileStore(userConfig);
        TheFixMessageTemplateStore templateStore = new TheFixMessageTemplateStore(userConfig);
        TheFixOrderStore orderStore = new TheFixOrderStore(userConfig);
        return new TheFixClientWorkbenchState(username, userConfig, profileStore, templateStore, orderStore);
    }

    private void evict(String token, UserSession session) {
        sessions.remove(token);
        closeQuietly(session);
        log.debug("user={} session expired token={}", session.user().username(), token);
    }

    private static void closeQuietly(UserSession session) {
        try {
            session.workbenchState().close();
        } catch (Exception exception) {
            log.warn("Error closing workbench state for user={}", session.user().username(), exception);
        }
    }

    private Instant nextExpiry() {
        return Instant.now().plus(sessionTimeout);
    }
}
