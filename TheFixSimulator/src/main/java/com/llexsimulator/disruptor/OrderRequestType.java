package com.llexsimulator.disruptor;

/**
 * Logical inbound order request type carried alongside the SBE order payload.
 */
public enum OrderRequestType {
    NEW,
    CANCEL,
    AMEND
}

