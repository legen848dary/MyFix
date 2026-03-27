package com.llexsimulator.web;

import com.llexsimulator.config.SimulatorConfig;
import com.llexsimulator.disruptor.DisruptorPipeline;
import com.llexsimulator.engine.OrderSessionRegistry;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.metrics.MetricsRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Vert.x HTTP server — serves the Vue.js SPA, REST API, and WebSocket endpoint.
 *
 * <p>Native transport (epoll on Linux, kqueue on macOS) is enabled for lower
 * per-connection overhead. {@code TCP_NODELAY} is set to disable Nagle's
 * algorithm.
 */
public final class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private final Vertx                vertx;
    private final WebSocketBroadcaster broadcaster;
    private final HttpServer           httpServer;

    public WebServer(SimulatorConfig     config,
                     MetricsRegistry     registry,
                     OrderSessionRegistry sessionRegistry,
                     FillProfileManager  profileManager,
                     DisruptorPipeline   pipeline) {

        VertxOptions opts = new VertxOptions().setPreferNativeTransport(true);
        this.vertx        = Vertx.vertx(opts);
        this.broadcaster  = new WebSocketBroadcaster();

        Router router = RestApiRouter.create(vertx, registry, sessionRegistry,
                                             profileManager, pipeline);

        HttpServerOptions serverOpts = new HttpServerOptions()
                .setPort(config.webPort())
                .setHost("0.0.0.0")
                .setTcpNoDelay(true)
                .setTcpFastOpen(true)
                .setReusePort(true);

        try {
            this.httpServer = vertx.createHttpServer(serverOpts)
                    .requestHandler(router)
                    .webSocketHandler(broadcaster::handleUpgrade)
                    .listen()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);
            log.info("Web server started on port {}", httpServer.actualPort());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start simulator web server", exception);
        }
    }

    public Vertx getVertx()                          { return vertx; }
    public WebSocketBroadcaster getBroadcaster()     { return broadcaster; }

    public void stop() {
        try {
            httpServer.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);
            vertx.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);
            log.info("Web server stopped");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to stop simulator web server", exception);
        }
    }
}

