package com.insoftu.thefix.client;

import com.llexsimulator.SimulatorBootstrap;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientLiveIntegrationTest {

    private SimulatorBootstrap simulatorBootstrap;
    private TheFixClientWorkbenchState state;

    @AfterEach
    void cleanup() {
        if (state != null) {
            state.close();
        }
        if (simulatorBootstrap != null) {
            simulatorBootstrap.stop();
        }
        System.clearProperty("fix.port");
        System.clearProperty("web.port");
    }

    @Test
    @Timeout(30)
    void workstationCanLogOnAndRouteALiveOrderToTheSimulator() throws Exception {
        state = createLiveState();

        state.connect();
        assertTrue(waitFor(() -> state.snapshot().getJsonObject("session").getBoolean("connected"), Duration.ofSeconds(10)),
                "Expected TheFixClient to log on to the simulator acceptor");

        state.sendOrder(new JsonObject()
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 100)
                .put("price", 100.25)
                .put("timeInForce", "DAY"));

        assertTrue(waitFor(() -> state.snapshot().getJsonObject("kpis").getLong("executionReports") > 0L, Duration.ofSeconds(10)),
                "Expected at least one execution report after routing a live order");

        JsonObject snapshot = state.snapshot();
        assertTrue(snapshot.getJsonObject("session").getBoolean("connected"));
        assertTrue(snapshot.getJsonObject("kpis").getLong("sentOrders") > 0L);
        assertFalse(snapshot.getJsonArray("recentOrders").isEmpty());
    }

    @Test
    @Timeout(30)
    void startingDemoFlowFromIdleAutoConnectsAndStreamsOrders() throws Exception {
        state = createLiveState();

        state.startOrderFlow(new JsonObject()
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 25)
                .put("price", 100.25)
                .put("timeInForce", "DAY")
                .put("ratePerSecond", 2));

        assertTrue(waitFor(() -> state.snapshot().getJsonObject("session").getBoolean("connected"), Duration.ofSeconds(10)),
                "Expected auto flow start to establish a FIX session automatically");
        assertTrue(waitFor(() -> state.snapshot().getJsonObject("kpis").getLong("executionReports") > 0L, Duration.ofSeconds(10)),
                "Expected execution reports after auto flow is armed from idle state");

        JsonObject snapshot = state.snapshot();
        assertTrue(snapshot.getJsonObject("session").getBoolean("connected"));
        assertTrue(snapshot.getJsonObject("session").getBoolean("autoFlowActive"));
        assertTrue(snapshot.getJsonObject("kpis").getLong("sentOrders") > 0L);
        assertFalse(snapshot.getJsonArray("recentOrders").isEmpty());
    }

    private TheFixClientWorkbenchState createLiveState() throws Exception {
        int fixPort = findFreePort();
        int webPort = findFreePort();

        System.setProperty("fix.port", Integer.toString(fixPort));
        System.setProperty("web.port", Integer.toString(webPort));

        simulatorBootstrap = new SimulatorBootstrap();
        simulatorBootstrap.start();

        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0",
                8081,
                "127.0.0.1",
                fixPort,
                "FIX.4.4",
                "HSBC_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                2,
                10,
                "build/test-live-quickfixj",
                false
        );
        return new TheFixClientWorkbenchState(config);
    }

    private static boolean waitFor(Check check, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (check.evaluate()) {
                return true;
            }
            Thread.sleep(100L);
        }
        return check.evaluate();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean evaluate() throws Exception;
    }
}

