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

package com.llexsimulator.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llexsimulator.engine.FixConnection;
import com.llexsimulator.engine.OrderSessionRegistry;
import com.llexsimulator.engine.SessionDiagnosticsSnapshot;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import uk.co.real_logic.artio.session.Session;

import java.util.ArrayList;
import java.util.List;

/** Lists active FIX sessions and exposes disconnect. */
public final class SessionHandler {

    private final OrderSessionRegistry registry;
    private final ObjectMapper         mapper;

    public SessionHandler(OrderSessionRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper   = mapper;
    }

    public Handler<RoutingContext> list() {
        return ctx -> {
            try {
                List<SessionDiagnosticsSnapshot> sessions = new ArrayList<>();
                for (FixConnection connection : registry.getAllConnections()) {
                    sessions.add(connection.snapshot());
                }
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(mapper.writeValueAsString(sessions));
            } catch (Exception e) {
                ctx.fail(500, e);
            }
        };
    }

    public Handler<RoutingContext> recentDisconnects() {
        return ctx -> {
            try {
                int limit = parseLimit(ctx.request().getParam("limit"));
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(mapper.writeValueAsString(registry.getRecentDisconnects(limit)));
            } catch (Exception e) {
                ctx.fail(500, e);
            }
        };
    }

    public Handler<RoutingContext> disconnect() {
        return ctx -> {
            String sessionIdStr = ctx.pathParam("id");
            boolean found = false;
            for (FixConnection connection : registry.getAllConnections()) {
                if (connection.sessionKey().equals(sessionIdStr)) {
                    Session session = connection.session();
                    if (session != null) {
                        session.requestDisconnect();
                        found = true;
                    }
                    break;
                }
            }
            ctx.response()
               .putHeader("Content-Type", "application/json")
               .setStatusCode(found ? 200 : 404)
               .end("{\"disconnected\":" + found + "}");
        };
    }

    public Handler<RoutingContext> resetSequenceNumbers() {
        return ctx -> {
            String sessionIdStr = ctx.pathParam("id");
            boolean found = false;
            boolean reset = false;
            long position = 0L;
            for (FixConnection connection : registry.getAllConnections()) {
                if (connection.sessionKey().equals(sessionIdStr)) {
                    found = true;
                    Session session = connection.session();
                    if (session != null) {
                        position = session.resetSequenceNumbers();
                        reset = position > 0L;
                    }
                    break;
                }
            }
            int statusCode = !found ? 404 : reset ? 200 : 409;
            ctx.response()
               .putHeader("Content-Type", "application/json")
               .setStatusCode(statusCode)
               .end("{\"reset\":" + reset + ",\"found\":" + found + ",\"position\":" + position + "}");
        };
    }

    private static int parseLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return 16;
        }
        try {
            return Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException e) {
            return 16;
        }
    }
}
