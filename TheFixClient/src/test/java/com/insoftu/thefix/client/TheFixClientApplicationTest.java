package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientApplicationTest {

    @Test
    void startupMessagePointsToTheWebWorkstationUrl() {
        assertEquals(
                "TheFixClient trader workstation is available at http://localhost:8081",
                TheFixClientApplication.startupMessage(new TheFixClientConfig("0.0.0.0", 8081))
        );
    }
}

