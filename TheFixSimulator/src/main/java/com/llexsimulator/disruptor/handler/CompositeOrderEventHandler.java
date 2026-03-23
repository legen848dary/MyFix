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

