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

package com.llexsimulator.disruptor.handler;

import com.llexsimulator.disruptor.OrderEvent;
import com.lmax.disruptor.EventHandler;

/**
 * Runs the full order-processing pipeline on a single Disruptor consumer thread.
 *
 * <p>This preserves the logical stage ordering while avoiding cross-thread handoff
 * between validation, fill-strategy selection, execution-report generation, and
 * metrics accounting.
 */
public final class CompositeOrderEventHandler implements EventHandler<OrderEvent> {

    private final boolean benchmarkStageTimingEnabled;
    private final ValidationHandler validationHandler;
    private final FillStrategyHandler fillStrategyHandler;
    private final ExecutionReportHandler executionReportHandler;
    private final MetricsPublishHandler metricsPublishHandler;

    public CompositeOrderEventHandler(
            boolean benchmarkStageTimingEnabled,
            ValidationHandler validationHandler,
            FillStrategyHandler fillStrategyHandler,
            ExecutionReportHandler executionReportHandler,
            MetricsPublishHandler metricsPublishHandler
    ) {
        this.benchmarkStageTimingEnabled = benchmarkStageTimingEnabled;
        this.validationHandler = validationHandler;
        this.fillStrategyHandler = fillStrategyHandler;
        this.executionReportHandler = executionReportHandler;
        this.metricsPublishHandler = metricsPublishHandler;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (benchmarkStageTimingEnabled) {
            event.validationStartNs = System.nanoTime();
        }

        validationHandler.onEvent(event, sequence, endOfBatch);
        if (benchmarkStageTimingEnabled) {
            event.validationEndNs = System.nanoTime();
        }

        fillStrategyHandler.onEvent(event, sequence, endOfBatch);
        if (benchmarkStageTimingEnabled) {
            event.fillStrategyEndNs = System.nanoTime();
        }

        executionReportHandler.onEvent(event, sequence, endOfBatch);
        if (benchmarkStageTimingEnabled) {
            event.executionReportEndNs = System.nanoTime();
        }

        metricsPublishHandler.onEvent(event, sequence, endOfBatch);
    }
}
