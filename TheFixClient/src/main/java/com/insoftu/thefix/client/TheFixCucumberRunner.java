package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Gherkin feature-file runner for TheFixClient.
 * <p>
 * Parses a Cucumber/Gherkin feature file and executes each step against
 * the live {@link TheFixClientWorkbenchState}.  No external Cucumber
 * library is required – the parser and step-definition matching are
 * implemented here to keep the dependency footprint minimal.
 * </p>
 */
final class TheFixCucumberRunner {

    private static final Logger log = LoggerFactory.getLogger(TheFixCucumberRunner.class);

    private static final double DEFAULT_BULK_ORDER_PRICE = 100.25;

    // -----------------------------------------------------------------------
    // Step patterns
    // -----------------------------------------------------------------------

    private static final List<StepDefinition> STEP_DEFINITIONS = buildStepDefinitions();

    private final TheFixClientWorkbenchState workbenchState;

    TheFixCucumberRunner(TheFixClientWorkbenchState workbenchState) {
        this.workbenchState = workbenchState;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    JsonObject run(String featureText, String profileName) {
        ParsedFeature feature = parseFeature(featureText == null ? "" : featureText);
        JsonArray scenarioResults = new JsonArray();
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        for (ParsedScenario scenario : feature.scenarios()) {
            RunContext ctx = new RunContext(profileName);
            JsonObject result = executeScenario(scenario, feature.backgroundSteps(), ctx);
            scenarioResults.add(result);
            switch (result.getString("status", "FAILED")) {
                case "PASSED" -> passed++;
                case "SKIPPED" -> skipped++;
                default -> failed++;
            }
        }

        String overallStatus = feature.scenarios().isEmpty() ? "UNDEFINED"
                : failed > 0 ? "FAILED" : "PASSED";

        return new JsonObject()
                .put("status", overallStatus)
                .put("featureName", feature.name())
                .put("totalScenarios", feature.scenarios().size())
                .put("passed", passed)
                .put("failed", failed)
                .put("skipped", skipped)
                .put("scenarios", scenarioResults);
    }

    // -----------------------------------------------------------------------
    // Gherkin parser
    // -----------------------------------------------------------------------

    private static ParsedFeature parseFeature(String text) {
        String featureName = "";
        List<ParsedStep> backgroundSteps = new ArrayList<>();
        List<ParsedScenario> scenarios = new ArrayList<>();

        String[] rawLines = text.split("\r?\n", -1);
        List<String> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            lines.add(rawLine.stripTrailing());
        }

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).stripLeading();

            if (line.startsWith("Feature:")) {
                featureName = line.substring("Feature:".length()).strip();
                i++;
                continue;
            }

            if (line.startsWith("Background:")) {
                i++;
                List<ParsedStep> steps = new ArrayList<>();
                while (i < lines.size()) {
                    String stepLine = lines.get(i).stripLeading();
                    if (isStepKeyword(stepLine)) {
                        steps.add(parseStep(stepLine));
                        i++;
                    } else if (stepLine.isBlank() || stepLine.startsWith("#") || stepLine.startsWith("@")) {
                        i++;
                    } else {
                        break;
                    }
                }
                backgroundSteps = steps;
                continue;
            }

            if (line.startsWith("Scenario Outline:") || line.startsWith("Scenario Template:")) {
                String prefix = line.startsWith("Scenario Outline:") ? "Scenario Outline:" : "Scenario Template:";
                String templateName = line.substring(prefix.length()).strip();
                i++;
                List<ParsedStep> templateSteps = new ArrayList<>();
                List<List<String>> exampleHeaders = new ArrayList<>();
                List<List<List<String>>> exampleRows = new ArrayList<>();

                while (i < lines.size()) {
                    String sLine = lines.get(i).stripLeading();
                    if (isStepKeyword(sLine)) {
                        templateSteps.add(parseStep(sLine));
                        i++;
                    } else if (sLine.startsWith("Examples:") || sLine.startsWith("Scenarios:")) {
                        i++;
                        // parse header row
                        while (i < lines.size() && lines.get(i).stripLeading().isBlank()) {
                            i++;
                        }
                        if (i < lines.size() && lines.get(i).stripLeading().startsWith("|")) {
                            exampleHeaders.add(parseTableRow(lines.get(i)));
                            i++;
                        }
                        List<List<String>> rows = new ArrayList<>();
                        while (i < lines.size() && lines.get(i).stripLeading().startsWith("|")) {
                            rows.add(parseTableRow(lines.get(i)));
                            i++;
                        }
                        exampleRows.add(rows);
                    } else if (sLine.isBlank() || sLine.startsWith("#") || sLine.startsWith("@")) {
                        i++;
                    } else {
                        break;
                    }
                }

                // expand outline into concrete scenarios
                for (int exIdx = 0; exIdx < exampleHeaders.size(); exIdx++) {
                    List<String> headers = exampleHeaders.get(exIdx);
                    List<List<String>> rows = exampleRows.get(exIdx);
                    for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                        List<String> row = rows.get(rowIdx);
                        Map<String, String> substitutions = new LinkedHashMap<>();
                        for (int h = 0; h < headers.size() && h < row.size(); h++) {
                            substitutions.put(headers.get(h).strip(), row.get(h).strip());
                        }
                        List<ParsedStep> expandedSteps = new ArrayList<>();
                        for (ParsedStep tmplStep : templateSteps) {
                            String expandedText = applySubstitutions(tmplStep.text(), substitutions);
                            expandedSteps.add(new ParsedStep(tmplStep.keyword(), expandedText));
                        }
                        scenarios.add(new ParsedScenario(
                                templateName + " · example " + (rowIdx + 1),
                                expandedSteps));
                    }
                }
                continue;
            }

            if (line.startsWith("Scenario:")) {
                String scenarioName = line.substring("Scenario:".length()).strip();
                i++;
                List<ParsedStep> steps = new ArrayList<>();
                while (i < lines.size()) {
                    String sLine = lines.get(i).stripLeading();
                    if (isStepKeyword(sLine)) {
                        steps.add(parseStep(sLine));
                        i++;
                    } else if (sLine.isBlank() || sLine.startsWith("#") || sLine.startsWith("@")) {
                        i++;
                    } else {
                        break;
                    }
                }
                scenarios.add(new ParsedScenario(scenarioName, steps));
                continue;
            }

            i++;
        }

        return new ParsedFeature(featureName, backgroundSteps, scenarios);
    }

    private static boolean isStepKeyword(String line) {
        return line.startsWith("Given ") || line.startsWith("When ")
                || line.startsWith("Then ") || line.startsWith("And ")
                || line.startsWith("But ");
    }

    private static ParsedStep parseStep(String line) {
        for (String kw : List.of("Given ", "When ", "Then ", "And ", "But ")) {
            if (line.startsWith(kw)) {
                return new ParsedStep(kw.strip(), line.substring(kw.length()).strip());
            }
        }
        return new ParsedStep("Given", line.strip());
    }

    private static List<String> parseTableRow(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("|")) {
            stripped = stripped.substring(1);
        }
        if (stripped.endsWith("|")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        String[] cells = stripped.split("\\|", -1);
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cell.strip());
        }
        return result;
    }

    private static String applySubstitutions(String text, Map<String, String> substitutions) {
        String result = text;
        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Scenario executor
    // -----------------------------------------------------------------------

    private JsonObject executeScenario(ParsedScenario scenario,
                                       List<ParsedStep> backgroundSteps,
                                       RunContext ctx) {
        JsonArray stepResults = new JsonArray();
        boolean scenarioFailed = false;

        // Execute background steps first
        for (ParsedStep bgStep : backgroundSteps) {
            StepResult result = scenarioFailed
                    ? new StepResult(bgStep.keyword(), bgStep.text(), "SKIPPED", "Previous step failed")
                    : executeStep(bgStep, ctx);
            stepResults.add(stepResultJson(result));
            if ("FAILED".equals(result.status())) {
                scenarioFailed = true;
            }
        }

        // Execute scenario steps
        for (ParsedStep step : scenario.steps()) {
            StepResult result = scenarioFailed
                    ? new StepResult(step.keyword(), step.text(), "SKIPPED", "Previous step failed")
                    : executeStep(step, ctx);
            stepResults.add(stepResultJson(result));
            if ("FAILED".equals(result.status())) {
                scenarioFailed = true;
            }
        }

        String status = scenarioFailed ? "FAILED" : "PASSED";
        return new JsonObject()
                .put("name", scenario.name())
                .put("status", status)
                .put("steps", stepResults);
    }

    private static JsonObject stepResultJson(StepResult result) {
        return new JsonObject()
                .put("keyword", result.keyword())
                .put("text", result.text())
                .put("status", result.status())
                .put("message", result.message());
    }

    // -----------------------------------------------------------------------
    // Step executor
    // -----------------------------------------------------------------------

    private StepResult executeStep(ParsedStep step, RunContext ctx) {
        for (StepDefinition def : STEP_DEFINITIONS) {
            Matcher matcher = def.pattern().matcher(step.text());
            if (matcher.matches()) {
                try {
                    String message = def.execute(matcher, ctx, workbenchState);
                    return new StepResult(step.keyword(), step.text(), "PASSED", message);
                } catch (StepException e) {
                    return new StepResult(step.keyword(), step.text(), "FAILED", e.getMessage());
                } catch (Exception e) {
                    log.warn("Unexpected error executing Cucumber step: {}", step.text(), e);
                    return new StepResult(step.keyword(), step.text(), "FAILED",
                            "Unexpected error: " + e.getMessage());
                }
            }
        }
        return new StepResult(step.keyword(), step.text(), "FAILED",
                "No matching step definition found for: " + step.text());
    }

    // -----------------------------------------------------------------------
    // Step definitions
    // -----------------------------------------------------------------------

    @FunctionalInterface
    interface StepAction {
        String execute(Matcher matcher, RunContext ctx, TheFixClientWorkbenchState state)
                throws StepException;
    }

    private record StepDefinition(Pattern pattern, StepAction action) {
        String execute(Matcher matcher, RunContext ctx, TheFixClientWorkbenchState state)
                throws StepException {
            return action.execute(matcher, ctx, state);
        }
    }

    private static List<StepDefinition> buildStepDefinitions() {
        List<StepDefinition> defs = new ArrayList<>();

        // ----------------------------------------------------------------
        // Session lifecycle steps
        // ----------------------------------------------------------------

        // "the FIX session is connected"
        defs.add(def(
                "the FIX session is connected",
                (m, ctx, state) -> {
                    JsonObject result = state.connect(profileReq(ctx));
                    boolean connected = result.getJsonObject("session", new JsonObject()).getBoolean("connected", false);
                    if (!connected) {
                        String status = result.getJsonObject("session", new JsonObject()).getString("status", "unknown");
                        if (status.toLowerCase(Locale.ROOT).contains("connect")) {
                            return "Connect initiated — session is connecting (status: " + status + ")";
                        }
                        throw new StepException("Connect request sent but session is not yet connected (status: " + status + "). Ensure the FIX simulator is running.");
                    }
                    return "FIX session connected successfully";
                }));

        // "the FIX session for profile {string} is connected"
        defs.add(def(
                "the FIX session for profile \"([^\"]+)\" is connected",
                (m, ctx, state) -> {
                    ctx.profileName = m.group(1);
                    JsonObject result = state.connect(profileReq(ctx));
                    boolean connected = result.getJsonObject("session", new JsonObject()).getBoolean("connected", false);
                    if (!connected) {
                        String status = result.getJsonObject("session", new JsonObject()).getString("status", "unknown");
                        if (status.toLowerCase(Locale.ROOT).contains("connect")) {
                            return "Connect initiated for profile \"" + ctx.profileName + "\" (status: " + status + ")";
                        }
                        throw new StepException("Session for profile \"" + ctx.profileName + "\" is not connected (status: " + status + ")");
                    }
                    return "FIX session for profile \"" + ctx.profileName + "\" connected successfully";
                }));

        // "the FIX session is disconnected"
        defs.add(def(
                "the FIX session is disconnected",
                (m, ctx, state) -> {
                    state.disconnect(profileReq(ctx));
                    return "FIX session disconnect requested";
                }));

        // "I wait {int} second(s)"
        defs.add(def(
                "I wait (\\d+) seconds?",
                (m, ctx, state) -> {
                    int secs = Integer.parseInt(m.group(1));
                    int capped = Math.min(secs, 60);
                    if (capped > 0) {
                        try {
                            Thread.sleep(capped * 1000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return "Waited " + capped + " second(s)";
                }));

        // ----------------------------------------------------------------
        // Single order steps
        // ----------------------------------------------------------------

        // "I send a New Order Single to buy/sell {int} shares of {string} at {double}"
        defs.add(def(
                "I send a New Order Single to (buy|sell|sell short) (\\d+) shares? of \"([^\"]+)\" at ([\\d.]+)",
                (m, ctx, state) -> {
                    String side = normalizeSide(m.group(1));
                    int qty = Integer.parseInt(m.group(2));
                    String symbol = m.group(3);
                    double price = Double.parseDouble(m.group(4));
                    JsonObject req = nosRequest(ctx, symbol, side, qty, "LIMIT", price, 0d, "DAY");
                    state.sendOrder(req);
                    return "Sent LIMIT NOS: " + side + " " + qty + " " + symbol + " @ " + price;
                }));

        // "I send a New Order Single for {int} shares of {string}"  (market)
        defs.add(def(
                "I send a New Order Single for (\\d+) shares? of \"([^\"]+)\"",
                (m, ctx, state) -> {
                    int qty = Integer.parseInt(m.group(1));
                    String symbol = m.group(2);
                    JsonObject req = nosRequest(ctx, symbol, "BUY", qty, "MARKET", 0d, 0d, "DAY");
                    state.sendOrder(req);
                    return "Sent MARKET NOS: BUY " + qty + " " + symbol;
                }));

        // "I send a New Order Single to buy/sell {int} shares of {string} as MARKET/LIMIT/STOP/STOP_LIMIT order"
        defs.add(def(
                "I send a New Order Single to (buy|sell|sell short) (\\d+) shares? of \"([^\"]+)\" as (MARKET|LIMIT|STOP|STOP_LIMIT|MARKET_ON_CLOSE|LIMIT_ON_CLOSE|PEGGED) order",
                (m, ctx, state) -> {
                    String side = normalizeSide(m.group(1));
                    int qty = Integer.parseInt(m.group(2));
                    String symbol = m.group(3);
                    String orderType = m.group(4).toUpperCase(Locale.ROOT);
                    double price = "LIMIT".equals(orderType) || "STOP_LIMIT".equals(orderType) || "LIMIT_ON_CLOSE".equals(orderType) ? 100.25 : 0d;
                    double stop = "STOP".equals(orderType) || "STOP_LIMIT".equals(orderType) ? 99.50 : 0d;
                    JsonObject req = nosRequest(ctx, symbol, side, qty, orderType, price, stop, "DAY");
                    state.sendOrder(req);
                    return "Sent " + orderType + " NOS: " + side + " " + qty + " " + symbol;
                }));

        // "I send a {string} New Order Single for {int} shares of {string} at {double} with TIF {string}"
        defs.add(def(
                "I send a (BUY|SELL|SELL_SHORT) New Order Single for (\\d+) shares? of \"([^\"]+)\" at ([\\d.]+) with TIF (DAY|IOC|FOK|GTC|GTD|OPG)",
                (m, ctx, state) -> {
                    String side = m.group(1).toUpperCase(Locale.ROOT);
                    int qty = Integer.parseInt(m.group(2));
                    String symbol = m.group(3);
                    double price = Double.parseDouble(m.group(4));
                    String tif = m.group(5).toUpperCase(Locale.ROOT);
                    JsonObject req = nosRequest(ctx, symbol, side, qty, "LIMIT", price, 0d, tif);
                    state.sendOrder(req);
                    return "Sent LIMIT NOS: " + side + " " + qty + " " + symbol + " @ " + price + " TIF=" + tif;
                }));

        // "I send a STOP order to {side} {int} shares of {string} at stop {double}"
        defs.add(def(
                "I send a STOP order to (buy|sell|sell short) (\\d+) shares? of \"([^\"]+)\" at stop ([\\d.]+)",
                (m, ctx, state) -> {
                    String side = normalizeSide(m.group(1));
                    int qty = Integer.parseInt(m.group(2));
                    String symbol = m.group(3);
                    double stop = Double.parseDouble(m.group(4));
                    JsonObject req = nosRequest(ctx, symbol, side, qty, "STOP", 0d, stop, "DAY");
                    state.sendOrder(req);
                    return "Sent STOP NOS: " + side + " " + qty + " " + symbol + " stop @ " + stop;
                }));

        // "I send a STOP_LIMIT order to {side} {int} shares of {string} at limit {double} stop {double}"
        defs.add(def(
                "I send a STOP_LIMIT order to (buy|sell|sell short) (\\d+) shares? of \"([^\"]+)\" at limit ([\\d.]+) stop ([\\d.]+)",
                (m, ctx, state) -> {
                    String side = normalizeSide(m.group(1));
                    int qty = Integer.parseInt(m.group(2));
                    String symbol = m.group(3);
                    double price = Double.parseDouble(m.group(4));
                    double stop = Double.parseDouble(m.group(5));
                    JsonObject req = nosRequest(ctx, symbol, side, qty, "STOP_LIMIT", price, stop, "DAY");
                    state.sendOrder(req);
                    return "Sent STOP_LIMIT NOS: " + side + " " + qty + " " + symbol + " limit=" + price + " stop=" + stop;
                }));

        // "I send a New Order Single for {int} shares of {string} on market {string}"
        defs.add(def(
                "I send a New Order Single for (\\d+) shares? of \"([^\"]+)\" on market \"([^\"]+)\"",
                (m, ctx, state) -> {
                    int qty = Integer.parseInt(m.group(1));
                    String symbol = m.group(2);
                    String market = m.group(3).toUpperCase(Locale.ROOT);
                    JsonObject req = nosRequest(ctx, symbol, "BUY", qty, "LIMIT", 100.25, 0d, "DAY")
                            .put("market", market);
                    state.sendOrder(req);
                    return "Sent NOS for " + qty + " " + symbol + " on " + market;
                }));

        // ----------------------------------------------------------------
        // Order cancel / amend steps
        // ----------------------------------------------------------------

        // "I cancel the order with ClOrdID {string}"
        defs.add(def(
                "I cancel the order with ClOrdID \"([^\"]+)\"",
                (m, ctx, state) -> {
                    String clOrdId = m.group(1);
                    JsonObject req = profileReq(ctx).put("clOrdId", clOrdId);
                    JsonObject result = state.cancelBlotterOrder(req);
                    JsonObject ar = result.getJsonObject("actionResult", new JsonObject());
                    if (!ar.getBoolean("success", false)) {
                        throw new StepException("Cancel failed: " + ar.getString("message", "unknown"));
                    }
                    return "Cancel submitted for ClOrdID=" + clOrdId;
                }));

        // "I cancel the most recently sent order"
        defs.add(def(
                "I cancel the most recently sent order",
                (m, ctx, state) -> {
                    String lastClOrdId = ctx.lastClOrdId;
                    if (lastClOrdId == null || lastClOrdId.isBlank()) {
                        throw new StepException("No order was sent in this scenario yet");
                    }
                    JsonObject req = profileReq(ctx).put("clOrdId", lastClOrdId);
                    JsonObject result = state.cancelBlotterOrder(req);
                    JsonObject ar = result.getJsonObject("actionResult", new JsonObject());
                    if (!ar.getBoolean("success", false)) {
                        throw new StepException("Cancel failed: " + ar.getString("message", "unknown"));
                    }
                    return "Cancel submitted for ClOrdID=" + lastClOrdId;
                }));

        // "I amend the order with ClOrdID {string} to quantity {int} and price {double}"
        defs.add(def(
                "I amend the order with ClOrdID \"([^\"]+)\" to quantity (\\d+) and price ([\\d.]+)",
                (m, ctx, state) -> {
                    String clOrdId = m.group(1);
                    int qty = Integer.parseInt(m.group(2));
                    double price = Double.parseDouble(m.group(3));
                    JsonObject req = profileReq(ctx)
                            .put("clOrdId", clOrdId)
                            .put("quantity", qty)
                            .put("price", price);
                    JsonObject result = state.amendBlotterOrder(req);
                    JsonObject ar = result.getJsonObject("actionResult", new JsonObject());
                    if (!ar.getBoolean("success", false)) {
                        throw new StepException("Amend failed: " + ar.getString("message", "unknown"));
                    }
                    return "Amend submitted for ClOrdID=" + clOrdId + " qty=" + qty + " price=" + price;
                }));

        // ----------------------------------------------------------------
        // Bulk order flow steps
        // ----------------------------------------------------------------

        // "I start a fixed rate bulk flow at {int} orders per second"
        defs.add(def(
                "I start a fixed rate bulk flow at (\\d+) orders? per second",
                (m, ctx, state) -> {
                    int rate = Integer.parseInt(m.group(1));
                    JsonObject req = bulkRequest(ctx, "FIXED_RATE", rate, 0, 10, 1000, 0);
                    state.startOrderFlow(req);
                    return "Started FIXED_RATE bulk flow at " + rate + " orders/sec (continuous)";
                }));

        // "I start a fixed rate bulk flow of {int} total orders at {int} orders per second"
        defs.add(def(
                "I start a fixed rate bulk flow of (\\d+) total orders? at (\\d+) orders? per second",
                (m, ctx, state) -> {
                    int total = Integer.parseInt(m.group(1));
                    int rate = Integer.parseInt(m.group(2));
                    JsonObject req = bulkRequest(ctx, "FIXED_RATE", rate, 0, 10, 1000, total);
                    state.startOrderFlow(req);
                    return "Started FIXED_RATE bulk flow: " + total + " orders at " + rate + " orders/sec";
                }));

        // "I start a burst bulk flow with {int} orders per burst every {int} ms"
        defs.add(def(
                "I start a burst bulk flow with (\\d+) orders? per burst every (\\d+) ms",
                (m, ctx, state) -> {
                    int burstSize = Integer.parseInt(m.group(1));
                    int intervalMs = Integer.parseInt(m.group(2));
                    JsonObject req = bulkRequest(ctx, "BURST", 10, 0, burstSize, intervalMs, 0);
                    state.startOrderFlow(req);
                    return "Started BURST bulk flow: " + burstSize + " orders every " + intervalMs + "ms (continuous)";
                }));

        // "I start a burst bulk flow of {int} total orders with {int} per burst every {int} ms"
        defs.add(def(
                "I start a burst bulk flow of (\\d+) total orders? with (\\d+) per burst every (\\d+) ms",
                (m, ctx, state) -> {
                    int total = Integer.parseInt(m.group(1));
                    int burstSize = Integer.parseInt(m.group(2));
                    int intervalMs = Integer.parseInt(m.group(3));
                    JsonObject req = bulkRequest(ctx, "BURST", 10, 0, burstSize, intervalMs, total);
                    state.startOrderFlow(req);
                    return "Started BURST bulk flow: " + total + " total, " + burstSize + " per burst every " + intervalMs + "ms";
                }));

        // "I stop the bulk order flow"
        defs.add(def(
                "I stop the bulk order flow",
                (m, ctx, state) -> {
                    state.stopOrderFlow(profileReq(ctx));
                    return "Bulk order flow stopped";
                }));

        // ----------------------------------------------------------------
        // Verification steps (Then / And)
        // ----------------------------------------------------------------

        // "the session should be connected"
        defs.add(def(
                "the session should be connected",
                (m, ctx, state) -> {
                    JsonObject snap = state.snapshot(ctx.profileName);
                    boolean connected = snap.getJsonObject("session", new JsonObject()).getBoolean("connected", false);
                    if (!connected) {
                        throw new StepException("Expected session to be connected but it is not");
                    }
                    return "Session is connected";
                }));

        // "the session should not be connected"
        defs.add(def(
                "the session should not be connected",
                (m, ctx, state) -> {
                    JsonObject snap = state.snapshot(ctx.profileName);
                    boolean connected = snap.getJsonObject("session", new JsonObject()).getBoolean("connected", false);
                    if (connected) {
                        throw new StepException("Expected session to be disconnected but it is connected");
                    }
                    return "Session is not connected";
                }));

        // "the sent orders count should be at least {int}"
        defs.add(def(
                "the sent orders count should be at least (\\d+)",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("sentOrders", 0);
                    if (actual < expected) {
                        throw new StepException("Expected sentOrders >= " + expected + " but was " + actual);
                    }
                    return "sentOrders=" + actual + " (expected >= " + expected + ")";
                }));

        // "the sent orders count should be exactly {int}"
        defs.add(def(
                "the sent orders count should be exactly (\\d+)",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("sentOrders", 0);
                    if (actual != expected) {
                        throw new StepException("Expected sentOrders == " + expected + " but was " + actual);
                    }
                    return "sentOrders=" + actual;
                }));

        // "the execution report count should be at least {int}"
        defs.add(def(
                "the execution report count should be at least (\\d+)",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("executionReports", 0);
                    if (actual < expected) {
                        throw new StepException("Expected executionReports >= " + expected + " but was " + actual);
                    }
                    return "executionReports=" + actual + " (expected >= " + expected + ")";
                }));

        // "the order blotter should contain at least {int} orders"
        defs.add(def(
                "the order blotter should contain at least (\\d+) orders?",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonArray("recentOrders", new JsonArray()).size();
                    if (actual < expected) {
                        throw new StepException("Expected blotter size >= " + expected + " but was " + actual);
                    }
                    return "Order blotter contains " + actual + " order(s) (expected >= " + expected + ")";
                }));

        // "the order blotter should contain an order with symbol {string}"
        defs.add(def(
                "the order blotter should contain an order with symbol \"([^\"]+)\"",
                (m, ctx, state) -> {
                    String symbol = m.group(1);
                    JsonObject snap = state.snapshot(ctx.profileName);
                    JsonArray orders = snap.getJsonArray("recentOrders", new JsonArray());
                    boolean found = false;
                    for (int i = 0; i < orders.size(); i++) {
                        if (symbol.equalsIgnoreCase(orders.getJsonObject(i).getString("symbol", ""))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new StepException("No order found with symbol \"" + symbol + "\" in the blotter");
                    }
                    return "Found order with symbol \"" + symbol + "\" in blotter";
                }));

        // "the order blotter should contain an order with status {string}"
        defs.add(def(
                "the order blotter should contain an order with status \"([^\"]+)\"",
                (m, ctx, state) -> {
                    String status = m.group(1).toUpperCase(Locale.ROOT);
                    JsonObject snap = state.snapshot(ctx.profileName);
                    JsonArray orders = snap.getJsonArray("recentOrders", new JsonArray());
                    boolean found = false;
                    for (int i = 0; i < orders.size(); i++) {
                        String orderStatus = orders.getJsonObject(i).getString("status", "").toUpperCase(Locale.ROOT);
                        if (orderStatus.contains(status)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new StepException("No order found with status containing \"" + status + "\" in the blotter");
                    }
                    return "Found order with status \"" + status + "\" in blotter";
                }));

        // "the FIX tape should contain at least {int} messages"
        defs.add(def(
                "the FIX tape should contain at least (\\d+) messages?",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject msgData = state.recentFixMessages(Math.max(expected, 1), 0, ctx.profileName);
                    int actual = msgData.getInteger("total", 0);
                    if (actual < expected) {
                        throw new StepException("Expected FIX tape >= " + expected + " messages but had " + actual);
                    }
                    return "FIX tape has " + actual + " message(s) (expected >= " + expected + ")";
                }));

        // "the cancel count should be at least {int}"
        defs.add(def(
                "the cancel count should be at least (\\d+)",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("cancels", 0);
                    if (actual < expected) {
                        throw new StepException("Expected cancels >= " + expected + " but was " + actual);
                    }
                    return "cancels=" + actual + " (expected >= " + expected + ")";
                }));

        // "the reject count should be at most {int}"
        defs.add(def(
                "the reject count should be at most (\\d+)",
                (m, ctx, state) -> {
                    int max = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("rejects", 0);
                    if (actual > max) {
                        throw new StepException("Expected rejects <= " + max + " but was " + actual);
                    }
                    return "rejects=" + actual + " (expected <= " + max + ")";
                }));

        // "the bulk flow should be running"
        defs.add(def(
                "the bulk flow should be running",
                (m, ctx, state) -> {
                    JsonObject snap = state.snapshot(ctx.profileName);
                    boolean active = snap.getJsonObject("session", new JsonObject()).getBoolean("autoFlowActive", false);
                    if (!active) {
                        throw new StepException("Expected bulk flow to be running but it is not");
                    }
                    return "Bulk flow is running";
                }));

        // "the bulk flow should not be running"
        defs.add(def(
                "the bulk flow should not be running",
                (m, ctx, state) -> {
                    JsonObject snap = state.snapshot(ctx.profileName);
                    boolean active = snap.getJsonObject("session", new JsonObject()).getBoolean("autoFlowActive", false);
                    if (active) {
                        throw new StepException("Expected bulk flow to be stopped but it is running");
                    }
                    return "Bulk flow is not running";
                }));

        // "the send failure count should be {int}"
        defs.add(def(
                "the send failure count should be (\\d+)",
                (m, ctx, state) -> {
                    int expected = Integer.parseInt(m.group(1));
                    JsonObject snap = state.snapshot(ctx.profileName);
                    int actual = snap.getJsonObject("kpis", new JsonObject()).getInteger("sendFailures", 0);
                    if (actual != expected) {
                        throw new StepException("Expected sendFailures == " + expected + " but was " + actual);
                    }
                    return "sendFailures=" + actual;
                }));

        return defs;
    }

    // -----------------------------------------------------------------------
    // Helper factories
    // -----------------------------------------------------------------------

    private static StepDefinition def(String pattern, StepAction action) {
        return new StepDefinition(
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE),
                action);
    }

    private static JsonObject profileReq(RunContext ctx) {
        return new JsonObject().put("profileName", ctx.profileName == null ? "" : ctx.profileName);
    }

    private static JsonObject nosRequest(RunContext ctx,
                                         String symbol,
                                         String side,
                                         int quantity,
                                         String orderType,
                                         double price,
                                         double stopPrice,
                                         String tif) {
        return profileReq(ctx)
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("clOrdId", "")
                .put("origClOrdId", "")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", symbol)
                .put("side", side)
                .put("quantity", quantity)
                .put("orderType", orderType)
                .put("priceType", "PER_UNIT")
                .put("timeInForce", tif)
                .put("currency", "USD")
                .put("price", price)
                .put("stopPrice", stopPrice)
                .put("additionalTags", new JsonArray());
    }

    private static JsonObject bulkRequest(RunContext ctx,
                                          String bulkMode,
                                          int ratePerSecond,
                                          double price,
                                          int burstSize,
                                          int burstIntervalMs,
                                          int totalOrders) {
        double effectivePrice = "FIXED_RATE".equals(bulkMode) || "BURST".equals(bulkMode) ? DEFAULT_BULK_ORDER_PRICE : price;
        return profileReq(ctx)
                .put("messageType", "NEW_ORDER_SINGLE")
                .put("clOrdId", "")
                .put("origClOrdId", "")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("symbol", "AAPL")
                .put("side", "BUY")
                .put("quantity", 100)
                .put("orderType", "LIMIT")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("currency", "USD")
                .put("price", effectivePrice)
                .put("stopPrice", 0d)
                .put("additionalTags", new JsonArray())
                .put("bulkMode", bulkMode)
                .put("ratePerSecond", ratePerSecond)
                .put("burstSize", burstSize)
                .put("burstIntervalMs", burstIntervalMs)
                .put("totalOrders", totalOrders);
    }

    private static String normalizeSide(String raw) {
        if (raw == null) return "BUY";
        return switch (raw.toLowerCase(Locale.ROOT).strip()) {
            case "sell short", "sell_short" -> "SELL_SHORT";
            case "sell" -> "SELL";
            default -> "BUY";
        };
    }

    // -----------------------------------------------------------------------
    // Internal data types
    // -----------------------------------------------------------------------

    private record ParsedFeature(String name, List<ParsedStep> backgroundSteps, List<ParsedScenario> scenarios) {}

    private record ParsedScenario(String name, List<ParsedStep> steps) {}

    private record ParsedStep(String keyword, String text) {}

    private record StepResult(String keyword, String text, String status, String message) {}

    static final class RunContext {
        String profileName;
        String lastClOrdId;

        RunContext(String profileName) {
            this.profileName = profileName;
        }
    }

    static final class StepException extends Exception {
        StepException(String message) {
            super(message);
        }
    }
}
