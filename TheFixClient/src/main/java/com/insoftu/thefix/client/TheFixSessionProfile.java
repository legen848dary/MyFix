package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;

import java.nio.file.Path;
import java.util.Locale;

record TheFixSessionProfile(
        String name,
        String senderCompId,
        String targetCompId,
        String fixHost,
        int fixPort,
        String fixVersionCode,
        boolean resetOnLogon,
        String sessionStartTime,
        String sessionEndTime,
        int heartBtIntSec,
        int reconnectIntervalSec,
        String quickFixLogDir,
        boolean rawMessageLoggingEnabled
) {
    static final String DEFAULT_PROFILE_NAME = "Default profile";

    static TheFixSessionProfile defaultProfile(TheFixClientConfig config) {
        return new TheFixSessionProfile(
                DEFAULT_PROFILE_NAME,
                config.senderCompId(),
                config.targetCompId(),
                config.fixHost(),
                config.fixPort(),
                TheFixFixVersion.fromBeginString(config.beginString(), config.defaultApplVerId()).code(),
                true,
                "00:00:00",
                "00:00:00",
                config.heartBtIntSec(),
                config.reconnectIntervalSec(),
                config.quickFixLogDir(),
                config.rawMessageLoggingEnabled()
        );
    }

    static TheFixSessionProfile fromJson(JsonObject json, TheFixClientConfig config) {
        TheFixSessionProfile defaults = defaultProfile(config);
        String profileName = sanitizeText(json == null ? null : json.getString("name"), defaults.name());
        String senderCompId = sanitizeText(json == null ? null : json.getString("senderCompId"), defaults.senderCompId());
        String targetCompId = sanitizeText(json == null ? null : json.getString("targetCompId"), defaults.targetCompId());
        String fixHost = sanitizeText(json == null ? null : json.getString("fixHost"), defaults.fixHost());
        int fixPort = parsePort(json == null ? null : json.getValue("fixPort"), defaults.fixPort());
        TheFixFixVersion version = TheFixFixVersion.fromCode(json == null ? null : json.getString("fixVersionCode"));
        boolean resetOnLogon = json != null && json.containsKey("resetOnLogon")
                ? json.getBoolean("resetOnLogon", defaults.resetOnLogon())
                : defaults.resetOnLogon();
        String sessionStartTime = sanitizeSessionTime(json == null ? null : json.getString("sessionStartTime"), defaults.sessionStartTime());
        String sessionEndTime = sanitizeSessionTime(json == null ? null : json.getString("sessionEndTime"), defaults.sessionEndTime());
        int heartBtIntSec = parsePositiveInt(json == null ? null : json.getValue("heartBtIntSec"), defaults.heartBtIntSec());
        int reconnectIntervalSec = parsePositiveInt(json == null ? null : json.getValue("reconnectIntervalSec"), defaults.reconnectIntervalSec());
        String quickFixLogDir = sanitizeText(json == null ? null : json.getString("quickFixLogDir"), defaults.quickFixLogDir());
        boolean rawMessageLoggingEnabled = json != null && json.containsKey("rawMessageLoggingEnabled")
                ? json.getBoolean("rawMessageLoggingEnabled", defaults.rawMessageLoggingEnabled())
                : defaults.rawMessageLoggingEnabled();

        return new TheFixSessionProfile(
                profileName,
                senderCompId,
                targetCompId,
                fixHost,
                fixPort,
                version.code(),
                resetOnLogon,
                sessionStartTime,
                sessionEndTime,
                heartBtIntSec,
                reconnectIntervalSec,
                quickFixLogDir,
                rawMessageLoggingEnabled
        );
    }

    TheFixFixVersion fixVersion() {
        return TheFixFixVersion.fromCode(fixVersionCode);
    }

    String beginString() {
        return fixVersion().beginString();
    }

    String defaultApplVerId() {
        return fixVersion().defaultApplVerId();
    }

    Path storeDir() {
        return Path.of(quickFixLogDir, "store");
    }

    Path rawLogDir() {
        return Path.of(quickFixLogDir, "messages");
    }

    JsonObject toJson() {
        return new JsonObject()
                .put("name", name)
                .put("senderCompId", senderCompId)
                .put("targetCompId", targetCompId)
                .put("fixHost", fixHost)
                .put("fixPort", fixPort)
                .put("fixVersionCode", fixVersionCode)
                .put("fixVersionLabel", fixVersion().label())
                .put("beginString", beginString())
                .put("defaultApplVerId", defaultApplVerId())
                .put("resetOnLogon", resetOnLogon)
                .put("sessionStartTime", sessionStartTime)
                .put("sessionEndTime", sessionEndTime)
                .put("heartBtIntSec", heartBtIntSec)
                .put("reconnectIntervalSec", reconnectIntervalSec)
                .put("quickFixLogDir", quickFixLogDir)
                .put("rawMessageLoggingEnabled", rawMessageLoggingEnabled);
    }

    private static String sanitizeText(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private static int parsePort(Object raw, int fallback) {
        int parsed = parsePositiveInt(raw, fallback);
        return parsed >= 1 && parsed <= 65535 ? parsed : fallback;
    }

    private static int parsePositiveInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : fallback;
        }
        if (raw instanceof String stringValue && !stringValue.isBlank()) {
            try {
                int parsed = Integer.parseInt(stringValue.trim());
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String sanitizeSessionTime(String raw, String fallback) {
        String candidate = sanitizeText(raw, fallback).toUpperCase(Locale.US);
        return candidate.matches("^\\d{2}:\\d{2}:\\d{2}$") ? candidate : fallback;
    }
}

