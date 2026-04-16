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

package com.llexsimulator.disruptor;

import com.llexsimulator.sbe.*;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Pre-allocated Disruptor ring-buffer slot.
 *
 * <p>One instance is created per ring-buffer slot by {@link OrderEventFactory}
 * at startup and reused for the lifetime of the process. SBE encoder/decoder
 * flyweights are created once; they are re-wrapped onto the buffers at each
 * processing cycle (zero-GC — wrap just sets two fields on a pre-allocated object).
 *
 * <p>All fields are {@code public} so that handlers in sub-packages can access
 * them directly without getter-call overhead.
 */
public final class OrderEvent {

    // ── Pre-allocated off-heap direct byte buffers (one per slot) ────────────
    public final UnsafeBuffer orderBuffer            = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    public final UnsafeBuffer execReportBuffer       = new UnsafeBuffer(ByteBuffer.allocateDirect(512));
    public final UnsafeBuffer fillInstructionBuffer  = new UnsafeBuffer(ByteBuffer.allocateDirect(128));

    // ── SBE flyweight codecs — created once, re-wrapped per event ────────────
    public final NewOrderSingleEncoder   nosEncoder             = new NewOrderSingleEncoder();
    public final NewOrderSingleDecoder   nosDecoder             = new NewOrderSingleDecoder();
    public final ExecutionReportEncoder  execReportEncoder      = new ExecutionReportEncoder();
    public final ExecutionReportDecoder  execReportDecoder      = new ExecutionReportDecoder();
    public final FillInstructionEncoder  fillInstructionEncoder = new FillInstructionEncoder();
    public final FillInstructionDecoder  fillInstructionDecoder = new FillInstructionDecoder();

    // ── Shared MessageHeader codecs ───────────────────────────────────────────
    public final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    public final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    // ── Scratch byte arrays for zero-GC ID copying (SBE uses byte[]) ─────────
    public final byte[] clOrdIdBytes  = new byte[36];
    public final byte[] origClOrdIdBytes = new byte[36];
    public final byte[] orderIdBytes  = new byte[36];
    public final byte[] execIdBytes   = new byte[36];
    public final byte[] symbolBytes   = new byte[16];
    public final byte[] senderBytes   = new byte[16];
    public final byte[] targetBytes   = new byte[16];

    // ── Hot-path metadata ─────────────────────────────────────────────────────
    public long    correlationId;
    public long    sessionConnectionId;
    public long    arrivalTimeNs;
    public long    publishCompleteNs;
    public long    validationStartNs;
    public long    validationEndNs;
    public long    fillStrategyEndNs;
    public long    executionReportEndNs;
    public byte    sideValue;
    public byte    orderTypeValue;
    public long    orderQty;
    public long    price;
    public long    referencedCorrelationId;
    public OrderRequestType requestType = OrderRequestType.NEW;
    public boolean hasOrigClOrdId;
    public boolean isValid;

    /** Default constructor — flyweights start unbound; they are wrapped per event. */
    public OrderEvent() {}
}
