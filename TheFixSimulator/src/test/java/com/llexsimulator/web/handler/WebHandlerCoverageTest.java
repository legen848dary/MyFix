package com.llexsimulator.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llexsimulator.disruptor.DisruptorPipeline;
import com.llexsimulator.engine.FixConnection;
import com.llexsimulator.engine.OrderSessionRegistry;
import com.llexsimulator.engine.SessionDiagnosticsSnapshot;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.metrics.MetricsRegistry;
import com.llexsimulator.web.RestApiRouter;
import com.llexsimulator.web.dto.FillProfileDto;
import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebHandlerCoverageTest {

    @Test
    void healthHandlerReturnsJsonPayload() {
        OrderSessionRegistry registry = mock(OrderSessionRegistry.class);
        DisruptorPipeline pipeline = mock(DisruptorPipeline.class);
        when(registry.activeCount()).thenReturn(3);
        when(pipeline.getRemainingCapacity()).thenReturn(99L);

        CapturedResponse response = capturedResponse();
        RoutingContext ctx = response.context();

        new HealthHandler(registry, pipeline).get().handle(ctx);

        assertEquals("application/json", response.headerValue());
        assertEquals("{\"status\":\"UP\",\"fixSessions\":3,\"disruptorRemainingCapacity\":99}", response.body());
    }

    @Test
    void statisticsHandlerGetAndResetWriteJsonAndHandleFailures() throws Exception {
        MetricsRegistry registry = new MetricsRegistry();
        FillProfileManager profileManager = new FillProfileManager();
        ObjectMapper mapper = new ObjectMapper();
        StatisticsHandler handler = new StatisticsHandler(registry, profileManager, mapper);

        registry.incrementOrdersReceived();
        registry.incrementFills();
        registry.incrementRejects();
        registry.incrementCancels();
        registry.recordLatency(80_000L);
        registry.recordStageLatencies(10_000L, 11_000L, 12_000L, 20_000L, 30_000L, 40_000L, 50_000L);
        registry.recordOutboundLatencies(50_000L, 60_000L);
        registry.snapshot();
        profileManager.activate("price-improvement");

        CapturedResponse success = capturedResponse();
        handler.get().handle(success.context());
        assertTrue(success.body().contains("\"ordersReceived\":1"));
        assertTrue(success.body().contains("\"p50LatencyUs\":"));
        assertTrue(success.body().contains("\"p75LatencyUs\":"));
        assertTrue(success.body().contains("\"maxLatencyUs\":"));
        assertTrue(success.body().contains("\"preValidationQueueP90LatencyUs\":"));
        assertTrue(success.body().contains("\"ingressPublishP90LatencyUs\":"));
        assertTrue(success.body().contains("\"disruptorQueueP90LatencyUs\":"));
        assertTrue(success.body().contains("\"metricsPublishP90LatencyUs\":"));
        assertTrue(success.body().contains("\"outboundQueueP90LatencyUs\":"));
        assertTrue(success.body().contains("\"outboundSendP90LatencyUs\":"));
        assertTrue(success.body().contains("\"activeProfile\":\"price-improvement\""));

        CapturedResponse reset = capturedResponse();
        handler.reset().handle(reset.context());
        assertTrue(reset.body().contains("\"ordersReceived\":0"));
        assertEquals(0L, registry.getOrdersReceived());

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        StatisticsHandler failing = new StatisticsHandler(registry, profileManager, failingMapper);
        RoutingContext failedCtx = mock(RoutingContext.class);
        HttpServerResponse failedResponse = mock(HttpServerResponse.class);
        when(failedCtx.response()).thenReturn(failedResponse);
        failing.get().handle(failedCtx);
        verify(failedCtx).fail(eq(500), any(RuntimeException.class));
        failing.reset().handle(failedCtx);
        verify(failedCtx, org.mockito.Mockito.times(2)).fail(eq(500), any(RuntimeException.class));
    }

    @Test
    void fillProfileHandlerSupportsCrudAndErrorPaths() throws Exception {
        FillProfileManager manager = new FillProfileManager();
        ObjectMapper mapper = new ObjectMapper();
        FillProfileHandler handler = new FillProfileHandler(manager, mapper);

        CapturedResponse listResponse = capturedResponse();
        handler.list().handle(listResponse.context());
        assertTrue(listResponse.body().contains("immediate-full-fill"));

        String json = mapper.writeValueAsString(new FillProfileDto(
                "custom-delete-me", "desc", "REJECT", 0, 0, 0, "SIMULATOR_REJECT", 0, 0, 0, 0, 0));
        CapturedResponse saveResponse = capturedResponseWithBody(json);
        handler.createOrUpdate().handle(saveResponse.context());
        assertEquals(201, saveResponse.statusCode());
        assertTrue(saveResponse.body().contains("custom-delete-me"));

        CapturedResponse activateResponse = capturedResponse();
        when(activateResponse.context().pathParam("name")).thenReturn("custom-delete-me");
        handler.activate().handle(activateResponse.context());
        assertTrue(activateResponse.body().contains("activated"));

        CapturedResponse missingActivate = capturedResponse();
        when(missingActivate.context().pathParam("name")).thenReturn("missing");
        handler.activate().handle(missingActivate.context());
        assertEquals(404, missingActivate.statusCode());

        CapturedResponse deleteResponse = capturedResponse();
        when(deleteResponse.context().pathParam("name")).thenReturn("custom-delete-me");
        handler.delete().handle(deleteResponse.context());
        assertEquals(200, deleteResponse.statusCode());
        assertEquals("{\"deleted\":true}", deleteResponse.body());

        CapturedResponse deleteMissing = capturedResponse();
        when(deleteMissing.context().pathParam("name")).thenReturn("missing");
        handler.delete().handle(deleteMissing.context());
        assertEquals(404, deleteMissing.statusCode());

        FillProfileManager throwingManager = mock(FillProfileManager.class);
        FillProfileHandler badJsonHandler = new FillProfileHandler(throwingManager, mapper);
        CapturedResponse badBody = capturedResponseWithBody("{not-json}");
        badJsonHandler.createOrUpdate().handle(badBody.context());
        verify(badBody.context()).fail(eq(400), any(Exception.class));

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("map-fail"));
        FillProfileHandler failingList = new FillProfileHandler(manager, failingMapper);
        RoutingContext failingCtx = mock(RoutingContext.class);
        HttpServerResponse failingResponse = mock(HttpServerResponse.class);
        when(failingCtx.response()).thenReturn(failingResponse);
        failingList.list().handle(failingCtx);
        verify(failingCtx).fail(eq(500), any(RuntimeException.class));

        FillProfileManager runtimeFailManager = mock(FillProfileManager.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(runtimeFailManager).activate("explode");
        FillProfileHandler runtimeFailActivate = new FillProfileHandler(runtimeFailManager, mapper);
        CapturedResponse runtimeActivate = capturedResponse();
        when(runtimeActivate.context().pathParam("name")).thenReturn("explode");
        runtimeFailActivate.activate().handle(runtimeActivate.context());
        verify(runtimeActivate.context()).fail(eq(500), any(RuntimeException.class));
    }

    @Test
    void sessionHandlerListsRecentDisconnectsDisconnectsAndHandlesSerializationFailures() throws Exception {
        OrderSessionRegistry registry = mock(OrderSessionRegistry.class);
        ObjectMapper mapper = new ObjectMapper();
        SessionHandler handler = new SessionHandler(registry, mapper);

        Session session = mock(Session.class);
        FixConnection connection = mock(FixConnection.class);
        SessionDiagnosticsSnapshot snapshot = new SessionDiagnosticsSnapshot(
                1L, 2L, "FIX.4.4:LLEXSIM->CLIENT1#1", "FIX.4.4", "LLEXSIM", "CLIENT1",
                true, 10L, 1, 2, 3, 4L, 5L, 6L, 7L, 8L, 9L, 10L, false,
                "D", "FILL", "NONE", 123L, 1L, 2L, 3L, 4L, 5L, 6L, List.of("event"));
        when(connection.snapshot()).thenReturn(snapshot);
        when(connection.sessionKey()).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#1");
        when(connection.session()).thenReturn(session);
        when(registry.getAllConnections()).thenReturn(List.of(connection));
        when(registry.getRecentDisconnects(16)).thenReturn(List.of(snapshot));
        when(registry.getRecentDisconnects(5)).thenReturn(List.of(snapshot));

        CapturedResponse list = capturedResponse();
        handler.list().handle(list.context());
        assertTrue(list.body().contains("FIX.4.4:LLEXSIM->CLIENT1#1"));

        CapturedResponse recentDefault = capturedResponse();
        when(recentDefault.request().getParam("limit")).thenReturn(" ");
        handler.recentDisconnects().handle(recentDefault.context());
        assertTrue(recentDefault.body().contains("recentEvents"));

        CapturedResponse recentInvalid = capturedResponse();
        when(recentInvalid.request().getParam("limit")).thenReturn("NaN");
        handler.recentDisconnects().handle(recentInvalid.context());
        assertTrue(recentInvalid.body().contains("recentEvents"));

        CapturedResponse recentFive = capturedResponse();
        when(recentFive.request().getParam("limit")).thenReturn("5");
        handler.recentDisconnects().handle(recentFive.context());
        verify(registry).getRecentDisconnects(5);

        CapturedResponse disconnectFound = capturedResponse();
        when(disconnectFound.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#1");
        handler.disconnect().handle(disconnectFound.context());
        assertEquals(200, disconnectFound.statusCode());
        verify(session).requestDisconnect();

        when(session.resetSequenceNumbers()).thenReturn(42L);
        CapturedResponse resetFound = capturedResponse();
        when(resetFound.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#1");
        handler.resetSequenceNumbers().handle(resetFound.context());
        assertEquals(200, resetFound.statusCode());
        assertTrue(resetFound.body().contains("\"reset\":true"));

        Session laterSession = mock(Session.class);
        FixConnection nonMatchingConnection = mock(FixConnection.class);
        FixConnection matchingLaterConnection = mock(FixConnection.class);
        when(nonMatchingConnection.sessionKey()).thenReturn("FIX.4.4:LLEXSIM->OTHER#2");
        when(matchingLaterConnection.sessionKey()).thenReturn("FIX.4.4:LLEXSIM->TARGET#3");
        when(matchingLaterConnection.session()).thenReturn(laterSession);
        when(laterSession.resetSequenceNumbers()).thenReturn(-1L);
        when(registry.getAllConnections()).thenReturn(List.of(nonMatchingConnection, matchingLaterConnection));

        CapturedResponse disconnectFoundLater = capturedResponse();
        when(disconnectFoundLater.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->TARGET#3");
        handler.disconnect().handle(disconnectFoundLater.context());
        assertEquals(200, disconnectFoundLater.statusCode());
        verify(laterSession).requestDisconnect();

        CapturedResponse resetRejected = capturedResponse();
        when(resetRejected.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->TARGET#3");
        handler.resetSequenceNumbers().handle(resetRejected.context());
        assertEquals(409, resetRejected.statusCode());
        assertTrue(resetRejected.body().contains("\"reset\":false"));

        when(connection.session()).thenReturn(null);
        when(registry.getAllConnections()).thenReturn(List.of(connection));
        CapturedResponse disconnectMissingSession = capturedResponse();
        when(disconnectMissingSession.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#1");
        handler.disconnect().handle(disconnectMissingSession.context());
        assertEquals(404, disconnectMissingSession.statusCode());

        CapturedResponse resetMissingSession = capturedResponse();
        when(resetMissingSession.context().pathParam("id")).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#1");
        handler.resetSequenceNumbers().handle(resetMissingSession.context());
        assertEquals(409, resetMissingSession.statusCode());

        CapturedResponse disconnectMissing = capturedResponse();
        when(registry.getAllConnections()).thenReturn(List.of());
        when(disconnectMissing.context().pathParam("id")).thenReturn("missing");
        handler.disconnect().handle(disconnectMissing.context());
        assertEquals(404, disconnectMissing.statusCode());

        CapturedResponse resetMissing = capturedResponse();
        when(resetMissing.context().pathParam("id")).thenReturn("missing");
        handler.resetSequenceNumbers().handle(resetMissing.context());
        assertEquals(404, resetMissing.statusCode());

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json-fail"));
        SessionHandler failing = new SessionHandler(registry, failingMapper);
        RoutingContext failingCtx = mock(RoutingContext.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(failingCtx.response()).thenReturn(response);
        when(failingCtx.request()).thenReturn(request);
        when(registry.getRecentDisconnects(16)).thenReturn(List.of(snapshot));
        failing.list().handle(failingCtx);
        failing.recentDisconnects().handle(failingCtx);
        verify(failingCtx, org.mockito.Mockito.times(2)).fail(eq(500), any(RuntimeException.class));
    }

    @Test
    void sessionHandlerParseLimitFallsBackForNull() throws Exception {
        Method parseLimit = SessionHandler.class.getDeclaredMethod("parseLimit", String.class);
        parseLimit.setAccessible(true);

        assertEquals(16, parseLimit.invoke(null, new Object[]{null}));
    }

    @Test
    void restApiRouterServesSettingsRoutes() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            Router router = RestApiRouter.create(
                    vertx,
                    new MetricsRegistry(),
                    new OrderSessionRegistry(false),
                    new FillProfileManager(),
                    mock(DisruptorPipeline.class)
            );
            server = vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(0)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> settingsResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.actualPort() + "/settings"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, settingsResponse.statusCode());
            assertTrue(settingsResponse.body().contains("LLExSimulator"));

            HttpResponse<Void> trailingSlashResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.actualPort() + "/settings/"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );
            assertEquals(308, trailingSlashResponse.statusCode());
            assertNotNull(trailingSlashResponse.headers().firstValue("location").orElse(null));
            assertEquals("/settings", trailingSlashResponse.headers().firstValue("location").orElseThrow());
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static CapturedResponse capturedResponse() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(ctx.response()).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(response.putHeader(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(response);
        when(response.setStatusCode(org.mockito.ArgumentMatchers.anyInt())).thenReturn(response);
        return new CapturedResponse(ctx, request, response);
    }

    private static CapturedResponse capturedResponseWithBody(String body) {
        CapturedResponse captured = capturedResponse();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(Buffer.buffer(body));
            return null;
        }).when(captured.request()).bodyHandler(any());
        return captured;
    }

    private record CapturedResponse(RoutingContext context, HttpServerRequest request, HttpServerResponse response) {
        private String body() {
            org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(response, never()).end();
            verify(response).end(captor.capture());
            return captor.getValue();
        }

        private String headerValue() {
            org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(response).putHeader(eq("Content-Type"), valueCaptor.capture());
            return valueCaptor.getValue();
        }

        private int statusCode() {
            org.mockito.ArgumentCaptor<Integer> captor = org.mockito.ArgumentCaptor.forClass(Integer.class);
            verify(response, org.mockito.Mockito.atLeast(0)).setStatusCode(captor.capture());
            return captor.getAllValues().isEmpty() ? 200 : captor.getAllValues().getLast();
        }
    }
}
