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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * H2-backed persistence layer for the per-user order blotter.
 *
 * <p>On every write the full JSON snapshot of a profile's visible orders is
 * stored.  At startup the snapshot is reloaded into memory, giving continuity
 * across process restarts and upgrades.  Rows older than the configured
 * {@code orderDataRetentionDays} are purged on each load.
 *
 * <p>The store uses one row per (username, profile_name) pair: the entire
 * blotter is an opaque JSON blob, keeping the schema minimal and the write
 * path fast.
 */
final class TheFixOrderStore implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TheFixOrderStore.class);
    private static final String DB_BASENAME = "thefixclient-orders";

    private final String jdbcUrl;
    private final int retentionDays;

    TheFixOrderStore(TheFixClientConfig config) {
        this(Path.of(config.quickFixLogDir(), "orders", DB_BASENAME)
                        .toAbsolutePath().normalize(),
                config.orderDataRetentionDays());
    }

    TheFixOrderStore(Path databaseBasePath, int retentionDays) {
        try {
            Files.createDirectories(databaseBasePath.toAbsolutePath().normalize().getParent());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create order store directory", exception);
        }
        this.jdbcUrl = "jdbc:h2:file:" + databaseBasePath.toAbsolutePath().normalize()
                + ";AUTO_SERVER=FALSE;DB_CLOSE_DELAY=0;DATABASE_TO_UPPER=false";
        this.retentionDays = Math.max(1, retentionDays);
        initializeSchema();
    }

    /**
     * Persists (upserts) the current order snapshot for the given user and profile.
     *
     * @param username    the authenticated username
     * @param profileName the FIX session profile name
     * @param ordersJson  the JSON array of order objects as produced by
     *                    {@code TheFixClientFixService.recentOrdersJson()}
     */
    synchronized void persist(String username, String profileName, JsonArray ordersJson) {
        if (username == null || username.isBlank() || profileName == null || profileName.isBlank()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement merge = connection.prepareStatement("""
                     MERGE INTO user_orders (username, profile_name, orders_json, updated_at)
                     KEY (username, profile_name)
                     VALUES (?, ?, ?, ?)
                     """)) {
            merge.setString(1, username.trim());
            merge.setString(2, profileName.trim());
            merge.setString(3, ordersJson == null ? "[]" : ordersJson.encode());
            merge.setTimestamp(4, Timestamp.from(Instant.now()));
            merge.executeUpdate();
        } catch (SQLException exception) {
            log.warn("Unable to persist orders for user={} profile={}", username, profileName, exception);
        }
    }

    /**
     * Loads the persisted order snapshot for the given user and profile,
     * removing rows that exceed the configured retention window.
     *
     * @return the stored {@link JsonArray} of order objects, or an empty array
     *         when no snapshot exists or the snapshot has expired
     */
    synchronized JsonArray load(String username, String profileName) {
        if (username == null || username.isBlank() || profileName == null || profileName.isBlank()) {
            return new JsonArray();
        }
        purgeExpired(username);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT orders_json FROM user_orders
                     WHERE username = ? AND profile_name = ?
                     """)) {
            statement.setString(1, username.trim());
            statement.setString(2, profileName.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String json = resultSet.getString("orders_json");
                    if (json == null) {
                        return new JsonArray();
                    }
                    try {
                        return new JsonArray(json);
                    } catch (Exception decodeException) {
                        log.warn("Corrupted order snapshot for user={} profile={} — discarding", username, profileName, decodeException);
                        deleteCorruptedSnapshot(username, profileName);
                        return new JsonArray();
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("Unable to load orders for user={} profile={}", username, profileName, exception);
        }
        return new JsonArray();
    }

    /**
     * Removes all order rows for the given user that are older than the
     * configured retention window.
     */
    synchronized void purgeExpired(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Connection connection = openConnection();
             PreparedStatement delete = connection.prepareStatement("""
                     DELETE FROM user_orders WHERE username = ? AND updated_at < ?
                     """)) {
            delete.setString(1, username.trim());
            delete.setTimestamp(2, Timestamp.from(cutoff));
            int deleted = delete.executeUpdate();
            if (deleted > 0) {
                log.debug("Purged {} expired order snapshot(s) for user={}", deleted, username);
            }
        } catch (SQLException exception) {
            log.warn("Unable to purge expired orders for user={}", username, exception);
        }
    }

    @Override
    public void close() {
        // Connections opened per-operation; nothing to close here.
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void deleteCorruptedSnapshot(String username, String profileName) {
        try (Connection connection = openConnection();
             PreparedStatement delete = connection.prepareStatement("""
                     DELETE FROM user_orders WHERE username = ? AND profile_name = ?
                     """)) {
            delete.setString(1, username.trim());
            delete.setString(2, profileName.trim());
            delete.executeUpdate();
        } catch (SQLException exception) {
            log.warn("Unable to delete corrupted order snapshot for user={} profile={}", username, profileName, exception);
        }
    }

    private void initializeSchema() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS user_orders (
                        id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        username    VARCHAR(255)  NOT NULL,
                        profile_name VARCHAR(255) NOT NULL,
                        orders_json CLOB          NOT NULL,
                        updated_at  TIMESTAMP     NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_user_orders_uk
                    ON user_orders(username, profile_name)
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize order store schema", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }
}
