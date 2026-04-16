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

import com.llexsimulator.disruptor.DisruptorPipeline;
import com.llexsimulator.engine.OrderSessionRegistry;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/** Simple health check endpoint. */
public final class HealthHandler {

    private final OrderSessionRegistry registry;
    private final DisruptorPipeline    pipeline;

    public HealthHandler(OrderSessionRegistry registry, DisruptorPipeline pipeline) {
        this.registry = registry;
        this.pipeline = pipeline;
    }

    public Handler<RoutingContext> get() {
        return ctx -> {
            long remaining = pipeline.getRemainingCapacity();
            ctx.response()
               .putHeader("Content-Type", "application/json")
               .end("{\"status\":\"UP\""
                    + ",\"fixSessions\":" + registry.activeCount()
                    + ",\"disruptorRemainingCapacity\":" + remaining
                    + "}");
        };
    }
}
