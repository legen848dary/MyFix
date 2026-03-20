package com.insoftu.thefix.simulator;

public final class TheFixSimulatorApplication {
    private static final String MODULE_NAME = "TheFixSimulator";

    private TheFixSimulatorApplication() {
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
    }

    static String startupMessage() {
        return MODULE_NAME + " is ready to receive the simulator codebase.";
    }
}

