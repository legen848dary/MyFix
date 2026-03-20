package com.insoftu.thefix.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheFixClientApplication {
    private static final Logger log = LoggerFactory.getLogger(TheFixClientApplication.class);

    private TheFixClientApplication() {
    }

    public static void main(String[] args) {
        TheFixClientConfig config = TheFixClientConfig.fromSystemProperties();
        TheFixClientServer server = new TheFixClientServer(config);

        Runtime.getRuntime().addShutdownHook(
                Thread.ofPlatform().name("thefix-client-shutdown").unstarted(server::stop)
        );

        server.start();
        log.info(startupMessage(config));
    }

    static String startupMessage(TheFixClientConfig config) {
        return "TheFixClient trader workstation is available at " + config.localUrl();
    }
}

