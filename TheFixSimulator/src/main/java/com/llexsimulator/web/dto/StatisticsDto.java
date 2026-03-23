package com.llexsimulator.web.dto;

/** Statistics snapshot returned by the REST API. All latencies in microseconds. */
public final class StatisticsDto {
    public long   ordersReceived;
    public long   execReportsSent;
    public long   fillsSent;
    public long   rejectsSent;
    public long   cancelsSent;
    public long   p50LatencyUs;
    public long   p75LatencyUs;
    public long   p80LatencyUs;
    public long   p90LatencyUs;
    public long   p99LatencyUs;
    public long   maxLatencyUs;
    public long   preValidationQueueP50LatencyUs;
    public long   preValidationQueueP75LatencyUs;
    public long   preValidationQueueP90LatencyUs;
    public long   preValidationQueueMaxLatencyUs;
    public long   ingressPublishP50LatencyUs;
    public long   ingressPublishP75LatencyUs;
    public long   ingressPublishP90LatencyUs;
    public long   ingressPublishMaxLatencyUs;
    public long   disruptorQueueP50LatencyUs;
    public long   disruptorQueueP75LatencyUs;
    public long   disruptorQueueP90LatencyUs;
    public long   disruptorQueueMaxLatencyUs;
    public long   validationP50LatencyUs;
    public long   validationP75LatencyUs;
    public long   validationP90LatencyUs;
    public long   validationMaxLatencyUs;
    public long   fillStrategyP50LatencyUs;
    public long   fillStrategyP75LatencyUs;
    public long   fillStrategyP90LatencyUs;
    public long   fillStrategyMaxLatencyUs;
    public long   executionReportP50LatencyUs;
    public long   executionReportP75LatencyUs;
    public long   executionReportP90LatencyUs;
    public long   executionReportMaxLatencyUs;
    public long   metricsPublishP50LatencyUs;
    public long   metricsPublishP75LatencyUs;
    public long   metricsPublishP90LatencyUs;
    public long   metricsPublishMaxLatencyUs;
    public long   outboundQueueP50LatencyUs;
    public long   outboundQueueP75LatencyUs;
    public long   outboundQueueP90LatencyUs;
    public long   outboundQueueMaxLatencyUs;
    public long   outboundSendP50LatencyUs;
    public long   outboundSendP75LatencyUs;
    public long   outboundSendP90LatencyUs;
    public long   outboundSendMaxLatencyUs;
    public long   throughputPerSec;
    public double fillRatePct;
    public String activeProfile;

    public StatisticsDto() {}
}

