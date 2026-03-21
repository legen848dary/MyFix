package com.insoftu.thefix.client;

import quickfix.field.OrdType;
import quickfix.field.Side;

import java.util.Locale;

record TheFixOrderRequest(
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
        String currency
) {
    String summary() {
        StringBuilder summary = new StringBuilder(side)
                .append(' ')
                .append(quantity)
                .append(' ')
                .append(symbol)
                .append(' ')
                .append(orderType)
                .append(' ')
                .append(timeInForce);
        if (requiresLimitPrice()) {
            summary.append(" @ ").append(String.format(Locale.US, "%.2f", price));
        }
        if (requiresStopPrice()) {
            summary.append(" stop ").append(String.format(Locale.US, "%.2f", stopPrice));
        }
        if (market != null && !market.isBlank()) {
            summary.append(" · ").append(market);
        }
        if (currency != null && !currency.isBlank()) {
            summary.append('/').append(currency);
        }
        return summary.toString();
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
        return switch (orderType.toUpperCase(Locale.US)) {
            case "LIMIT", "STOP_LIMIT", "LIMIT_ON_CLOSE" -> true;
            default -> false;
        };
    }

    boolean requiresStopPrice() {
        return switch (orderType.toUpperCase(Locale.US)) {
            case "STOP", "STOP_LIMIT" -> true;
            default -> false;
        };
    }
}

