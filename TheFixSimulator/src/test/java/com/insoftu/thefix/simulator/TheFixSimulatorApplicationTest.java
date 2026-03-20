package com.insoftu.thefix.simulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixSimulatorApplicationTest {

    @Test
    void startupMessageDescribesTheSimulatorModule() {
        assertEquals(
                "TheFixSimulator is ready to receive the simulator codebase.",
                TheFixSimulatorApplication.startupMessage()
        );
    }
}

