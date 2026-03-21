package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientApplicationTest {

    @Test
    void startupMessagePointsToTheWebWorkstationUrl() {
        assertEquals(
                "TheFixClient trader workstation is available at http://localhost:8081",
                TheFixClientApplication.startupMessage(new TheFixClientConfig(
                        "0.0.0.0",
                        8081,
                        "localhost",
                        9880,
                        "FIX.4.4",
                        "THEFIX_TRDR01",
                        "LLEXSIM",
                        "FIX.4.4",
                        30,
                        5,
                        25,
                        "logs/thefixclient/test-quickfixj",
                        false
                ))
        );
    }
}

