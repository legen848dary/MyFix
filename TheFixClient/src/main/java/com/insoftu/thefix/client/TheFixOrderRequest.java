package com.insoftu.thefix.client;

import quickfix.field.OrdType;
import quickfix.field.Side;

import java.util.List;
import java.util.Locale;

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
    TheFixOrderRequest {
        additionalTags = additionalTags == null ? List.of() : List.copyOf(additionalTags);
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
        return switch (orderType.toUpperCase(Locale.US)) {
            case "LIMIT", "STOP_LIMIT", "LIMIT_ON_CLOSE" -> true;
            default -> false;
        };
    }

    boolean requiresStopPrice() {
        if (messageType() == TheFixMessageType.ORDER_CANCEL_REQUEST) {
            return false;
        }
        return switch (orderType.toUpperCase(Locale.US)) {
            case "STOP", "STOP_LIMIT" -> true;
            default -> false;
        };
    }
}

