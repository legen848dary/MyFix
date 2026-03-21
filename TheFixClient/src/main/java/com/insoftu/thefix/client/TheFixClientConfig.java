package com.insoftu.thefix.client;

import com.llexsimulator.client.FixDemoClientConfig;

record TheFixClientConfig(
        String host,
        int port,
        String fixHost,
        int fixPort,
        String beginString,
        String senderCompId,
        String targetCompId,
        String defaultApplVerId,
        int heartBtIntSec,
        int reconnectIntervalSec,
        int defaultRatePerSecond,
        String quickFixLogDir,
        boolean rawMessageLoggingEnabled
) {
    static final int DEFAULT_PORT = 8081;
    private static final String WEB_PORT_PROPERTY = "thefix.client.port";
    private static final String WEB_PORT_ENVIRONMENT = "THEFIX_CLIENT_PORT";

    static TheFixClientConfig fromSystemProperties() {
        return new TheFixClientConfig(
                "0.0.0.0",
                resolvePort(),
                resolveString("thefix.fix.host", "THEFIX_FIX_HOST", "fix.demo.host", "FIX_CLIENT_HOST", "localhost"),
                resolvePositiveInt("thefix.fix.port", "THEFIX_FIX_PORT", "fix.demo.port", "FIX_CLIENT_PORT", 9880),
                resolveString("thefix.fix.beginString", "THEFIX_FIX_BEGIN_STRING", "fix.demo.beginString", "FIX_CLIENT_BEGIN_STRING", "FIX.4.4"),
                resolveString("thefix.fix.senderCompId", "THEFIX_FIX_SENDER_COMP_ID", "fix.demo.senderCompId", "FIX_CLIENT_SENDER_COMP_ID", "THEFIX_TRDR01"),
                resolveString("thefix.fix.targetCompId", "THEFIX_FIX_TARGET_COMP_ID", "fix.demo.targetCompId", "FIX_CLIENT_TARGET_COMP_ID", "LLEXSIM"),
                resolveString("thefix.fix.defaultApplVerId", "THEFIX_FIX_DEFAULT_APPL_VER_ID", "fix.demo.defaultApplVerId", "FIX_CLIENT_DEFAULT_APPL_VER_ID", "FIX.4.4"),
                resolvePositiveInt("thefix.fix.heartBtInt", "THEFIX_FIX_HEARTBTINT", "fix.demo.heartBtInt", "FIX_CLIENT_HEARTBTINT", 30),
                resolvePositiveInt("thefix.fix.reconnectIntervalSec", "THEFIX_FIX_RECONNECT_INTERVAL_SEC", "fix.demo.reconnectIntervalSec", "FIX_CLIENT_RECONNECT_INTERVAL_SEC", 5),
                resolvePositiveInt("thefix.fix.defaultRatePerSecond", "THEFIX_FIX_DEFAULT_RATE", "fix.demo.rate", "FIX_DEMO_RATE", 25),
                resolveString("thefix.fix.logDir", "THEFIX_FIX_LOG_DIR", "fix.demo.logDir", null, "logs/thefixclient/quickfixj"),
                resolveBoolean("thefix.fix.rawLoggingEnabled", "THEFIX_FIX_RAW_LOGGING_ENABLED", "fix.demo.rawLoggingEnabled", "FIX_CLIENT_RAW_LOGGING_ENABLED", false)
        );
    }

    String localUrl() {
        return "http://localhost:" + port;
    }

    FixDemoClientConfig toFixDemoClientConfig() {
        return new FixDemoClientConfig(
                fixHost,
                fixPort,
                beginString,
                senderCompId,
                targetCompId,
                defaultApplVerId,
                heartBtIntSec,
                reconnectIntervalSec,
                defaultRatePerSecond,
                "AAPL",
                '1',
                100.0d,
                100.25d,
                quickFixLogDir,
                rawMessageLoggingEnabled
        );
    }

    static int resolvePort() {
        return parsePort(firstNonBlank(
                System.getProperty(WEB_PORT_PROPERTY),
                System.getenv(WEB_PORT_ENVIRONMENT)
        ));
    }

    static int parsePort(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            if (parsed < 1 || parsed > 65535) {
                return DEFAULT_PORT;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    private static String resolveString(String primaryProperty, String primaryEnv, String fallbackProperty, String fallbackEnv, String defaultValue) {
        String value = firstNonBlank(
                System.getProperty(primaryProperty),
                primaryEnv == null ? null : System.getenv(primaryEnv),
                fallbackProperty == null ? null : System.getProperty(fallbackProperty),
                fallbackEnv == null ? null : System.getenv(fallbackEnv)
        );
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int resolvePositiveInt(String primaryProperty, String primaryEnv, String fallbackProperty, String fallbackEnv, int defaultValue) {
        String raw = firstNonBlank(
                System.getProperty(primaryProperty),
                primaryEnv == null ? null : System.getenv(primaryEnv),
                fallbackProperty == null ? null : System.getProperty(fallbackProperty),
                fallbackEnv == null ? null : System.getenv(fallbackEnv)
        );
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static boolean resolveBoolean(String primaryProperty, String primaryEnv, String fallbackProperty, String fallbackEnv, boolean defaultValue) {
        String raw = firstNonBlank(
                System.getProperty(primaryProperty),
                primaryEnv == null ? null : System.getenv(primaryEnv),
                fallbackProperty == null ? null : System.getProperty(fallbackProperty),
                fallbackEnv == null ? null : System.getenv(fallbackEnv)
        );
        return raw == null || raw.isBlank() ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

