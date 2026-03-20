package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TheFixClientWorkbenchState implements AutoCloseable {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private final TheFixClientConfig config;
    private final TheFixClientFixService fixService;
    private final List<WatchlistItem> watchlist = List.of(
            new WatchlistItem("AAPL", "NASDAQ", "210.42", "+0.48%", "up"),
            new WatchlistItem("MSFT", "NASDAQ", "492.13", "+0.19%", "up"),
            new WatchlistItem("EUR/USD", "Spot FX", "1.0884", "-0.03%", "down"),
            new WatchlistItem("GBP/USD", "Spot FX", "1.2972", "+0.11%", "up")
    );
    private final List<String> launchChecklist = List.of(
            "Confirm FIX session host, port, and CompIDs with simulator settings.",
            "Prime the FIX initiator before routing manual tickets or demo-rate order flow.",
            "Preview trader intent and notional before releasing manual orders.",
            "Watch execution reports and rejects in the live event stream after every send."
    );

    private int pulseChecks;

    TheFixClientWorkbenchState(TheFixClientConfig config) {
        this(config, new TheFixClientFixService(config));
    }

    TheFixClientWorkbenchState(TheFixClientConfig config, TheFixClientFixService fixService) {
        this.config = config;
        this.fixService = fixService;
    }

    synchronized JsonObject snapshot() {
        JsonObject kpis = fixService.kpiSnapshot();
        kpis.put("pulseChecks", pulseChecks);

        return new JsonObject()
                .put("applicationName", "TheFixClient")
                .put("subtitle", "HSBC Trader Workstation")
                .put("environment", "Live simulator-linked FIX workstation")
                .put("generatedAt", Instant.now().toString())
                .put("session", fixService.sessionSnapshot())
                .put("kpis", kpis)
                .put("watchlist", buildWatchlist())
                .put("launchChecklist", new JsonArray(launchChecklist))
                .put("recentEvents", fixService.recentEventsJson())
                .put("recentOrders", fixService.recentOrdersJson())
                .put("defaults", new JsonObject().put("defaultFlowRate", config.defaultRatePerSecond()));
    }

    synchronized JsonObject connect() {
        fixService.connect();
        return snapshot();
    }

    synchronized JsonObject disconnect() {
        fixService.disconnect();
        return snapshot();
    }

    synchronized JsonObject pulseTest() {
        pulseChecks++;
        fixService.pulseTest();
        return snapshot();
    }

    synchronized JsonObject sendOrder(JsonObject request) {
        OrderPreview preview = buildPreview(request);
        if (!preview.warnings().isEmpty()) {
            return snapshot();
        }
        fixService.sendOrder(preview.toOrderRequest());
        return snapshot();
    }

    synchronized JsonObject startOrderFlow(JsonObject request) {
        OrderPreview preview = buildPreview(request);
        if (!preview.warnings().isEmpty()) {
            return snapshot();
        }
        int requestedRate = request == null ? config.defaultRatePerSecond() : Math.max(1, request.getInteger("ratePerSecond", config.defaultRatePerSecond()));
        fixService.startAutoFlow(preview.toOrderRequest(), requestedRate);
        return snapshot();
    }

    synchronized JsonObject stopOrderFlow() {
        fixService.stopAutoFlow();
        return snapshot();
    }

    synchronized JsonObject previewOrder(JsonObject request) {
        return buildPreview(request).toJson(fixService.isLoggedOn());
    }

    @Override
    public synchronized void close() {
        fixService.close();
    }

    private OrderPreview buildPreview(JsonObject request) {
        String symbol = sanitizeText(request == null ? null : request.getString("symbol"), "AAPL").toUpperCase(Locale.US);
        String side = sanitizeText(request == null ? null : request.getString("side"), "BUY").toUpperCase(Locale.US);
        String timeInForce = sanitizeText(request == null ? null : request.getString("timeInForce"), "DAY").toUpperCase(Locale.US);
        int quantity = request == null ? 100 : request.getInteger("quantity", 100);
        double rawPrice = request == null ? 100.25d : request.getDouble("price", 100.25d);

        List<String> warnings = new ArrayList<>();
        if (symbol.isBlank()) {
            warnings.add("Symbol is required.");
        }
        if (!side.equals("BUY") && !side.equals("SELL")) {
            warnings.add("Side must be BUY or SELL.");
        }
        if (!List.of("DAY", "IOC", "FOK").contains(timeInForce)) {
            warnings.add("Time in force must be DAY, IOC, or FOK.");
        }
        if (quantity <= 0) {
            warnings.add("Quantity must be greater than zero.");
        }
        if (rawPrice <= 0d) {
            warnings.add("Limit price must be greater than zero.");
        }

        BigDecimal price = BigDecimal.valueOf(Math.max(rawPrice, 0d)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal notional = price.multiply(BigDecimal.valueOf(Math.max(quantity, 0L))).setScale(2, RoundingMode.HALF_UP);
        String status = warnings.isEmpty()
                ? (fixService.isLoggedOn() ? "READY_FOR_ROUTING" : "READY_PENDING_CONNECTION")
                : "INVALID";

        String recommendation;
        if (!warnings.isEmpty()) {
            recommendation = "Resolve the highlighted validation issues before promoting this ticket to a live session.";
        } else if (fixService.isLoggedOn()) {
            recommendation = "Ticket is ready for live routing through the simulator-linked FIX initiator.";
        } else {
            recommendation = "Preview is valid. Prime the session before routing or starting the demo order flow.";
        }

        return new OrderPreview(symbol, side, quantity, price.doubleValue(), timeInForce, status,
                side + " " + quantity + " " + symbol + " @ " + price + " " + timeInForce,
                CURRENCY_FORMAT.format(notional), recommendation, warnings);
    }

    private JsonArray buildWatchlist() {
        JsonArray items = new JsonArray();
        for (WatchlistItem watchlistItem : watchlist) {
            items.add(watchlistItem.toJson());
        }
        return items;
    }

    private static String sanitizeText(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return raw.trim();
    }

    private record OrderPreview(
            String symbol,
            String side,
            int quantity,
            double price,
            String timeInForce,
            String status,
            String summary,
            String notional,
            String recommendation,
            List<String> warnings
    ) {
        JsonObject toJson(boolean connected) {
            return new JsonObject()
                    .put("status", status)
                    .put("summary", summary)
                    .put("notional", notional)
                    .put("recommendation", recommendation)
                    .put("connected", connected)
                    .put("warnings", new JsonArray(warnings));
        }

        TheFixClientFixService.OrderRequest toOrderRequest() {
            return new TheFixClientFixService.OrderRequest(symbol, side, quantity, price, timeInForce);
        }
    }

    private record WatchlistItem(String symbol, String venue, String last, String change, String trend) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("symbol", symbol)
                    .put("venue", venue)
                    .put("last", last)
                    .put("change", change)
                    .put("trend", trend);
        }
    }
}
