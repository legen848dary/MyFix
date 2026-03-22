package com.insoftu.thefix.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
                false
        ));
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        for (String path : List.of("/", "/home", "/order", "/orders", "/blotter", "/settings")) {
            HttpResponse<String> response = send(client, path);
            assertEquals(200, response.statusCode(), "Unexpected status for " + path);
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/html"), "Expected HTML for " + path);
            assertTrue(response.body().contains("<div id=\"app\"></div>"), "Expected SPA shell for " + path);
            assertTrue(response.body().contains("<base href=\"/\"/>"), "Expected base href for " + path);
            assertTrue(response.body().contains("app.js?v="), "Expected app.js reference for " + path);
        }

        HttpResponse<String> apiHealth = send(client, "/api/health");
        assertEquals(200, apiHealth.statusCode());
        assertTrue(apiHealth.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(apiHealth.body().contains("\"status\":\"UP\""));

        HttpResponse<String> appJs = send(client, "/app.js");
        assertEquals(200, appJs.statusCode());
        assertTrue(appJs.body().contains("createApp({"));

        HttpResponse<String> trailingSlashRedirect = send(client, "/blotter/");
        assertEquals(308, trailingSlashRedirect.statusCode());
        assertEquals("/blotter", trailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> ordersTrailingSlashRedirect = send(client, "/orders/");
        assertEquals(308, ordersTrailingSlashRedirect.statusCode());
        assertEquals("/orders", ordersTrailingSlashRedirect.headers().firstValue("location").orElse(""));

        HttpResponse<String> missingAsset = send(client, "/missing-does-not-exist.js");
        assertEquals(404, missingAsset.statusCode());
    }

    private HttpResponse<String> send(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.actualPort() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

