package com.insoftu.thefix.client;

public final class TheFixClientApplication {
    private static final String MODULE_NAME = "TheFixClient";

    private TheFixClientApplication() {
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
    }

    static String startupMessage() {
        return MODULE_NAME + " is ready for FIX client implementation.";
    }
}

