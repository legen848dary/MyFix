package com.insoftu.thefix.client;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

final class TheFixClientServer {
    private static final Logger log = LoggerFactory.getLogger(TheFixClientServer.class);

    private final TheFixClientConfig config;
    private final TheFixClientWorkbenchState workbenchState;
    private final Vertx vertx;

    private HttpServer httpServer;

    TheFixClientServer(TheFixClientConfig config) {
        this(config, new TheFixClientWorkbenchState(config));
    }

    TheFixClientServer(TheFixClientConfig config, TheFixClientWorkbenchState workbenchState) {
        this.config = config;
        this.workbenchState = workbenchState;
        this.vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    }

    public void start() {
        Router router = Router.router(vertx);
        router.route("/api/*").handler(BodyHandler.create());

        router.get("/api/health").handler(ctx -> writeJson(ctx.response(), new JsonObject()
                .put("status", "UP")
                .put("application", "TheFixClient")
                .put("mode", "live-fix-workstation")
                .put("port", config.port())));

        router.get("/api/overview").handler(ctx -> writeJson(ctx.response(), workbenchState.snapshot()));
        router.get("/api/fix-metadata").handler(ctx -> writeJson(ctx.response(), workbenchState.fixMetadataSnapshot()));
        router.get("/api/settings").handler(ctx -> writeJson(ctx.response(), workbenchState.settingsSnapshot()));
        router.get("/api/templates").handler(ctx -> writeJson(ctx.response(), workbenchState.templateSnapshot()));
        router.post("/api/session/connect").handler(ctx -> writeJson(ctx.response(), workbenchState.connect()));
        router.post("/api/session/disconnect").handler(ctx -> writeJson(ctx.response(), workbenchState.disconnect()));
        router.post("/api/session/pulse-test").handler(ctx -> writeJson(ctx.response(), workbenchState.pulseTest()));
        router.post("/api/settings/profiles/save").handler(ctx -> writeJson(ctx.response(), workbenchState.saveSettingsProfile(bodyJson(ctx))));
        router.post("/api/settings/profiles/activate").handler(ctx -> writeJson(ctx.response(), workbenchState.activateSettingsProfile(bodyJson(ctx))));
        router.post("/api/settings/storage-path").handler(ctx -> writeJson(ctx.response(), workbenchState.updateSettingsStoragePath(bodyJson(ctx))));
        router.post("/api/templates/save").handler(ctx -> writeJson(ctx.response(), workbenchState.saveMessageTemplate(bodyJson(ctx))));
        router.post("/api/order-ticket/preview").handler(ctx -> writeJson(ctx.response(), workbenchState.previewOrder(bodyJson(ctx))));
        router.post("/api/order-ticket/send").handler(ctx -> writeJson(ctx.response(), workbenchState.sendOrder(bodyJson(ctx))));
        router.post("/api/order-flow/start").handler(ctx -> writeJson(ctx.response(), workbenchState.startOrderFlow(bodyJson(ctx))));
        router.post("/api/order-flow/stop").handler(ctx -> writeJson(ctx.response(), workbenchState.stopOrderFlow()));

        router.getWithRegex("^/(home|order|orders|blotter|settings)$").handler(ctx -> ctx.reroute("/index.html"));
        router.getWithRegex("^/(home|order|orders|blotter|settings)/$").handler(ctx -> ctx.response()
                .setStatusCode(308)
                .putHeader("location", ctx.request().path().substring(0, ctx.request().path().length() - 1))
                .end());

        router.route().handler(StaticHandler.create("web")
                .setCachingEnabled(false)
                .setIndexPage("index.html"));

        HttpServerOptions options = new HttpServerOptions()
                .setHost(config.host())
                .setPort(config.port())
                .setTcpNoDelay(true)
                .setReusePort(true);

        try {
            httpServer = vertx.createHttpServer(options)
                    .requestHandler(router)
                    .listen()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            log.info("TheFixClient workstation server started on port {}", httpServer.actualPort());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start TheFixClient web server", exception);
        }
    }

    public void stop() {
        try {
            workbenchState.close();
        } catch (Exception exception) {
            log.warn("Error while stopping TheFixClient FIX state", exception);
        }

        try {
            if (httpServer != null) {
                httpServer.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                httpServer = null;
            }
        } catch (Exception exception) {
            log.warn("Error while stopping TheFixClient HTTP server", exception);
        }

        try {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Error while stopping TheFixClient Vert.x runtime", exception);
        }
    }

    int actualPort() {
        return httpServer == null ? config.port() : httpServer.actualPort();
    }

    private static void writeJson(io.vertx.core.http.HttpServerResponse response, JsonObject payload) {
        response.putHeader("content-type", "application/json")
                .end(payload.encode());
    }

    private static JsonObject bodyJson(io.vertx.ext.web.RoutingContext context) {
        return context.body() == null ? new JsonObject() : context.body().asJsonObject();
    }
}


