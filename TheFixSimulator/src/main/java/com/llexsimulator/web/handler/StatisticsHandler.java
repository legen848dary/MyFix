package com.llexsimulator.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.metrics.MetricsRegistry;
import com.llexsimulator.web.dto.StatisticsDto;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/** Serves the current metrics snapshot as JSON. */
public final class StatisticsHandler {

    private final MetricsRegistry  registry;
    private final FillProfileManager profileManager;
    private final ObjectMapper     mapper;

    public StatisticsHandler(MetricsRegistry registry, FillProfileManager profileManager,
                              ObjectMapper mapper) {
        this.registry       = registry;
        this.profileManager = profileManager;
        this.mapper         = mapper;
    }

    public Handler<RoutingContext> get() {
        return ctx -> {
            try {
                writeDto(ctx, buildDto());
            } catch (Exception e) {
                ctx.fail(500, e);
            }
        };
    }

    public Handler<RoutingContext> reset() {
        return ctx -> {
            try {
                registry.reset();
                writeDto(ctx, buildDto());
            } catch (Exception e) {
                ctx.fail(500, e);
            }
        };
    }

    private StatisticsDto buildDto() {
        StatisticsDto dto = new StatisticsDto();
        dto.ordersReceived   = registry.getOrdersReceived();
        dto.execReportsSent  = registry.getExecReportsSent();
        dto.fillsSent        = registry.getFillsSent();
        dto.rejectsSent      = registry.getRejectsSent();
        dto.cancelsSent      = registry.getCancelsSent();
        dto.p50LatencyUs     = registry.getP50Ns() / 1000;
        dto.p75LatencyUs     = registry.getP75Ns() / 1000;
        dto.p80LatencyUs     = registry.getP80Ns() / 1000;
        dto.p90LatencyUs     = registry.getP90Ns() / 1000;
        dto.p99LatencyUs     = registry.getP99Ns() / 1000;
        dto.maxLatencyUs     = registry.getMaxNs() / 1000;
        dto.preValidationQueueP50LatencyUs = registry.getPreValidationQueueP50Ns() / 1000;
        dto.preValidationQueueP75LatencyUs = registry.getPreValidationQueueP75Ns() / 1000;
        dto.preValidationQueueP90LatencyUs = registry.getPreValidationQueueP90Ns() / 1000;
        dto.preValidationQueueMaxLatencyUs = registry.getPreValidationQueueMaxNs() / 1000;
        dto.ingressPublishP50LatencyUs = registry.getIngressPublishP50Ns() / 1000;
        dto.ingressPublishP75LatencyUs = registry.getIngressPublishP75Ns() / 1000;
        dto.ingressPublishP90LatencyUs = registry.getIngressPublishP90Ns() / 1000;
        dto.ingressPublishMaxLatencyUs = registry.getIngressPublishMaxNs() / 1000;
        dto.disruptorQueueP50LatencyUs = registry.getDisruptorQueueP50Ns() / 1000;
        dto.disruptorQueueP75LatencyUs = registry.getDisruptorQueueP75Ns() / 1000;
        dto.disruptorQueueP90LatencyUs = registry.getDisruptorQueueP90Ns() / 1000;
        dto.disruptorQueueMaxLatencyUs = registry.getDisruptorQueueMaxNs() / 1000;
        dto.validationP50LatencyUs = registry.getValidationP50Ns() / 1000;
        dto.validationP75LatencyUs = registry.getValidationP75Ns() / 1000;
        dto.validationP90LatencyUs = registry.getValidationP90Ns() / 1000;
        dto.validationMaxLatencyUs = registry.getValidationMaxNs() / 1000;
        dto.fillStrategyP50LatencyUs = registry.getFillStrategyP50Ns() / 1000;
        dto.fillStrategyP75LatencyUs = registry.getFillStrategyP75Ns() / 1000;
        dto.fillStrategyP90LatencyUs = registry.getFillStrategyP90Ns() / 1000;
        dto.fillStrategyMaxLatencyUs = registry.getFillStrategyMaxNs() / 1000;
        dto.executionReportP50LatencyUs = registry.getExecutionReportP50Ns() / 1000;
        dto.executionReportP75LatencyUs = registry.getExecutionReportP75Ns() / 1000;
        dto.executionReportP90LatencyUs = registry.getExecutionReportP90Ns() / 1000;
        dto.executionReportMaxLatencyUs = registry.getExecutionReportMaxNs() / 1000;
        dto.metricsPublishP50LatencyUs = registry.getMetricsPublishP50Ns() / 1000;
        dto.metricsPublishP75LatencyUs = registry.getMetricsPublishP75Ns() / 1000;
        dto.metricsPublishP90LatencyUs = registry.getMetricsPublishP90Ns() / 1000;
        dto.metricsPublishMaxLatencyUs = registry.getMetricsPublishMaxNs() / 1000;
        dto.outboundQueueP50LatencyUs = registry.getOutboundQueueP50Ns() / 1000;
        dto.outboundQueueP75LatencyUs = registry.getOutboundQueueP75Ns() / 1000;
        dto.outboundQueueP90LatencyUs = registry.getOutboundQueueP90Ns() / 1000;
        dto.outboundQueueMaxLatencyUs = registry.getOutboundQueueMaxNs() / 1000;
        dto.outboundSendP50LatencyUs = registry.getOutboundSendP50Ns() / 1000;
        dto.outboundSendP75LatencyUs = registry.getOutboundSendP75Ns() / 1000;
        dto.outboundSendP90LatencyUs = registry.getOutboundSendP90Ns() / 1000;
        dto.outboundSendMaxLatencyUs = registry.getOutboundSendMaxNs() / 1000;
        dto.throughputPerSec = registry.getThroughputPerSec();
        dto.fillRatePct      = dto.ordersReceived > 0
                               ? dto.fillsSent * 100.0 / dto.ordersReceived : 0.0;
        dto.activeProfile    = profileManager.getActiveProfileName();
        return dto;
    }

    private void writeDto(RoutingContext ctx, StatisticsDto dto) throws Exception {
        ctx.response()
           .putHeader("Content-Type", "application/json")
           .end(mapper.writeValueAsString(dto));
    }
}

