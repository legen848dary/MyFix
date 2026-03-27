package com.insoftu.thefix.client;

import quickfix.field.OrdType;
import quickfix.field.Side;

import java.util.List;
import java.util.Locale;
import java.util.Map;

record TheFixOrderRequest(
        String messageTypeCode,
        String clOrdId,
        String origClOrdId,
        String symbol,
        String side,
        int quantity,
        double price,
        double stopPrice,
        String timeInForce,
        String orderType,
        String priceType,
        String region,
        String market,
        String currency,
        List<TheFixTagEntry> additionalTags
) {
    private static final Map<String, List<String>> BULK_SYMBOLS_BY_MARKET = Map.ofEntries(
            Map.entry("XNAS", List.of("AAPL", "MSFT", "NVDA", "AMZN")),
            Map.entry("XNYS", List.of("IBM", "GS", "KO", "JNJ")),
            Map.entry("XLON", List.of("BP.L", "VOD.L", "HSBA.L", "AZN.L")),
            Map.entry("XPAR", List.of("AIR.PA", "BNP.PA", "MC.PA", "OR.PA")),
            Map.entry("XETR", List.of("SAP.DE", "SIE.DE", "BMW.DE", "ADS.DE")),
            Map.entry("XSWX", List.of("NESN.SW", "NOVN.SW", "ROG.SW", "UBSG.SW")),
            Map.entry("XHKG", List.of("0700.HK", "9988.HK", "1299.HK", "2318.HK")),
            Map.entry("XTKS", List.of("7203.T", "6758.T", "9984.T", "8035.T")),
            Map.entry("XSES", List.of("D05.SI", "O39.SI", "U11.SI", "C38U.SI")),
            Map.entry("XSHG", List.of("600519.SS", "601318.SS", "600036.SS", "601988.SS")),
            Map.entry("XTSE", List.of("RY.TO", "SHOP.TO", "TD.TO", "BNS.TO")),
            Map.entry("BVMF", List.of("PETR4", "VALE3", "ITUB4", "ABEV3"))
    );
    private static final List<String> BULK_SIDES = List.of("BUY", "SELL", "SELL_SHORT");
    private static final List<String> BULK_ORDER_TYPES = List.of("MARKET", "LIMIT", "STOP", "STOP_LIMIT");
    private static final List<String> BULK_TIME_IN_FORCES = List.of("DAY", "IOC", "FOK", "GTC");
    private static final List<Integer> BULK_QUANTITIES = List.of(100, 200, 300, 500, 750, 1000, 1500, 2000, 2500, 5000);

    TheFixOrderRequest {
        additionalTags = additionalTags == null ? List.of() : List.copyOf(additionalTags);
    }

    TheFixOrderRequest bulkVariant(long seed) {
        if (messageType() != TheFixMessageType.NEW_ORDER_SINGLE) {
            return this;
        }

        long symbolSeed = mix(seed ^ 0x9E3779B97F4A7C15L);
        long sideSeed = mix(seed ^ 0xC2B2AE3D27D4EB4FL);
        long quantitySeed = mix(seed ^ 0x165667B19E3779F9L);
        long orderTypeSeed = mix(seed ^ 0x85EBCA77C2B2AE63L);
        long tifSeed = mix(seed ^ 0x27D4EB2F165667C5L);
        long priceSeed = mix(seed ^ 0x94D049BB133111EBL);
        long stopSeed = mix(seed ^ 0x2545F4914F6CDD1DL);

        String nextSymbol = pickSymbol(symbolSeed);
        String nextSide = pick(BULK_SIDES, sideSeed, side == null || side.isBlank() ? "BUY" : side);
        int nextQuantity = pick(BULK_QUANTITIES, quantitySeed, quantity > 0 ? quantity : 100);
        String nextOrderType = pick(BULK_ORDER_TYPES, orderTypeSeed, orderType == null || orderType.isBlank() ? "LIMIT" : orderType);
        String nextTimeInForce = pick(BULK_TIME_IN_FORCES, tifSeed, timeInForce == null || timeInForce.isBlank() ? "DAY" : timeInForce);
        double anchorPrice = price > 0d ? price : 100.25d;
        double nextPrice = requiresLimitPrice(nextOrderType)
                ? roundPrice(anchorPrice * (0.92d + normalizedFraction(priceSeed) * 0.18d))
                : 0d;
        double nextStopPrice = requiresStopPrice(nextOrderType)
                ? roundPrice(Math.max(anchorPrice * (0.96d + normalizedFraction(stopSeed) * 0.10d), 0.01d))
                : 0d;

        return new TheFixOrderRequest(
                messageTypeCode,
                "",
                "",
                nextSymbol,
                nextSide,
                nextQuantity,
                nextPrice,
                nextStopPrice,
                nextTimeInForce,
                nextOrderType,
                priceType == null || priceType.isBlank() ? "PER_UNIT" : priceType,
                region,
                market,
                currency,
                additionalTags
        );
    }

    String summary() {
        StringBuilder summary = new StringBuilder(messageType().shortLabel()).append(" · ");
        if (messageType().requiresOrigClOrdId() && origClOrdId != null && !origClOrdId.isBlank()) {
            summary.append("orig ").append(origClOrdId).append(" · ");
        }

        summary.append(side).append(' ');
        if (quantity > 0) {
            summary.append(quantity).append(' ');
        }
        summary.append(symbol);

        if (messageType() != TheFixMessageType.ORDER_CANCEL_REQUEST) {
            summary.append(' ').append(orderType).append(' ').append(timeInForce);
            if (requiresLimitPrice()) {
                summary.append(" @ ").append(String.format(Locale.US, "%.2f", price));
            }
            if (requiresStopPrice()) {
                summary.append(" stop ").append(String.format(Locale.US, "%.2f", stopPrice));
            }
        }

        if (market != null && !market.isBlank()) {
            summary.append(" · ").append(market);
        }
        if (currency != null && !currency.isBlank()) {
            summary.append('/').append(currency);
        }
        if (!additionalTags.isEmpty()) {
            summary.append(" · +").append(additionalTags.size()).append(" tag");
            if (additionalTags.size() != 1) {
                summary.append('s');
            }
        }
        return summary.toString();
    }

    TheFixMessageType messageType() {
        return TheFixMessageType.fromCode(messageTypeCode);
    }

    String outboundClOrdIdOr(String fallback) {
        return clOrdId == null || clOrdId.isBlank() ? fallback : clOrdId.trim();
    }

    char fixSide() {
        return switch (side.toUpperCase(Locale.US)) {
            case "SELL" -> Side.SELL;
            case "SELL_SHORT" -> Side.SELL_SHORT;
            default -> Side.BUY;
        };
    }

    char fixTimeInForce() {
        return switch (timeInForce.toUpperCase(Locale.US)) {
            case "IOC" -> '3';
            case "FOK" -> '4';
            case "GTC" -> '1';
            case "GTD" -> '6';
            case "OPG" -> '2';
            default -> '0';
        };
    }

    char fixOrdType() {
        return switch (orderType.toUpperCase(Locale.US)) {
            case "MARKET" -> OrdType.MARKET;
            case "STOP" -> OrdType.STOP;
            case "STOP_LIMIT" -> OrdType.STOP_LIMIT;
            case "MARKET_ON_CLOSE" -> OrdType.MARKET_ON_CLOSE;
            case "LIMIT_ON_CLOSE" -> OrdType.LIMIT_ON_CLOSE;
            case "PEGGED" -> OrdType.PEGGED;
            default -> OrdType.LIMIT;
        };
    }

    int fixPriceType() {
        return switch (priceType.toUpperCase(Locale.US)) {
            case "PERCENTAGE" -> 1;
            case "FIXED_AMOUNT" -> 3;
            case "YIELD" -> 9;
            case "SPREAD" -> 6;
            default -> 2;
        };
    }

    boolean requiresLimitPrice() {
        if (messageType() == TheFixMessageType.ORDER_CANCEL_REQUEST) {
            return false;
        }
        return requiresLimitPrice(orderType);
    }

    private static boolean requiresLimitPrice(String candidateOrderType) {
        return switch (normalizeCode(candidateOrderType, "LIMIT")) {
            case "LIMIT", "STOP_LIMIT", "LIMIT_ON_CLOSE" -> true;
            default -> false;
        };
    }

    boolean requiresStopPrice() {
        if (messageType() == TheFixMessageType.ORDER_CANCEL_REQUEST) {
            return false;
        }
        return requiresStopPrice(orderType);
    }

    private static boolean requiresStopPrice(String candidateOrderType) {
        return switch (normalizeCode(candidateOrderType, "LIMIT")) {
            case "STOP", "STOP_LIMIT" -> true;
            default -> false;
        };
    }

    private String pickSymbol(long seed) {
        List<String> symbols = BULK_SYMBOLS_BY_MARKET.getOrDefault(normalizeCode(market, ""), List.of(symbol == null || symbol.isBlank() ? "AAPL" : symbol));
        return pick(symbols, seed, symbol == null || symbol.isBlank() ? symbols.getFirst() : symbol);
    }

    private static <T> T pick(List<T> candidates, long seed, T fallback) {
        if (candidates == null || candidates.isEmpty()) {
            return fallback;
        }
        int index = Math.floorMod((int) Long.remainderUnsigned(seed, candidates.size()), candidates.size());
        return candidates.get(index);
    }

    private static double normalizedFraction(long seed) {
        long positive = seed & Long.MAX_VALUE;
        return (positive % 10_000L) / 10_000d;
    }

    private static double roundPrice(double value) {
        return Math.round(Math.max(value, 0.01d) * 100d) / 100d;
    }

    private static long mix(long value) {
        long mixed = value;
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);
        return mixed;
    }

    private static String normalizeCode(String raw, String fallback) {
        return (raw == null || raw.isBlank() ? fallback : raw).trim().toUpperCase(Locale.US);
    }
}

