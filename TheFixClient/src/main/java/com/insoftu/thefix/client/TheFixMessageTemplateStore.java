package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class TheFixMessageTemplateStore implements AutoCloseable {
    private static final String TEMPLATE_DB_BASENAME = "thefixclient-message-templates";
    private static final DateTimeFormatter AUTO_TEMPLATE_NAME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final String jdbcUrl;

    TheFixMessageTemplateStore(TheFixClientConfig config) {
        this(Path.of(config.quickFixLogDir(), "templates", TEMPLATE_DB_BASENAME).toAbsolutePath().normalize());
    }

    TheFixMessageTemplateStore(Path databaseBasePath) {
        try {
            Files.createDirectories(databaseBasePath.toAbsolutePath().normalize().getParent());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create message template database directory", exception);
        }
        this.jdbcUrl = "jdbc:h2:file:" + databaseBasePath.toAbsolutePath().normalize() + ";AUTO_SERVER=FALSE;DB_CLOSE_DELAY=0;DATABASE_TO_UPPER=false";
        initializeSchema();
    }

    synchronized JsonObject snapshot(String profileName) {
        JsonArray items = new JsonArray();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, profile_name, template_name, message_type, template_json, auto_saved, updated_at
                     FROM message_templates
                     WHERE profile_name = ?
                     ORDER BY updated_at DESC, template_name ASC
                     """)) {
            statement.setString(1, sanitizeProfile(profileName));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(readTemplate(resultSet).toJson());
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load FIX message templates", exception);
        }
        return new JsonObject()
                .put("profileName", sanitizeProfile(profileName))
                .put("items", items);
    }

    synchronized TheFixMessageTemplate saveManualTemplate(String profileName, String requestedName, JsonObject draft) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Template name is required.");
        }
        return upsertTemplate(sanitizeProfile(profileName), name, sanitizeDraft(draft), false);
    }

    synchronized TheFixMessageTemplate autoSaveTemplate(String profileName, JsonObject draft) {
        JsonObject sanitizedDraft = sanitizeDraft(draft);
        String messageType = sanitizedDraft.getString("messageType", TheFixMessageType.NEW_ORDER_SINGLE.code());
        String symbol = sanitizedDraft.getString("symbol", "").trim();
        String suffix = symbol.isBlank() ? messageType : messageType + " · " + symbol;
        String generatedName = "Auto · " + AUTO_TEMPLATE_NAME_FORMAT.format(Instant.now()) + " · " + suffix;
        return upsertTemplate(sanitizeProfile(profileName), generatedName, sanitizedDraft, true);
    }

    @Override
    public void close() {
        // Connections are opened per operation; nothing to close here.
    }

    private TheFixMessageTemplate upsertTemplate(String profileName, String name, JsonObject draft, boolean autoSaved) {
        String messageType = draft.getString("messageType", TheFixMessageType.NEW_ORDER_SINGLE.code());
        Instant updatedAt = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement merge = connection.prepareStatement("""
                     MERGE INTO message_templates (profile_name, template_name, message_type, template_json, auto_saved, updated_at)
                     KEY (profile_name, template_name)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            merge.setString(1, profileName);
            merge.setString(2, name);
            merge.setString(3, messageType);
            merge.setString(4, draft.encode());
            merge.setBoolean(5, autoSaved);
            merge.setTimestamp(6, Timestamp.from(updatedAt));
            merge.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist FIX message template '" + name + "'", exception);
        }
        return findByName(profileName, name);
    }

    private TheFixMessageTemplate findByName(String profileName, String name) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, profile_name, template_name, message_type, template_json, auto_saved, updated_at
                     FROM message_templates
                     WHERE profile_name = ? AND template_name = ?
                     """)) {
            statement.setString(1, profileName);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readTemplate(resultSet);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reload FIX message template '" + name + "'", exception);
        }
        throw new IllegalStateException("Saved FIX message template '" + name + "' could not be reloaded.");
    }

    private void initializeSchema() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS message_templates (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        profile_name VARCHAR(255) NOT NULL,
                        template_name VARCHAR(255) NOT NULL,
                        message_type VARCHAR(64) NOT NULL,
                        template_json CLOB NOT NULL,
                        auto_saved BOOLEAN NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_message_templates_profile_name ON message_templates(profile_name, template_name)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize FIX message template database", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    private static TheFixMessageTemplate readTemplate(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new TheFixMessageTemplate(
                resultSet.getLong("id"),
                resultSet.getString("profile_name"),
                resultSet.getString("template_name"),
                resultSet.getString("message_type"),
                new JsonObject(resultSet.getString("template_json")),
                resultSet.getBoolean("auto_saved"),
                updatedAt == null ? Instant.now() : updatedAt.toInstant()
        );
    }

    private static JsonObject sanitizeDraft(JsonObject draft) {
        JsonObject sanitized = draft == null ? new JsonObject() : draft.copy();
        if (!(sanitized.getValue("additionalTags") instanceof JsonArray)) {
            sanitized.put("additionalTags", new JsonArray());
        }
        sanitized.put("messageType", sanitized.getString("messageType", TheFixMessageType.NEW_ORDER_SINGLE.code()));
        sanitized.put("clOrdId", sanitized.getString("clOrdId", ""));
        sanitized.put("origClOrdId", sanitized.getString("origClOrdId", ""));
        sanitized.put("region", sanitized.getString("region", "AMERICAS"));
        sanitized.put("market", sanitized.getString("market", "XNAS"));
        sanitized.put("symbol", sanitized.getString("symbol", "AAPL"));
        sanitized.put("side", sanitized.getString("side", "BUY"));
        sanitized.put("orderType", sanitized.getString("orderType", "LIMIT"));
        sanitized.put("priceType", sanitized.getString("priceType", "PER_UNIT"));
        sanitized.put("timeInForce", sanitized.getString("timeInForce", "DAY"));
        sanitized.put("currency", sanitized.getString("currency", "USD"));
        sanitized.put("quantity", numberValue(sanitized.getValue("quantity"), 100));
        sanitized.put("price", decimalValue(sanitized.getValue("price"), 100.25d));
        sanitized.put("stopPrice", decimalValue(sanitized.getValue("stopPrice"), 0d));
        return sanitized;
    }

    private static String sanitizeProfile(String profileName) {
        return profileName == null || profileName.isBlank() ? TheFixSessionProfile.DEFAULT_PROFILE_NAME : profileName.trim();
    }

    private static int numberValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double decimalValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            try {
                return Double.parseDouble(raw.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

