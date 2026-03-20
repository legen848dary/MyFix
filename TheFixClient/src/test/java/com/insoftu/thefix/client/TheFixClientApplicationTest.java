package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientApplicationTest {

    @Test
    void startupMessageDescribesTheClientModule() {
        assertEquals(
                "TheFixClient is ready for FIX client implementation.",
                TheFixClientApplication.startupMessage()
        );
    }
}

