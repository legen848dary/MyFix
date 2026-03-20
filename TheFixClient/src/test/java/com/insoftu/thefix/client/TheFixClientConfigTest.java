package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientConfigTest {

    @Test
    void parsePortUsesDefaultForBlankOrInvalidValues() {
        assertEquals(TheFixClientConfig.DEFAULT_PORT, TheFixClientConfig.parsePort(null));
        assertEquals(TheFixClientConfig.DEFAULT_PORT, TheFixClientConfig.parsePort(""));
        assertEquals(TheFixClientConfig.DEFAULT_PORT, TheFixClientConfig.parsePort("abc"));
        assertEquals(TheFixClientConfig.DEFAULT_PORT, TheFixClientConfig.parsePort("70000"));
    }

    @Test
    void parsePortAcceptsValidTcpPorts() {
        assertEquals(8088, TheFixClientConfig.parsePort("8088"));
    }

    @Test
    void toFixDemoClientConfigCarriesFixSessionSettings() {
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "127.0.0.1",
                9880,
                "FIX.4.4",
                "HSBC_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                "logs/thefixclient/test-quickfixj",
                false
        );

        assertEquals("127.0.0.1", config.toFixDemoClientConfig().host());
        assertEquals(9880, config.toFixDemoClientConfig().port());
        assertEquals("HSBC_TRDR01", config.toFixDemoClientConfig().senderCompId());
        assertEquals("LLEXSIM", config.toFixDemoClientConfig().targetCompId());
    }
}

