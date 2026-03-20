package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

final class TheFixClientWorkbenchState {
    private static final int MAX_EVENTS = 10;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

    private final Deque<WorkbenchEvent> recentEvents = new ArrayDeque<>();
    private final List<WatchlistItem> watchlist = List.of(
            new WatchlistItem("AAPL", "NASDAQ", "210.42", "+0.48%", "up"),
            new WatchlistItem("MSFT", "NASDAQ", "492.13", "+0.19%", "up"),
            new WatchlistItem("EUR/USD", "Spot FX", "1.0884", "-0.03%", "down"),
            new WatchlistItem("GBP/USD", "Spot FX", "1.2972", "+0.11%", "up")
    );
    private final List<String> launchChecklist = List.of(
            "Confirm FIX session host, port, and CompIDs with simulator settings.",
            "Validate trader profile defaults before enabling live routing actions.",
            "Review order preview warnings before sending to market gateways.",
            "Use session pulse checks to confirm workstation responsiveness during rehearsals."
    );

    private boolean connected;
    private Instant connectedAt;
    private int pulseChecks;

    TheFixClientWorkbenchState() {
        addEvent("INFO", "Desk initialized", "TheFixClient workstation shell is live and ready for UI review.");
        addEvent("INFO", "Next wiring step", "Session controls will be connected to the FIX demo client flow in the next checkpoint.");
    }

    synchronized JsonObject snapshot() {
        return new JsonObject()
                .put("applicationName", "TheFixClient")
                .put("subtitle", "HSBC Trader Workstation")
                .put("environment", "Local simulator integration checkpoint")
                .put("generatedAt", Instant.now().toString())
                .put("session", buildSession())
                .put("kpis", buildKpis())
                .put("watchlist", buildWatchlist())
                .put("launchChecklist", new JsonArray(launchChecklist))
                .put("recentEvents", buildEvents());
    }

    synchronized JsonObject connect() {
        if (connected) {
            addEvent("WARN", "Session already primed", "The workstation is already marked connected in shell mode.");
            return snapshot();
        }
        connected = true;
        connectedAt = Instant.now();
        addEvent("SUCCESS", "Session primed", "Desk controls switched to connected shell mode for UI validation.");
        return snapshot();
    }

    synchronized JsonObject disconnect() {
        if (!connected) {
            addEvent("WARN", "No active shell session", "Disconnect requested while the workstation was already idle.");
            return snapshot();
        }
        connected = false;
        connectedAt = null;
        addEvent("INFO", "Session released", "Desk controls returned to standby mode.");
        return snapshot();
    }

    synchronized JsonObject pulseTest() {
        pulseChecks++;
        addEvent("INFO", "Pulse check executed", "Workbench heartbeat and operator controls responded normally.");
        return snapshot();
    }

    synchronized JsonObject previewOrder(JsonObject request) {
        String symbol = sanitizeText(request.getString("symbol"), "AAPL");
        String side = sanitizeText(request.getString("side"), "BUY").toUpperCase(Locale.US);
        String timeInForce = sanitizeText(request.getString("timeInForce"), "DAY").toUpperCase(Locale.US);
        int quantity = request.getInteger("quantity", 100);
        double rawPrice = request.getDouble("price", 100.25d);

        List<String> warnings = new ArrayList<>();
        if (symbol.isBlank()) {
            warnings.add("Symbol is required.");
        }
        if (!side.equals("BUY") && !side.equals("SELL")) {
            warnings.add("Side must be BUY or SELL.");
        }
        if (quantity <= 0) {
            warnings.add("Quantity must be greater than zero.");
        }
        if (rawPrice <= 0) {
            warnings.add("Limit price must be greater than zero.");
        }

        BigDecimal price = BigDecimal.valueOf(Math.max(rawPrice, 0d)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal notional = price.multiply(BigDecimal.valueOf(Math.max(quantity, 0L))).setScale(2, RoundingMode.HALF_UP);

        String status = warnings.isEmpty()
                ? (connected ? "READY_FOR_ROUTING" : "READY_PENDING_CONNECTION")
                : "INVALID";

        String recommendation;
        if (!warnings.isEmpty()) {
            recommendation = "Resolve the highlighted validation issues before promoting this ticket to a live session.";
        } else if (connected) {
            recommendation = "Ticket looks good for the next checkpoint when live FIX wiring is enabled.";
        } else {
            recommendation = "Preview is valid. Prime the workstation session before rehearsing end-to-end routing.";
        }

        return new JsonObject()
                .put("status", status)
                .put("summary", side + " " + quantity + " " + symbol + " @ " + price + " " + timeInForce)
                .put("notional", CURRENCY_FORMAT.format(notional))
                .put("recommendation", recommendation)
                .put("connected", connected)
                .put("warnings", new JsonArray(warnings));
    }

    private JsonObject buildSession() {
        return new JsonObject()
                .put("connected", connected)
                .put("status", connected ? "Connected shell mode" : "Standby")
                .put("host", "localhost")
                .put("port", 9880)
                .put("beginString", "FIX.4.4")
                .put("senderCompId", "HSBC_TRDR01")
                .put("targetCompId", "LLEXSIM")
                .put("mode", "UI shell")
                .put("uptime", formatUptime());
    }

    private JsonObject buildKpis() {
        return new JsonObject()
                .put("readyState", connected ? "Desk primed" : "UI ready")
                .put("activeAlerts", connected ? 1 : 2)
                .put("openOrders", connected ? 6 : 0)
                .put("pulseChecks", pulseChecks)
                .put("sessionUptime", formatUptime());
    }

    private JsonArray buildWatchlist() {
        JsonArray items = new JsonArray();
        for (WatchlistItem watchlistItem : watchlist) {
            items.add(watchlistItem.toJson());
        }
        return items;
    }

    private JsonArray buildEvents() {
        JsonArray items = new JsonArray();
        for (WorkbenchEvent recentEvent : recentEvents) {
            items.add(recentEvent.toJson());
        }
        return items;
    }

    private void addEvent(String level, String title, String detail) {
        recentEvents.addFirst(new WorkbenchEvent(Instant.now(), level, title, detail));
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }
    }

    private String formatUptime() {
        if (!connected || connectedAt == null) {
            return "Not connected";
        }
        Duration duration = Duration.between(connectedAt, Instant.now());
        long seconds = duration.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + remainingSeconds + "s";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return hours + "h " + remainingMinutes + "m";
    }

    private static String sanitizeText(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return raw.trim();
    }

    private record WorkbenchEvent(Instant timestamp, String level, String title, String detail) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("time", EVENT_TIME_FORMAT.format(timestamp))
                    .put("level", level)
                    .put("title", title)
                    .put("detail", detail);
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

