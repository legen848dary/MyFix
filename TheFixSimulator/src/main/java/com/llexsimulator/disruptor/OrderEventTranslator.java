package com.llexsimulator.disruptor;

import com.llexsimulator.engine.FixConnection;
import com.llexsimulator.sbe.*;

import uk.co.real_logic.artio.decoder.NewOrderSingleDecoder;
import uk.co.real_logic.artio.decoder.OrderCancelReplaceRequestDecoder;
import uk.co.real_logic.artio.decoder.OrderCancelRequestDecoder;
import uk.co.real_logic.artio.fields.ReadOnlyDecimalFloat;

/**
 * Translates an incoming Artio FIX decoder into the pre-allocated
 * {@link OrderEvent} ring-buffer slot.
 *
 * <p>All scratch arrays are {@code byte[]} since SBE fixed-length char fields
 * use {@code byte[]} for their put/get methods.
 */
public final class OrderEventTranslator {

    private static final long[] POW10 = {
            1L,
            10L,
            100L,
            1_000L,
            10_000L,
            100_000L,
            1_000_000L,
            10_000_000L,
            100_000_000L
    };

    // Scratch byte arrays (single Artio library-thread handler instance — no sync needed)
    private final byte[] symbolBuf  = new byte[16];
    private final byte[] clOrdBuf   = new byte[36];
    private final byte[] senderBuf  = new byte[16];
    private final byte[] targetBuf  = new byte[16];
    private long correlationCounter = 0L;

    public void translateTo(OrderEvent event, long sequence, Object decodedMessage,
                            FixConnection connection, long arrivalNs) {
        long          sessionConnId  = connection.connectionId();

        event.correlationId       = ++correlationCounter;
        event.sessionConnectionId = sessionConnId;
        event.arrivalTimeNs       = arrivalNs;
        event.publishCompleteNs   = 0L;
        event.validationStartNs   = 0L;
        event.validationEndNs     = 0L;
        event.fillStrategyEndNs   = 0L;
        event.executionReportEndNs = 0L;
        event.sideValue           = (byte) OrderSide.NULL_VAL.value();
        event.orderTypeValue      = (byte) OrderType.LIMIT.value();
        event.orderQty            = 0L;
        event.price               = 0L;
        event.referencedCorrelationId = 0L;
        event.requestType         = OrderRequestType.NEW;
        event.hasOrigClOrdId      = false;
        event.isValid             = false;

        // ── Encode into pre-allocated orderBuffer using SBE ──────────────────
        NewOrderSingleEncoder encoder = event.nosEncoder;
        encoder.wrapAndApplyHeader(event.orderBuffer, 0, event.headerEncoder);

        encoder.correlationId(event.correlationId)
               .sessionConnectionId(sessionConnId)
               .arrivalTimeNs(arrivalNs);

        switch (decodedMessage) {
            case NewOrderSingleDecoder decoder -> {
                event.requestType = OrderRequestType.NEW;
                copyCharsToBytes(decoder.clOrdID(), decoder.clOrdIDLength(), clOrdBuf, 36);
                copyCharsToBytes(decoder.symbol(), decoder.symbolLength(), symbolBuf, 16);
                OrderSide side = mapSide(decoder.side());
                OrderType orderType = mapOrdType(decoder.ordType());
                long orderQty = toScaledLong(decoder.orderQty(), 4);
                long price = decoder.hasPrice() ? toScaledLong(decoder.price(), 8) : 0L;
                event.sideValue = (byte) side.value();
                event.orderTypeValue = (byte) orderType.value();
                event.orderQty = orderQty;
                event.price = price;
                encoder.side(side);
                encoder.orderType(orderType);
                encoder.timeInForce(decoder.hasTimeInForce()
                        ? mapTif(decoder.timeInForce())
                        : com.llexsimulator.sbe.TimeInForce.DAY);
                encoder.orderQty(orderQty);
                encoder.price(price);
                encoder.stopPx(decoder.hasStopPx() ? toScaledLong(decoder.stopPx(), 8) : 0L);
                encoder.transactTimeNs(arrivalNs);
            }
            case OrderCancelReplaceRequestDecoder decoder -> {
                event.requestType = OrderRequestType.AMEND;
                copyCharsToBytes(decoder.clOrdID(), decoder.clOrdIDLength(), clOrdBuf, 36);
                copyCharsToBytes(decoder.origClOrdID(), decoder.origClOrdIDLength(), event.origClOrdIdBytes, 36);
                copyCharsToBytes(decoder.symbol(), decoder.symbolLength(), symbolBuf, 16);
                OrderSide side = mapSide(decoder.side());
                OrderType orderType = mapOrdType(decoder.ordType());
                long orderQty = decoder.hasOrderQty() ? toScaledLong(decoder.orderQty(), 4) : 0L;
                long price = decoder.hasPrice() ? toScaledLong(decoder.price(), 8) : 0L;
                event.hasOrigClOrdId = decoder.origClOrdIDLength() > 0;
                event.sideValue = (byte) side.value();
                event.orderTypeValue = (byte) orderType.value();
                event.orderQty = orderQty;
                event.price = price;
                encoder.side(side);
                encoder.orderType(orderType);
                encoder.timeInForce(decoder.hasTimeInForce()
                        ? mapTif(decoder.timeInForce())
                        : com.llexsimulator.sbe.TimeInForce.DAY);
                encoder.orderQty(orderQty);
                encoder.price(price);
                encoder.stopPx(decoder.hasStopPx() ? toScaledLong(decoder.stopPx(), 8) : 0L);
                encoder.transactTimeNs(arrivalNs);
            }
            case OrderCancelRequestDecoder decoder -> {
                event.requestType = OrderRequestType.CANCEL;
                copyCharsToBytes(decoder.clOrdID(), decoder.clOrdIDLength(), clOrdBuf, 36);
                copyCharsToBytes(decoder.origClOrdID(), decoder.origClOrdIDLength(), event.origClOrdIdBytes, 36);
                copyCharsToBytes(decoder.symbol(), decoder.symbolLength(), symbolBuf, 16);
                OrderSide side = mapSide(decoder.side());
                event.hasOrigClOrdId = decoder.origClOrdIDLength() > 0;
                event.sideValue = (byte) side.value();
                event.orderTypeValue = (byte) OrderType.LIMIT.value();
                event.orderQty = 0L;
                event.price = 0L;
                encoder.side(side);
                encoder.orderType(OrderType.LIMIT);
                encoder.timeInForce(com.llexsimulator.sbe.TimeInForce.DAY);
                encoder.orderQty(0L);
                encoder.price(0L);
                encoder.stopPx(0L);
                encoder.transactTimeNs(arrivalNs);
            }
            default -> throw new IllegalArgumentException("Unsupported FIX decoder: " + decodedMessage.getClass().getName());
        }

        encoder.putClOrdId(clOrdBuf, 0);
        encoder.putSymbol(symbolBuf, 0);

        // FIX version
        encoder.fixVersion(mapBeginString(connection.beginString()));

        // sender / target compId
        copyStringToBytes(connection.senderCompId(), senderBuf);
        encoder.putSenderCompId(senderBuf, 0);
        copyStringToBytes(connection.targetCompId(), targetBuf);
        encoder.putTargetCompId(targetBuf, 0);

        // Re-wrap the NOS *decoder* so downstream handlers can read the just-encoded data
        event.nosDecoder.wrapAndApplyHeader(event.orderBuffer, 0, event.headerDecoder);
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private static OrderSide mapSide(char c) {
        return switch (c) {
            case '1' -> OrderSide.BUY;
            case '2' -> OrderSide.SELL;
            case '5' -> OrderSide.SELL_SHORT;
            default  -> OrderSide.BUY;
        };
    }

    private static OrderType mapOrdType(char c) {
        return switch (c) {
            case '1' -> OrderType.MARKET;
            case '2' -> OrderType.LIMIT;
            case '3' -> OrderType.STOP;
            case '4' -> OrderType.STOP_LIMIT;
            default  -> OrderType.LIMIT;
        };
    }

    private static com.llexsimulator.sbe.TimeInForce mapTif(char c) {
        return switch (c) {
            case '0' -> com.llexsimulator.sbe.TimeInForce.DAY;
            case '1' -> com.llexsimulator.sbe.TimeInForce.GTC;
            case '3' -> com.llexsimulator.sbe.TimeInForce.IOC;
            case '4' -> com.llexsimulator.sbe.TimeInForce.FOK;
            default  -> com.llexsimulator.sbe.TimeInForce.DAY;
        };
    }

    private static FixVersion mapBeginString(String bs) {
        return switch (bs) {
            case "FIX.4.2"  -> FixVersion.FIX42;
            case "FIX.4.4"  -> FixVersion.FIX44;
            case "FIX.5.0"  -> FixVersion.FIX50;
            case "FIXT.1.1" -> FixVersion.FIXT11;
            default         -> FixVersion.FIX44;
        };
    }

    /** Copies a String into a byte[] with blank-padding (ASCII). */
    static void copyStringToBytes(String src, byte[] dst) {
        int len = dst.length;
        int n = Math.min(src.length(), len);
        for (int i = 0; i < n; i++)  dst[i] = (byte) src.charAt(i);
        for (int i = n; i < len; i++) dst[i] = (byte) ' ';
    }

    private static void copyCharsToBytes(char[] src, int srcLen, byte[] dst, int dstLen) {
        int n = Math.min(srcLen, dstLen);
        for (int i = 0; i < n; i++) {
            dst[i] = (byte) src[i];
        }
        for (int i = n; i < dstLen; i++) {
            dst[i] = (byte) ' ';
        }
    }

    private static long toScaledLong(ReadOnlyDecimalFloat value, int targetScale) {
        long unscaled = value.value();
        int scale = value.scale();
        if (unscaled == 0L) {
            return 0L;
        }
        if (scale == targetScale) {
            return unscaled;
        }
        int delta = Math.abs(targetScale - scale);
        long factor = delta < POW10.length ? POW10[delta] : 1L;
        return scale < targetScale ? unscaled * factor : unscaled / factor;
    }
}
