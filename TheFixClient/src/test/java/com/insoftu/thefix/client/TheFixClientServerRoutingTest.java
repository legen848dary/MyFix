package com.insoftu.thefix.client;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixClientServerRoutingTest {

    private TheFixClientServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void servesSpaShellForCleanRoutesAndPreservesApiAndAssetResponses() throws Exception {
        Path logDir = Files.createTempDirectory("thefixclient-routing-test");
        server = new TheFixClientServer(new TheFixClientConfig(
                "127.0.0.1",
                0,
                "localhost",
                9880,
                "FIX.4.4",
                "THEFIX_TRDR01",
                "LLEXSIM",
                "FIX.4.4",
                30,
                5,
                25,
                logDir.toString(),
                false,
                480,
                1
        ));
        server.start();

        HttpClient client = HttpClient.newHttpClient();

        // Obtain a demo auth token before calling protected API endpoints.
        HttpResponse<String> loginResponse = post(client, "/api/auth/login",
                "{\"username\":\"trader1\",\"password\":\"trader1\"}", null);
        assertEquals(200, loginResponse.statusCode());
        String token = new JsonObject(loginResponse.body()).getString("token");

        for (String path : List.of("/", "/home", "/neworder", "/order", "/orders", "/blotter", "/settings", "/session-profiles", "/about", "/cucumber")) {
            HttpResponse<String> response = send(client, path, null);
            assertEquals(200, response.statusCode(), "Unexpected status for " + path);
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/html"), "Expected HTML for " + path);
            assertTrue(response.body().contains("<div id=\"app\"></div>"), "Expected SPA shell for " + path);
            assertTrue(response.body().contains("<base href=\"/\"/>"), "Expected base href for " + path);
            assertTrue(response.body().contains("app.js?v="), "Expected app.js reference for " + path);
            assertTrue(response.body().contains("overflow-y: auto;"), "Expected orders blotter vertical scroll styling in shell for " + path);
            assertTrue(response.body().contains("z-index: 70;"), "Expected elevated menu dropdown layering in shell for " + path);
        }

        HttpResponse<String> appJsFromAsset = send(client, "/app.js", null);
        assertEquals(200, appJsFromAsset.statusCode());
        assertTrue(appJsFromAsset.body().contains("'order-input': '/home'"));
        assertTrue(appJsFromAsset.body().contains("'session-profiles': '/session-profiles'"));
        assertTrue(appJsFromAsset.body().contains("about: '/about'"));
        assertTrue(appJsFromAsset.body().contains("case '/neworder':"));
        assertTrue(appJsFromAsset.body().contains("case '/session-profiles':"));
        assertTrue(appJsFromAsset.body().contains("case '/about':"));
        assertTrue(appJsFromAsset.body().contains("'hsbc-dark'"));
        assertTrue(appJsFromAsset.body().contains("Dark - Green"));
        assertTrue(appJsFromAsset.body().contains("toggleThemeMenu"));
        assertTrue(appJsFromAsset.body().contains("toggleRefreshMenu"));
        assertTrue(appJsFromAsset.body().contains("previewStatusLabel"));
        assertTrue(appJsFromAsset.body().contains("READY_PENDING_CONNECTION"));
        assertTrue(appJsFromAsset.body().contains("reconcileSessionActionPending"));
        assertTrue(appJsFromAsset.body().contains("shouldKeepConnectPending"));
        assertTrue(appJsFromAsset.body().contains("selectedProfileQuery"));
        assertTrue(appJsFromAsset.body().contains("withSelectedProfile"));
        assertTrue(appJsFromAsset.body().contains("runtimeSessions"));
        assertTrue(appJsFromAsset.body().contains("runtime-roster"));
        assertTrue(appJsFromAsset.body().contains("toggleRuntimeSession"));
        assertTrue(appJsFromAsset.body().contains("'scenario-runner': '/cucumber'"));
        assertTrue(appJsFromAsset.body().contains("case '/cucumber':"));
        assertTrue(appJsFromAsset.body().contains("runCucumberScenarios"));
        assertTrue(appJsFromAsset.body().contains("uploadFeatureFile"));
        assertTrue(appJsFromAsset.body().contains("parseRawFixInputByDelimiter"));
        assertTrue(appJsFromAsset.body().contains("rawFixInputDraft"));

        HttpResponse<String> apiHealth = send(client, "/api/health", null);
        assertEquals(200, apiHealth.statusCode());
        assertTrue(apiHealth.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(apiHealth.body().contains("\"status\":\"UP\""));

        // Unauthenticated request must return 401.
        HttpResponse<String> unauthenticated = send(client, "/api/templates", null);
        assertEquals(401, unauthenticated.statusCode());

        HttpResponse<String> templates = send(client, "/api/templates", token);
        assertEquals(200, templates.statusCode());
        assertTrue(templates.body().contains("\"templates\""));

        HttpResponse<String> saveTemplate = post(client, "/api/templates/save", """
                {"name":"Routing test template","draft":{"messageType":"NEW_ORDER_SINGLE","symbol":"AAPL","side":"BUY","quantity":10,"price":100.25}}
                """, token);
        assertEquals(200, saveTemplate.statusCode());
        assertTrue(saveTemplate.body().contains("Routing test template"));

        HttpResponse<String> amendOrder = post(client, "/api/orders/amend", """
                {"clOrdId":"UNKNOWN","quantity":25,"price":101.10}
                """, token);
        assertEquals(200, amendOrder.statusCode());
        assertTrue(amendOrder.body().contains("\"actionResult\""));

        HttpResponse<String> cancelOrder = post(client, "/api/orders/cancel", """
                {"clOrdId":"UNKNOWN"}
                """, token);
        assertEquals(200, cancelOrder.statusCode());
        assertTrue(cancelOrder.body().contains("\"actionResult\""));

        HttpResponse<String> deleteProfile = post(client, "/api/session-profiles/delete", """
                {"name":"Default profile"}
                """, token);
        assertEquals(200, deleteProfile.statusCode());
        assertTrue(deleteProfile.body().contains("\"actionResult\""));
        assertTrue(deleteProfile.body().contains("delete-profile"));

        assertTrue(appJsFromAsset.body().contains("createApp({"));

        HttpResponse<String> trailingSlashRedirect = send(client, "/blotter/", null);
        assertEquals(308, trailingSlashRedirect.statusCode());
        assertEquals("/blotter", trailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> ordersTrailingSlashRedirect = send(client, "/orders/", null);
        assertEquals(308, ordersTrailingSlashRedirect.statusCode());
        assertEquals("/orders", ordersTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> newOrderTrailingSlashRedirect = send(client, "/neworder/", null);
        assertEquals(308, newOrderTrailingSlashRedirect.statusCode());
        assertEquals("/neworder", newOrderTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> sessionProfilesTrailingSlashRedirect = send(client, "/session-profiles/", null);
        assertEquals(308, sessionProfilesTrailingSlashRedirect.statusCode());
        assertEquals("/session-profiles", sessionProfilesTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> aboutTrailingSlashRedirect = send(client, "/about/", null);
        assertEquals(308, aboutTrailingSlashRedirect.statusCode());
        assertEquals("/about", aboutTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> cucumberTrailingSlashRedirect = send(client, "/cucumber/", null);
        assertEquals(308, cucumberTrailingSlashRedirect.statusCode());
        assertEquals("/cucumber", cucumberTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> cucumberRun = post(client, "/api/cucumber/run", """
                {"featureText":"Feature: Routing test\\n\\n  Scenario: Disconnected session verifies\\n    Given the FIX session is disconnected\\n    Then the session should not be connected"}
                """, token);
        assertEquals(200, cucumberRun.statusCode());
        assertTrue(cucumberRun.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(cucumberRun.body().contains("\"status\""));
        assertTrue(cucumberRun.body().contains("\"scenarios\""));
        assertTrue(cucumberRun.body().contains("\"totalScenarios\""));

        HttpResponse<String> missingAsset = send(client, "/missing-does-not-exist.js", null);
        assertEquals(404, missingAsset.statusCode());
    }

    @Test
    void loginWithUnsafeUsernameReturnsBadRequest() throws Exception {
        Path logDir = Files.createTempDirectory("thefixclient-routing-test-unsafe");
        // Use a permissive auth module that accepts any non-blank username so the server
        // reaches createWorkbenchState() where the path-traversal check fires, returning HTTP 400.
        UserSessionRegistry permissiveRegistry = new UserSessionRegistry(
                new TheFixClientConfig("127.0.0.1", 0, "localhost", 9880,
                        "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                        "FIX.4.4", 30, 5, 25, logDir.toString(), false, 480, 1),
                new AuthModule() {
                    @Override
                    public java.util.Optional<AuthenticatedUser> authenticate(String u, String p) {
                        if (u != null && !u.isBlank()) {
                            return java.util.Optional.of(new AuthenticatedUser(u, u, "TRADER"));
                        }
                        return java.util.Optional.empty();
                    }
                    @Override public String name() { return "permissive"; }
                }
        );
        server = new TheFixClientServer(
                new TheFixClientConfig("127.0.0.1", 0, "localhost", 9880,
                        "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                        "FIX.4.4", 30, 5, 25, logDir.toString(), false, 480, 1),
                permissiveRegistry
        );
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = post(client, "/api/auth/login",
                "{\"username\":\"../evil\",\"password\":\"x\"}", null);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    private HttpResponse<String> send(HttpClient client, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.actualPort() + path))
                .GET();
        if (token != null) {
            builder.header(TheFixClientServer.AUTH_TOKEN_HEADER, token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String payload, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.actualPort() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header(TheFixClientServer.AUTH_TOKEN_HEADER, token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

