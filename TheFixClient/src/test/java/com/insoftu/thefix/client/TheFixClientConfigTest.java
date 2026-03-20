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
}

