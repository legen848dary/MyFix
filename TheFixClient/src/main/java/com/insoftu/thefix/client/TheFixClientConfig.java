package com.insoftu.thefix.client;

record TheFixClientConfig(String host, int port) {
    static final int DEFAULT_PORT = 8081;
    private static final String PORT_PROPERTY = "thefix.client.port";
    private static final String PORT_ENVIRONMENT = "THEFIX_CLIENT_PORT";

    static TheFixClientConfig fromSystemProperties() {
        return new TheFixClientConfig("0.0.0.0", resolvePort());
    }

    String localUrl() {
        return "http://localhost:" + port;
    }

    static int resolvePort() {
        String rawValue = System.getProperty(PORT_PROPERTY);
        if (rawValue == null || rawValue.isBlank()) {
            rawValue = System.getenv(PORT_ENVIRONMENT);
        }
        return parsePort(rawValue);
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
}

