package com.insoftu.thefix.client;

import com.llexsimulator.SimulatorBootstrap;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientLiveIntegrationTest {

    @TempDir
    Path tempDir;

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

    @Test
    @Timeout(30)
    void burstBulkFlowCanCompleteAFiniteRun() throws Exception {
        state = createLiveState();

        state.startOrderFlow(new JsonObject()
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("currency", "USD")
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 10)
                .put("price", 100.25)
                .put("timeInForce", "DAY")
                .put("orderType", "LIMIT")
                .put("priceType", "PER_UNIT")
                .put("bulkMode", "BURST")
                .put("totalOrders", 6)
                .put("burstSize", 2)
                .put("burstIntervalMs", 200));

        assertTrue(waitFor(() -> state.snapshot().getJsonObject("session").getBoolean("connected"), Duration.ofSeconds(10)),
                "Expected burst bulk flow to establish a FIX session automatically");
        assertTrue(waitFor(() -> state.snapshot().getJsonObject("kpis").getLong("sentOrders") >= 6L, Duration.ofSeconds(10)),
                "Expected burst bulk flow to route the requested finite run");
        assertTrue(waitFor(() -> !state.snapshot().getJsonObject("session").getBoolean("autoFlowActive"), Duration.ofSeconds(10)),
                "Expected burst bulk flow to stop itself after reaching totalOrders");
    }

    @Test
    @Timeout(30)
    void workstationCanAmendAndCancelOrdersFromTheBlotter() throws Exception {
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

        assertTrue(waitFor(() -> !state.snapshot().getJsonArray("recentOrders").isEmpty(), Duration.ofSeconds(10)),
                "Expected the blotter to contain the submitted order");

        JsonObject submittedOrder = state.snapshot().getJsonArray("recentOrders").getJsonObject(0);
        String originalClOrdId = submittedOrder.getString("clOrdId");

        JsonObject amendResponse = state.amendBlotterOrder(new JsonObject()
                .put("clOrdId", originalClOrdId)
                .put("quantity", 120)
                .put("price", 101.25));
        assertTrue(amendResponse.getJsonObject("actionResult").getBoolean("success"));
        assertTrue(waitFor(() -> {
            JsonObject order = state.snapshot().getJsonArray("recentOrders").getJsonObject(0);
            return Integer.toString(120).equals(order.getValue("quantity").toString())
                    && "101.25".equals(order.getString("limitPrice"));
        }, Duration.ofSeconds(10)), "Expected the blotter order to reflect the amended quantity and price");

        String amendedClOrdId = state.snapshot().getJsonArray("recentOrders").getJsonObject(0).getString("clOrdId");
        JsonObject cancelResponse = state.cancelBlotterOrder(new JsonObject().put("clOrdId", amendedClOrdId));
        assertTrue(cancelResponse.getJsonObject("actionResult").getBoolean("success"));
        assertTrue(waitFor(() -> {
            for (Object item : state.snapshot().getJsonArray("recentOrders")) {
                JsonObject order = (JsonObject) item;
                if (amendedClOrdId.equals(order.getString("clOrdId"))) {
                    return false;
                }
            }
            return true;
        }, Duration.ofSeconds(10)), "Expected the amended order to be removed from the blotter after cancellation");
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
                "THEFIX_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                2,
                10,
                "build/test-live-quickfixj",
                false
        );
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(config);
        store.updateStoragePath(tempDir.resolve("profiles").toString());
        return new TheFixClientWorkbenchState(config, store);
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

