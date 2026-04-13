package com.insoftu.thefix.client;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class TheFixClientServer {
    private static final Logger log = LoggerFactory.getLogger(TheFixClientServer.class);
    static final String AUTH_TOKEN_HEADER = "X-Auth-Token";
    static final String AUTH_TOKEN_COOKIE = "thefix-token";

    private final TheFixClientConfig config;
    private final UserSessionRegistry sessionRegistry;
    private final Vertx vertx;

    private HttpServer httpServer;

    TheFixClientServer(TheFixClientConfig config) {
        this(config, new UserSessionRegistry(config, new DemoAuthModule()));
    }

    TheFixClientServer(TheFixClientConfig config, UserSessionRegistry sessionRegistry) {
        this.config = config;
        this.sessionRegistry = sessionRegistry;
        this.vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    }

    public void start() {
        Router router = Router.router(vertx);
        router.route("/api/*").handler(BodyHandler.create());

        // ── Public endpoints (no auth required) ──────────────────────────────
        router.get("/api/health").handler(ctx -> writeJson(ctx.response(), new JsonObject()
                .put("status", "UP")
                .put("application", "TheFixClient")
                .put("mode", "live-fix-workstation")
                .put("port", config.port())));

        router.post("/api/auth/login").handler(ctx -> {
            JsonObject body = bodyJson(ctx);
            String username = body.getString("username");
            String password = body.getString("password");
            Optional<UserSession> session = sessionRegistry.login(username, password);
            if (session.isEmpty()) {
                ctx.response().setStatusCode(401)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("error", "Invalid credentials").encode());
                return;
            }
            UserSession s = session.get();
            writeJson(ctx.response(), new JsonObject()
                    .put("token", s.token())
                    .put("username", s.user().username())
                    .put("displayName", s.user().displayName())
                    .put("role", s.user().role())
                    .put("expiresAt", s.expiresAt().toString())
                    .put("sessionTimeoutMinutes", config.sessionTimeoutMinutes()));
        });

        // ── Auth middleware: applied to all /api/* except the two above ───────
        router.route("/api/*").handler(ctx -> {
            String path = ctx.request().path();
            if (path.equals("/api/health") || path.equals("/api/auth/login")) {
                ctx.next();
                return;
            }
            Optional<UserSession> session = resolveSession(ctx);
            if (session.isEmpty()) {
                ctx.response().setStatusCode(401)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("error", "Authentication required").encode());
                return;
            }
            ctx.put("userSession", session.get());
            ctx.next();
        });

        // ── Authenticated auth endpoints ──────────────────────────────────────
        router.post("/api/auth/logout").handler(ctx -> {
            String token = resolveToken(ctx);
            sessionRegistry.logout(token);
            writeJson(ctx.response(), new JsonObject().put("loggedOut", true));
        });

        router.get("/api/auth/me").handler(ctx -> {
            UserSession s = ctx.get("userSession");
            writeJson(ctx.response(), new JsonObject()
                    .put("username", s.user().username())
                    .put("displayName", s.user().displayName())
                    .put("role", s.user().role())
                    .put("expiresAt", s.expiresAt().toString()));
        });

        // ── Authenticated workbench API ───────────────────────────────────────
        router.get("/api/overview").handler(ctx -> {
            TheFixClientWorkbenchState ws = workbench(ctx);
            writeJson(ctx.response(), ws.snapshot(ctx.request().getParam("profileName")));
        });
        router.get("/api/fix-metadata").handler(ctx -> writeJson(ctx.response(), workbench(ctx).fixMetadataSnapshot()));
        router.get("/api/settings").handler(ctx -> writeJson(ctx.response(), workbench(ctx).settingsSnapshot()));
        router.get("/api/session-profiles").handler(ctx -> writeJson(ctx.response(), workbench(ctx).sessionProfilesSnapshot()));
        router.get("/api/templates").handler(ctx -> writeJson(ctx.response(), workbench(ctx).templateSnapshot(ctx.request().getParam("profileName"))));
        router.post("/api/session/connect").handler(ctx -> writeJson(ctx.response(), workbench(ctx).connect(bodyJson(ctx))));
        router.post("/api/session/disconnect").handler(ctx -> writeJson(ctx.response(), workbench(ctx).disconnect(bodyJson(ctx))));
        router.post("/api/session/pulse-test").handler(ctx -> writeJson(ctx.response(), workbench(ctx).pulseTest(bodyJson(ctx))));
        router.post("/api/session/reset-sequence").handler(ctx -> writeJson(ctx.response(), workbench(ctx).resetSequenceNumbers(bodyJson(ctx))));
        router.post("/api/settings/profiles/save").handler(ctx -> writeJson(ctx.response(), workbench(ctx).saveSettingsProfile(bodyJson(ctx))));
        router.post("/api/settings/profiles/activate").handler(ctx -> writeJson(ctx.response(), workbench(ctx).activateSettingsProfile(bodyJson(ctx))));
        router.post("/api/settings/profiles/delete").handler(ctx -> writeJson(ctx.response(), workbench(ctx).deleteSettingsProfile(bodyJson(ctx))));
        router.post("/api/session-profiles/save").handler(ctx -> writeJson(ctx.response(), workbench(ctx).saveSettingsProfile(bodyJson(ctx))));
        router.post("/api/session-profiles/activate").handler(ctx -> writeJson(ctx.response(), workbench(ctx).activateSettingsProfile(bodyJson(ctx))));
        router.post("/api/session-profiles/delete").handler(ctx -> writeJson(ctx.response(), workbench(ctx).deleteSettingsProfile(bodyJson(ctx))));
        router.post("/api/settings/storage-path").handler(ctx -> writeJson(ctx.response(), workbench(ctx).updateSettingsStoragePath(bodyJson(ctx))));
        router.post("/api/templates/save").handler(ctx -> writeJson(ctx.response(), workbench(ctx).saveMessageTemplate(bodyJson(ctx))));
        router.post("/api/order-ticket/preview").handler(ctx -> writeJson(ctx.response(), workbench(ctx).previewOrder(bodyJson(ctx))));
        router.post("/api/order-ticket/send").handler(ctx -> writeJson(ctx.response(), workbench(ctx).sendOrder(bodyJson(ctx))));
        router.post("/api/orders/amend").handler(ctx -> writeJson(ctx.response(), workbench(ctx).amendBlotterOrder(bodyJson(ctx))));
        router.post("/api/orders/cancel").handler(ctx -> writeJson(ctx.response(), workbench(ctx).cancelBlotterOrder(bodyJson(ctx))));
        router.post("/api/order-flow/start").handler(ctx -> writeJson(ctx.response(), workbench(ctx).startOrderFlow(bodyJson(ctx))));
        router.post("/api/order-flow/stop").handler(ctx -> writeJson(ctx.response(), workbench(ctx).stopOrderFlow(bodyJson(ctx))));
        router.post("/api/cucumber/run").blockingHandler(ctx -> writeJson(ctx.response(), workbench(ctx).runCucumber(bodyJson(ctx))));

        router.get("/api/fix-messages").handler(ctx -> {
            int limit = Math.max(1, Math.min(parseIntParam(ctx.request().getParam("limit"), 20), 100));
            int offset = Math.max(0, parseIntParam(ctx.request().getParam("offset"), 0));
            writeJson(ctx.response(), workbench(ctx).recentFixMessages(limit, offset, ctx.request().getParam("profileName")));
        });

        router.getWithRegex("^/(home|neworder|order|orders|blotter|settings|session-profiles|sessionprofiles|recentfixmsgs|cucumber|about)$").handler(ctx -> ctx.reroute("/index.html"));
        router.getWithRegex("^/(home|neworder|order|orders|blotter|settings|session-profiles|sessionprofiles|recentfixmsgs|cucumber|about)/$").handler(ctx -> ctx.response()
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
            sessionRegistry.close();
        } catch (Exception exception) {
            log.warn("Error while stopping TheFixClient session registry", exception);
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

    // ── Private helpers ───────────────────────────────────────────────────────

    private static TheFixClientWorkbenchState workbench(RoutingContext ctx) {
        UserSession session = ctx.get("userSession");
        return session.workbenchState();
    }

    private Optional<UserSession> resolveSession(RoutingContext ctx) {
        return sessionRegistry.validate(resolveToken(ctx));
    }

    private static String resolveToken(RoutingContext ctx) {
        String headerToken = ctx.request().getHeader(AUTH_TOKEN_HEADER);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        io.vertx.core.http.Cookie cookie = ctx.request().getCookie(AUTH_TOKEN_COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    private static void writeJson(io.vertx.core.http.HttpServerResponse response, JsonObject payload) {
        response.putHeader("content-type", "application/json")
                .end(payload.encode());
    }

    private static JsonObject bodyJson(RoutingContext context) {
        return context.body() == null ? new JsonObject() : context.body().asJsonObject();
    }

    private static int parseIntParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}

