package com.llexsimulator.testutil;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.FillInstructionDecoder;
import com.llexsimulator.sbe.FixVersion;
import com.llexsimulator.sbe.NewOrderSingleEncoder;
import com.llexsimulator.sbe.OrderSide;
import com.llexsimulator.sbe.OrderType;
import com.llexsimulator.sbe.RejectReason;
import com.llexsimulator.sbe.TimeInForce;

import java.nio.charset.StandardCharsets;

public final class OrderEventFixtures {

    private OrderEventFixtures() {
    }

    public static OrderEvent newLimitOrderEvent(long correlationId, long sessionConnectionId, OrderSide side,
                                                long orderQty, long price) {
        OrderEvent event = new OrderEvent();
        event.correlationId = correlationId;
        event.sessionConnectionId = sessionConnectionId;
        event.arrivalTimeNs = 123_456_789L;

        NewOrderSingleEncoder encoder = event.nosEncoder;
        encoder.wrapAndApplyHeader(event.orderBuffer, 0, event.headerEncoder);
        encoder.correlationId(correlationId)
                .sessionConnectionId(sessionConnectionId)
                .arrivalTimeNs(event.arrivalTimeNs)
                .side(side)
                .orderType(OrderType.LIMIT)
                .timeInForce(TimeInForce.DAY)
                .orderQty(orderQty)
                .price(price)
                .stopPx(0L)
                .transactTimeNs(event.arrivalTimeNs)
                .fixVersion(FixVersion.FIX44);
        encoder.putClOrdId(ascii36("CL-" + correlationId), 0);
        encoder.putSymbol(ascii16("AAPL"), 0);
        encoder.putSenderCompId(ascii16("CLIENT1"), 0);
        encoder.putTargetCompId(ascii16("LLEXSIM"), 0);
        event.nosDecoder.wrapAndApplyHeader(event.orderBuffer, 0, event.headerDecoder);
        event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder);
        return event;
    }

    public static FillInstructionDecoder decodeFillInstruction(OrderEvent event) {
        event.fillInstructionDecoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerDecoder);
        return event.fillInstructionDecoder;
    }

    public static void writeFillInstruction(OrderEvent event,
                                            FillBehaviorType behavior,
                                            int fillPctBps,
                                            int numPartialFills,
                                            long delayNs,
                                            long fillPrice,
                                            RejectReason rejectReason) {
        event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder)
                .correlationId(event.correlationId)
                .fillBehavior(behavior)
                .fillPctBps(fillPctBps)
                .numPartialFills((short) numPartialFills)
                .delayNs(delayNs)
                .fillPrice(fillPrice)
                .rejectReasonCode(rejectReason)
                .randomMinQtyPct(0)
                .randomMaxQtyPct(0)
                .randomMinDelayNs(0L)
                .randomMaxDelayNs(0L);
        decodeFillInstruction(event);
    }

    public static String trimmedAscii(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0 && bytes[len] != ' ') {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.US_ASCII);
    }

    public static byte[] ascii36(String value) {
        return paddedAscii(value, 36);
    }

    public static byte[] ascii16(String value) {
        return paddedAscii(value, 16);
    }

    private static byte[] paddedAscii(String value, int width) {
        byte[] out = new byte[width];
        byte[] src = value.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(width, src.length);
        System.arraycopy(src, 0, out, 0, len);
        for (int i = len; i < width; i++) {
            out[i] = ' ';
        }
        return out;
    }
}

