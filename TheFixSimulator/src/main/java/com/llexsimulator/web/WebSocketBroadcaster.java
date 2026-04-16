/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.llexsimulator.web;

import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains the set of connected WebSocket clients and broadcasts JSON messages
 * to all of them.
 *
 * <p>All {@link #broadcast(String)} calls MUST originate from the Vert.x event-loop
 * thread (use {@code vertx.runOnContext()} when calling from other threads).
 */
public final class WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final CopyOnWriteArraySet<ServerWebSocket> clients = new CopyOnWriteArraySet<>();

    public void handleUpgrade(ServerWebSocket ws) {
        clients.add(ws);
        log.info("WebSocket client connected: {}", ws.remoteAddress());

        ws.closeHandler(v -> {
            clients.remove(ws);
            log.debug("WebSocket client disconnected: {}", ws.remoteAddress());
        });
        ws.exceptionHandler(ex -> {
            clients.remove(ws);
            log.debug("WebSocket client error: {}", ex.getMessage());
        });
    }

    /** Sends {@code json} to all connected clients. Must be called on the event loop. */
    public void broadcast(String json) {
        for (ServerWebSocket ws : clients) {
            ws.writeTextMessage(json);
        }
    }

    public int clientCount() { return clients.size(); }
}
