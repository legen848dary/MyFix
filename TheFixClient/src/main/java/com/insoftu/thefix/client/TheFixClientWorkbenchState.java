package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TheFixClientWorkbenchState implements AutoCloseable {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final List<MarketDefinition> MARKETS = List.of(
            new MarketDefinition("ASIA", "XHKG", "Hong Kong", "HKEX", "HKD", "Equities", List.of("0700.HK", "9988.HK")),
            new MarketDefinition("ASIA", "XTKS", "Tokyo", "JPX", "JPY", "Equities", List.of("7203.T", "6758.T")),
            new MarketDefinition("ASIA", "XSES", "Singapore", "SGX", "SGD", "Equities", List.of("D05.SI", "O39.SI")),
            new MarketDefinition("ASIA", "XSHG", "Shanghai", "SSE", "CNY", "Equities", List.of("600519.SS", "601318.SS")),
            new MarketDefinition("EMEA", "XLON", "London", "LSEG", "GBP", "Equities", List.of("BP.L", "VOD.L")),
            new MarketDefinition("EMEA", "XPAR", "Paris", "Euronext Paris", "EUR", "Equities", List.of("AIR.PA", "BNP.PA")),
            new MarketDefinition("EMEA", "XETR", "Frankfurt", "Xetra", "EUR", "Equities", List.of("SAP.DE", "SIE.DE")),
            new MarketDefinition("EMEA", "XSWX", "Zurich", "SIX", "CHF", "Equities", List.of("NESN.SW", "NOVN.SW")),
            new MarketDefinition("AMERICAS", "XNAS", "New York", "NASDAQ", "USD", "Equities", List.of("AAPL", "MSFT")),
            new MarketDefinition("AMERICAS", "XNYS", "New York", "NYSE", "USD", "Equities", List.of("IBM", "GS")),
            new MarketDefinition("AMERICAS", "XTSE", "Toronto", "TSX", "CAD", "Equities", List.of("RY.TO", "SHOP.TO")),
            new MarketDefinition("AMERICAS", "BVMF", "Sao Paulo", "B3", "BRL", "Equities", List.of("PETR4", "VALE3"))
    );
    private static final Map<String, String> REGION_LABELS = Map.of(
            "ASIA", "Asia Pacific",
            "EMEA", "Europe, Middle East & Africa",
            "AMERICAS", "Americas"
    );
    private static final List<OptionDefinition> SIDE_OPTIONS = List.of(
            new OptionDefinition("BUY", "Buy", "Lift liquidity or aggress the offer."),
            new OptionDefinition("SELL", "Sell", "Hit liquidity or aggress the bid."),
            new OptionDefinition("SELL_SHORT", "Sell short", "Short-sale instruction routed with FIX side 5.")
    );
    private static final List<OrderTypeDefinition> ORDER_TYPES = List.of(
            new OrderTypeDefinition("MARKET", "Market", "Execute at the best available market price.", false, false),
            new OrderTypeDefinition("LIMIT", "Limit", "Rest or execute at the specified limit price.", true, false),
            new OrderTypeDefinition("STOP", "Stop", "Trigger into a market order when the stop price is reached.", false, true),
            new OrderTypeDefinition("STOP_LIMIT", "Stop limit", "Trigger into a limit order at the stop level.", true, true),
            new OrderTypeDefinition("MARKET_ON_CLOSE", "Market on close", "Route for participation in the closing session.", false, false),
            new OrderTypeDefinition("LIMIT_ON_CLOSE", "Limit on close", "Closing auction participation with a price cap.", true, false),
            new OrderTypeDefinition("PEGGED", "Pegged", "Pegged order entry for venue-supported workflows.", false, false)
    );
    private static final List<OptionDefinition> PRICE_TYPES = List.of(
            new OptionDefinition("PER_UNIT", "Per unit", "Standard per-share or per-contract pricing."),
            new OptionDefinition("PERCENTAGE", "Percentage", "Percentage-based pricing where supported."),
            new OptionDefinition("FIXED_AMOUNT", "Fixed amount", "Explicit monetary amount pricing."),
            new OptionDefinition("YIELD", "Yield", "Yield-based fixed-income pricing."),
            new OptionDefinition("SPREAD", "Spread", "Spread-based pricing for relative value workflows.")
    );
    private static final List<OptionDefinition> TIME_IN_FORCE_OPTIONS = List.of(
            new OptionDefinition("DAY", "Day", "Valid for the current trading session."),
            new OptionDefinition("IOC", "Immediate or cancel", "Execute immediately and cancel the balance."),
            new OptionDefinition("FOK", "Fill or kill", "Require full execution immediately or cancel."),
            new OptionDefinition("GTC", "Good till cancel", "Remain active until cancelled."),
            new OptionDefinition("GTD", "Good till date", "Remain active until the venue-defined expiry date."),
            new OptionDefinition("OPG", "At the open", "Participate in the opening auction or cross.")
    );
    private static final List<OptionDefinition> BULK_MODES = List.of(
            new OptionDefinition("FIXED_RATE", "Fixed rate", "Send one order per cadence at a steady orders-per-second rate."),
            new OptionDefinition("BURST", "Burst rate", "Send bursts of orders on each interval for higher throughput testing.")
    );
    private static final Set<String> VALID_SIDE_CODES = Set.of("BUY", "SELL", "SELL_SHORT");
    private static final Set<String> VALID_PRICE_TYPE_CODES = Set.of("PER_UNIT", "PERCENTAGE", "FIXED_AMOUNT", "YIELD", "SPREAD");
    private static final Set<String> VALID_TIME_IN_FORCE_CODES = Set.of("DAY", "IOC", "FOK", "GTC", "GTD", "OPG");
    private static final Set<String> VALID_BULK_MODE_CODES = Set.of("FIXED_RATE", "BURST");
    private static final TheFixFixDictionaryCatalog FIX_DICTIONARY_CATALOG = new TheFixFixDictionaryCatalog();

    private final TheFixClientConfig config;
    private final TheFixSessionProfileStore profileStore;
    private final TheFixMessageTemplateStore templateStore;
    private final LinkedHashMap<String, TheFixClientFixService> fixServices = new LinkedHashMap<>();

    private int pulseChecks;

    TheFixClientWorkbenchState(TheFixClientConfig config) {
        this(config, new TheFixSessionProfileStore(config), new TheFixMessageTemplateStore(config));
    }

    TheFixClientWorkbenchState(TheFixClientConfig config, TheFixSessionProfileStore profileStore) {
        this(config, profileStore, new TheFixMessageTemplateStore(config));
    }

    TheFixClientWorkbenchState(TheFixClientConfig config, TheFixSessionProfileStore profileStore, TheFixMessageTemplateStore templateStore) {
        this.config = config;
        this.profileStore = profileStore;
        this.templateStore = templateStore;
    }

    synchronized JsonObject snapshot() {
        return snapshot(activeProfileName());
    }

    synchronized JsonObject snapshot(String selectedProfileName) {
        TheFixSessionProfile selectedProfile = profileForName(selectedProfileName);
        TheFixClientFixService selectedService = fixServices.get(selectedProfile.name());
        JsonObject kpis = selectedService == null ? idleKpiSnapshot() : selectedService.kpiSnapshot();
        kpis.put("pulseChecks", pulseChecks);

        return new JsonObject()
                .put("applicationName", "TheFixClient")
                .put("subtitle", "Electronic execution workstation")
                .put("environment", "Live simulator-linked FIX order workstation")
                .put("generatedAt", Instant.now().toString())
                .put("session", selectedService == null ? idleSessionSnapshot(selectedProfile) : selectedService.sessionSnapshot(selectedProfile))
                .put("runtimeSessions", runtimeSessionsSnapshot(selectedProfile.name()))
                .put("kpis", kpis)
                .put("recentEvents", selectedService == null ? new JsonArray() : selectedService.recentEventsJson())
                .put("recentOrders", selectedService == null ? new JsonArray() : selectedService.recentOrdersJson())
                .put("referenceData", buildReferenceData())
                .put("settings", workspaceSettingsSnapshot())
                .put("sessionProfiles", profileStore.snapshot())
                .put("defaults", new JsonObject()
                        .put("defaultFlowRate", config.defaultRatePerSecond())
                        .put("bulkMode", "FIXED_RATE")
                        .put("order", defaultOrderJson()));
    }

    synchronized JsonObject connect(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        JsonObject conflict = rejectOrResolveSessionIdentityConflict(selectedProfile, "connect");
        if (conflict != null) {
            return conflict;
        }
        serviceForProfile(selectedProfile.name()).connect();
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject connect() {
        return connect(new JsonObject());
    }

    synchronized JsonObject disconnect(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        if (service != null) {
            service.disconnect();
            if (service.isIdle()) {
                fixServices.remove(selectedProfile.name());
            }
        }
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject disconnect() {
        return disconnect(new JsonObject());
    }

    synchronized JsonObject pulseTest(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        pulseChecks++;
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        if (service != null) {
            service.pulseTest();
        }
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject pulseTest() {
        return pulseTest(new JsonObject());
    }

    synchronized JsonObject sendOrder(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        JsonObject conflict = rejectOrResolveSessionIdentityConflict(selectedProfile, "send");
        if (conflict != null) {
            return conflict;
        }
        OrderPreview preview = buildPreview(request);
        if (!preview.warnings().isEmpty()) {
            return snapshot(selectedProfile.name());
        }
        serviceForProfile(selectedProfile.name()).sendOrder(preview.toOrderRequest());
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject amendBlotterOrder(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        String clOrdId = request == null ? null : request.getString("clOrdId");
        int quantity = request == null ? 0 : parseInt(request, "quantity", 0);
        double price = resolveDouble(request, "price", 0d);
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        boolean success = clOrdId != null && !clOrdId.isBlank() && quantity > 0 && price >= 0d && service != null && service.amendOrder(clOrdId, quantity, price);
        return snapshot(selectedProfile.name()).put("actionResult", new JsonObject()
                .put("success", success)
                .put("type", "amend")
                .put("clOrdId", clOrdId == null ? "" : clOrdId)
                .put("message", success ? "Order amend submitted." : "Unable to amend the selected order."));
    }

    synchronized JsonObject cancelBlotterOrder(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        String clOrdId = request == null ? null : request.getString("clOrdId");
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        boolean success = clOrdId != null && !clOrdId.isBlank() && service != null && service.cancelOrder(clOrdId);
        return snapshot(selectedProfile.name()).put("actionResult", new JsonObject()
                .put("success", success)
                .put("type", "cancel")
                .put("clOrdId", clOrdId == null ? "" : clOrdId)
                .put("message", success ? "Order cancel submitted." : "Unable to cancel the selected order."));
    }

    synchronized JsonObject startOrderFlow(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        JsonObject conflict = rejectOrResolveSessionIdentityConflict(selectedProfile, "bulk-flow");
        if (conflict != null) {
            return conflict;
        }
        OrderPreview preview = buildPreview(request);
        if (!preview.warnings().isEmpty() || !preview.toOrderRequest().messageType().supportsBulk()) {
            return snapshot(selectedProfile.name());
        }
        String bulkMode = sanitizeCode(request == null ? null : request.getString("bulkMode"), "FIXED_RATE");
        int requestedRate = parsePositiveInt(request, "ratePerSecond", config.defaultRatePerSecond());
        int totalOrders = Math.max(0, parseInt(request, "totalOrders", 0));
        int burstSize = parsePositiveInt(request, "burstSize", 10);
        int burstIntervalMs = parsePositiveInt(request, "burstIntervalMs", 1_000);
        if (!VALID_BULK_MODE_CODES.contains(bulkMode)) {
            bulkMode = "FIXED_RATE";
        }
        serviceForProfile(selectedProfile.name()).startAutoFlow(preview.toOrderRequest(), new TheFixBulkOptions(
                bulkMode,
                requestedRate,
                burstSize,
                burstIntervalMs,
                totalOrders
        ));
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject stopOrderFlow(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        if (service != null) {
            service.stopAutoFlow();
        }
        return snapshot(selectedProfile.name());
    }

    synchronized JsonObject stopOrderFlow() {
        return stopOrderFlow(new JsonObject());
    }

    synchronized JsonObject previewOrder(JsonObject request) {
        TheFixSessionProfile selectedProfile = profileForRequest(request);
        TheFixClientFixService service = fixServices.get(selectedProfile.name());
        return buildPreview(request).toJson(service != null && service.isLoggedOn());
    }

    synchronized JsonObject settingsSnapshot() {
        return new JsonObject().put("settings", workspaceSettingsSnapshot());
    }

    synchronized JsonObject sessionProfilesSnapshot() {
        return new JsonObject().put("sessionProfiles", profileStore.snapshot());
    }

    synchronized JsonObject templateSnapshot(String profileName) {
        return new JsonObject().put("templates", templateStore.snapshot(profileForName(profileName).name()));
    }

    synchronized JsonObject templateSnapshot() {
        return templateSnapshot(activeProfileName());
    }

    synchronized JsonObject saveMessageTemplate(JsonObject request) {
        JsonObject effectiveRequest = request == null ? new JsonObject() : request;
        String profileName = profileForRequest(effectiveRequest).name();
        TheFixMessageTemplate savedTemplate = templateStore.saveManualTemplate(
                profileName,
                effectiveRequest.getString("name"),
                effectiveRequest.getJsonObject("draft", defaultOrderJson())
        );
        return templateSnapshot(profileName).put("savedTemplateId", savedTemplate.id());
    }

    synchronized JsonObject fixMetadataSnapshot() {
        return FIX_DICTIONARY_CATALOG.snapshot();
    }

    synchronized JsonObject saveSettingsProfile(JsonObject request) {
        profileStore.saveProfile(request == null ? new JsonObject() : request);
        String savedProfileName = trimToNull(request == null ? null : request.getString("name"));
        return snapshot(savedProfileName == null ? activeProfileName() : savedProfileName);
    }

    synchronized JsonObject activateSettingsProfile(JsonObject request) {
        profileStore.activateProfile(request == null ? null : request.getString("name"));
        return snapshot(profileForRequest(request).name());
    }

    synchronized JsonObject deleteSettingsProfile(JsonObject request) {
        String profileName = trimToNull(request == null ? null : request.getString("name"));
        if (profileName == null) {
            return snapshot().put("actionResult", new JsonObject()
                    .put("success", false)
                    .put("type", "delete-profile")
                    .put("profileName", "")
                    .put("message", "Profile name is required."));
        }

        TheFixClientFixService service = fixServices.get(profileName);
        if (service != null && !service.isIdle()) {
            return snapshot(profileName).put("actionResult", new JsonObject()
                    .put("success", false)
                    .put("type", "delete-profile")
                    .put("profileName", profileName)
                    .put("message", "Disconnect the session before deleting its profile."));
        }

        boolean success = profileStore.deleteProfile(profileName);
        if (success) {
            TheFixClientFixService removedService = fixServices.remove(profileName);
            if (removedService != null) {
                removedService.close();
            }
        }

        return snapshot().put("actionResult", new JsonObject()
                .put("success", success)
                .put("type", "delete-profile")
                .put("profileName", profileName)
                .put("message", success
                        ? "Session profile deleted."
                        : "Unable to delete the selected session profile."));
    }

    synchronized JsonObject updateSettingsStoragePath(JsonObject request) {
        profileStore.updateStoragePath(request == null ? null : request.getString("storagePath"));
        return snapshot();
    }

    @Override
    public synchronized void close() {
        for (TheFixClientFixService service : fixServices.values()) {
            service.close();
        }
        fixServices.clear();
        templateStore.close();
    }

    private String activeProfileName() {
        return profileStore.activeProfile().name();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TheFixSessionProfile profileForRequest(JsonObject request) {
        return profileForName(request == null ? null : request.getString("profileName"));
    }

    private TheFixSessionProfile profileForName(String profileName) {
        return profileStore.profile(profileName);
    }

    private TheFixClientFixService serviceForProfile(String profileName) {
        TheFixSessionProfile profile = profileForName(profileName);
        return fixServices.computeIfAbsent(profile.name(), ignored -> new TheFixClientFixService(config, profile));
    }

    private JsonObject rejectOrResolveSessionIdentityConflict(TheFixSessionProfile selectedProfile, String actionType) {
        List<String> conflicts = conflictingOperationalProfiles(selectedProfile);
        if (conflicts.isEmpty()) {
            return null;
        }

        TheFixClientFixService selectedService = fixServices.get(selectedProfile.name());
        if (selectedService != null && !selectedService.isIdle()) {
            disconnectAndRemoveProfiles(conflicts);
            return null;
        }

        String conflictingProfileName = conflicts.getFirst();
        String actionLabel = switch (actionType) {
            case "bulk-flow" -> "start bulk flow";
            case "send" -> "route orders";
            default -> "connect";
        };
        return snapshot(selectedProfile.name()).put("actionResult", new JsonObject()
                .put("success", false)
                .put("type", actionType)
                .put("profileName", selectedProfile.name())
                .put("conflictingProfileName", conflictingProfileName)
                .put("message", "Cannot " + actionLabel + " for profile \"" + selectedProfile.name()
                        + "\" while profile \"" + conflictingProfileName
                        + "\" is already using the same FIX session identity (BeginString/SenderCompID/TargetCompID)."));
    }

    private List<String> conflictingOperationalProfiles(TheFixSessionProfile selectedProfile) {
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, TheFixClientFixService> entry : fixServices.entrySet()) {
            if (entry.getKey().equals(selectedProfile.name()) || entry.getValue().isIdle()) {
                continue;
            }
            TheFixSessionProfile candidateProfile = profileForName(entry.getKey());
            if (sameSessionIdentity(selectedProfile, candidateProfile)) {
                conflicts.add(candidateProfile.name());
            }
        }
        return conflicts;
    }

    private void disconnectAndRemoveProfiles(List<String> profileNames) {
        for (String profileName : profileNames) {
            TheFixClientFixService service = fixServices.remove(profileName);
            if (service == null) {
                continue;
            }
            service.close();
        }
    }

    private static boolean sameSessionIdentity(TheFixSessionProfile left, TheFixSessionProfile right) {
        return left.beginString().equals(right.beginString())
                && left.senderCompId().equals(right.senderCompId())
                && left.targetCompId().equals(right.targetCompId());
    }

    private JsonObject workspaceSettingsSnapshot() {
        return profileStore.workspaceSnapshot();
    }

    private JsonObject idleSessionSnapshot(TheFixSessionProfile profile) {
        return new JsonObject()
                .put("connected", false)
                .put("status", "Standby")
                .put("host", profile.fixHost())
                .put("port", profile.fixPort())
                .put("beginString", profile.beginString())
                .put("senderCompId", profile.senderCompId())
                .put("targetCompId", profile.targetCompId())
                .put("profileName", profile.name())
                .put("activeProfileName", profileStore.activeProfile().name())
                .put("pendingProfileChange", false)
                .put("fixVersionCode", profile.fixVersionCode())
                .put("fixVersionLabel", profile.fixVersion().label())
                .put("mode", "QuickFIX/J initiator")
                .put("uptime", "Not connected")
                .put("autoFlowActive", false)
                .put("autoFlowRate", 0)
                .put("autoFlowMode", "OFF")
                .put("autoFlowBurstSize", 0)
                .put("autoFlowBurstIntervalMs", 0)
                .put("autoFlowTotalOrders", 0)
                .put("autoFlowRemaining", 0)
                .put("autoFlowDescriptor", "Inactive")
                .put("rawMessageLoggingEnabled", profile.rawMessageLoggingEnabled());
    }

    private JsonObject idleKpiSnapshot() {
        return new JsonObject()
                .put("readyState", "UI ready")
                .put("openOrders", 0)
                .put("sentOrders", 0)
                .put("executionReports", 0)
                .put("cancels", 0)
                .put("rejects", 0)
                .put("sendFailures", 0)
                .put("sessionUptime", "Not connected");
    }

    private JsonArray runtimeSessionsSnapshot(String selectedProfileName) {
        JsonArray items = new JsonArray();
        for (Map.Entry<String, TheFixClientFixService> entry : fixServices.entrySet()) {
            TheFixSessionProfile configuredProfile = profileForName(entry.getKey());
            JsonObject snapshot = entry.getValue().sessionSnapshot(configuredProfile);
            snapshot.put("selected", configuredProfile.name().equals(selectedProfileName));
            items.add(snapshot);
        }
        return items;
    }

    private JsonObject orderDraftJson(TheFixOrderRequest request) {
        JsonArray additionalTags = new JsonArray();
        for (TheFixTagEntry additionalTag : request.additionalTags()) {
            additionalTags.add(additionalTag.toJson());
        }
        return new JsonObject()
                .put("messageType", request.messageTypeCode())
                .put("clOrdId", request.clOrdId())
                .put("origClOrdId", request.origClOrdId())
                .put("region", request.region())
                .put("market", request.market())
                .put("symbol", request.symbol())
                .put("side", request.side())
                .put("quantity", request.quantity())
                .put("orderType", request.orderType())
                .put("priceType", request.priceType())
                .put("timeInForce", request.timeInForce())
                .put("currency", request.currency())
                .put("price", request.price())
                .put("stopPrice", request.stopPrice())
                .put("additionalTags", additionalTags);
    }

    private OrderPreview buildPreview(JsonObject request) {
        JsonObject effectiveRequest = request == null ? new JsonObject() : request;
        TheFixClientFixService selectedService = fixServices.get(profileForRequest(effectiveRequest).name());
        boolean loggedOn = selectedService != null && selectedService.isLoggedOn();
        TheFixMessageType messageType = TheFixMessageType.fromCode(effectiveRequest.getString("messageType"));
        String clOrdId = resolveText(effectiveRequest, "clOrdId", "");
        String origClOrdId = resolveText(effectiveRequest, "origClOrdId", "");
        String region = sanitizeCode(effectiveRequest.getString("region"), "AMERICAS");
        String market = sanitizeCode(effectiveRequest.getString("market"), "XNAS");
        MarketDefinition marketDefinition = exactMarketDefinition(region, market);
        if (marketDefinition == null) {
            marketDefinition = marketDefinition("AMERICAS", "XNAS");
        }
        boolean requestedMarketMatched = region.equals(marketDefinition.region()) && market.equals(marketDefinition.code());
        String currency = sanitizeCode(effectiveRequest.getString("currency"), marketDefinition.currency());
        String symbol = resolveText(effectiveRequest, "symbol", defaultSymbolForMarket(marketDefinition)).toUpperCase(Locale.US);
        String side = sanitizeCode(effectiveRequest.getString("side"), "BUY");
        String timeInForce = sanitizeCode(effectiveRequest.getString("timeInForce"), "DAY");
        String orderType = sanitizeCode(effectiveRequest.getString("orderType"), "LIMIT");
        String priceType = sanitizeCode(effectiveRequest.getString("priceType"), "PER_UNIT");
        int quantity = effectiveRequest.containsKey("quantity") && effectiveRequest.getValue("quantity") != null
                ? parseInt(effectiveRequest, "quantity", 100)
                : 100;
        double rawPrice = resolveDouble(effectiveRequest, "price", 100.25d);
        double rawStopPrice = resolveDouble(effectiveRequest, "stopPrice", 0d);
        OrderTypeDefinition orderTypeDefinition = orderTypeDefinition(orderType);
        List<TheFixTagEntry> additionalTags = TheFixTagEntry.fromJsonArray(effectiveRequest.getJsonArray("additionalTags"));

        List<String> warnings = new ArrayList<>();
        if (messageType.requiresOrigClOrdId() && origClOrdId.isBlank()) {
            warnings.add("OrigClOrdID is required for amend and cancel messages.");
        }
        if (symbol.isBlank()) {
            warnings.add("Symbol is required.");
        }
        if (!REGION_LABELS.containsKey(region)) {
            warnings.add("Region must be ASIA, EMEA, or AMERICAS.");
        }
        if (!requestedMarketMatched) {
            warnings.add("Select a valid market for the chosen region.");
        }
        if (!VALID_SIDE_CODES.contains(side)) {
            warnings.add("Side must be BUY, SELL, or SELL_SHORT.");
        }
        if (messageType == TheFixMessageType.ORDER_CANCEL_REQUEST) {
            if (quantity < 0) {
                warnings.add("Quantity cannot be negative.");
            }
        } else {
            if (!VALID_TIME_IN_FORCE_CODES.contains(timeInForce)) {
                warnings.add("Time in force must be DAY, IOC, FOK, GTC, GTD, or OPG.");
            }
            if (orderTypeDefinition == null) {
                warnings.add("Select a supported order type.");
            }
            if (!VALID_PRICE_TYPE_CODES.contains(priceType)) {
                warnings.add("Select a supported price type.");
            }
            if (quantity <= 0) {
                warnings.add("Quantity must be greater than zero.");
            }
            if (orderTypeDefinition != null && orderTypeDefinition.requiresLimitPrice() && rawPrice <= 0d) {
                warnings.add("Limit price must be greater than zero.");
            }
            if (orderTypeDefinition != null && orderTypeDefinition.requiresStopPrice() && rawStopPrice <= 0d) {
                warnings.add("Stop price must be greater than zero for stop-based orders.");
            }
        }
        if (!currency.matches("^[A-Z]{3}$")) {
            warnings.add("Currency must be a 3-letter ISO code.");
        }

        Set<Integer> seenAdditionalTags = new LinkedHashSet<>();
        Set<Integer> reservedTags = reservedCoreTags(messageType);
        for (TheFixTagEntry additionalTag : additionalTags) {
            if (additionalTag.tag() <= 0) {
                warnings.add("Additional FIX tags must use a positive numeric tag.");
                continue;
            }
            if (!seenAdditionalTags.add(additionalTag.tag())) {
                warnings.add("Duplicate additional FIX tag " + additionalTag.tag() + " is not allowed.");
            }
            if (!additionalTag.hasValue()) {
                warnings.add("Additional FIX tag " + additionalTag.tag() + " requires a value.");
            }
            if (reservedTags.contains(additionalTag.tag())) {
                warnings.add("Additional FIX tag " + additionalTag.tag() + " is already managed by the main form.");
            }
        }

        BigDecimal price = BigDecimal.valueOf(Math.max(rawPrice, 0d)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stopPrice = BigDecimal.valueOf(Math.max(rawStopPrice, 0d)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal notional = price.multiply(BigDecimal.valueOf(Math.max(quantity, 0L))).setScale(2, RoundingMode.HALF_UP);
        String status = warnings.isEmpty()
                ? (loggedOn ? "READY_FOR_ROUTING" : "READY_PENDING_CONNECTION")
                : "INVALID";

        String recommendation;
        if (!warnings.isEmpty()) {
            recommendation = "Resolve the highlighted validation issues before promoting this ticket to a live session.";
        } else if (loggedOn) {
            recommendation = "Ticket is ready for live routing through the simulator-linked FIX initiator.";
        } else {
            recommendation = "Preview is valid. Prime the session or launch bulk flow to establish live FIX connectivity.";
        }

        String orderTypeLabel = orderTypeDefinition == null ? orderType : orderTypeDefinition.label();
        boolean requiresLimitPrice = orderTypeDefinition != null && orderTypeDefinition.requiresLimitPrice();
        boolean requiresStopPrice = orderTypeDefinition != null && orderTypeDefinition.requiresStopPrice();

        String summary = switch (messageType) {
            case ORDER_CANCEL_REQUEST -> "CANCEL " + symbol + " · orig " + (origClOrdId.isBlank() ? "?" : origClOrdId)
                    + (quantity > 0 ? " · qty " + quantity : "");
            case ORDER_CANCEL_REPLACE_REQUEST -> "AMEND " + side + " " + quantity + " " + symbol + " · orig "
                    + (origClOrdId.isBlank() ? "?" : origClOrdId)
                    + " · " + orderTypeLabel
                    + (requiresLimitPrice ? " @ " + price : "")
                    + (requiresStopPrice ? " / stop " + stopPrice : "")
                    + " · " + timeInForce;
            case NEW_ORDER_SINGLE -> side + " " + quantity + " " + symbol + " · " + orderTypeLabel
                    + (requiresLimitPrice ? " @ " + price : "")
                    + (requiresStopPrice ? " / stop " + stopPrice : "")
                    + " · " + timeInForce;
        };
        String routeSummary = marketDefinition.label() + " (" + marketDefinition.code() + ')'
                + " · " + currency
                + (messageType == TheFixMessageType.ORDER_CANCEL_REQUEST ? "" : " · " + priceType.replace('_', ' '))
                + " · +" + additionalTags.size() + " extra tags";

        return new OrderPreview(
                messageType.code(),
                clOrdId,
                origClOrdId,
                symbol,
                side,
                quantity,
                price.doubleValue(),
                stopPrice.doubleValue(),
                timeInForce,
                orderType,
                priceType,
                region,
                market,
                currency,
                status,
                summary,
                quantity > 0 && price.doubleValue() > 0d ? formatNotional(notional, currency) : "—",
                recommendation,
                routeSummary,
                additionalTags,
                warnings
        );
    }

    private JsonObject buildReferenceData() {
        JsonArray regions = new JsonArray();
        REGION_LABELS.forEach((code, label) -> regions.add(new JsonObject().put("code", code).put("label", label)));

        JsonArray markets = new JsonArray();
        for (MarketDefinition definition : MARKETS) {
            markets.add(definition.toJson());
        }

        JsonArray sides = new JsonArray();
        SIDE_OPTIONS.forEach(option -> sides.add(option.toJson()));
        JsonArray orderTypes = new JsonArray();
        ORDER_TYPES.forEach(option -> orderTypes.add(option.toJson()));
        JsonArray priceTypes = new JsonArray();
        PRICE_TYPES.forEach(option -> priceTypes.add(option.toJson()));
        JsonArray timeInForces = new JsonArray();
        TIME_IN_FORCE_OPTIONS.forEach(option -> timeInForces.add(option.toJson()));
        JsonArray bulkModes = new JsonArray();
        BULK_MODES.forEach(option -> bulkModes.add(option.toJson()));
        JsonArray messageTypes = new JsonArray();
        TheFixMessageType.options().forEach(type -> messageTypes.add(type.toJson()));
        JsonArray fixVersions = new JsonArray();
        TheFixFixVersion.options().forEach(version -> fixVersions.add(version.toJson()));

        return new JsonObject()
                .put("regions", regions)
                .put("markets", markets)
                .put("sides", sides)
                .put("orderTypes", orderTypes)
                .put("priceTypes", priceTypes)
                .put("timeInForces", timeInForces)
                .put("bulkModes", bulkModes)
                .put("messageTypes", messageTypes)
                .put("fixVersions", fixVersions);
    }

    private JsonObject defaultOrderJson() {
        MarketDefinition marketDefinition = marketDefinition("AMERICAS", "XNAS");
        return new JsonObject()
                .put("messageType", TheFixMessageType.NEW_ORDER_SINGLE.code())
                .put("clOrdId", "")
                .put("origClOrdId", "")
                .put("region", "AMERICAS")
                .put("market", "XNAS")
                .put("currency", marketDefinition.currency())
                .put("symbol", defaultSymbolForMarket(marketDefinition))
                .put("side", "BUY")
                .put("orderType", "LIMIT")
                .put("priceType", "PER_UNIT")
                .put("timeInForce", "DAY")
                .put("quantity", 100)
                .put("price", 100.25)
                .put("stopPrice", 0.0)
                .put("additionalTags", new JsonArray());
    }

    private static String sanitizeText(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return raw.trim();
    }

    private static String resolveText(JsonObject request, String field, String defaultValue) {
        if (request == null || !request.containsKey(field) || request.getValue(field) == null) {
            return defaultValue;
        }
        String raw = request.getString(field);
        return raw == null ? defaultValue : raw.trim();
    }

    private static String sanitizeCode(String raw, String defaultValue) {
        return sanitizeText(raw, defaultValue).trim().toUpperCase(Locale.US);
    }

    private static int parsePositiveInt(JsonObject request, String field, int defaultValue) {
        int parsed = parseInt(request, field, defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }

    private static int parseInt(JsonObject request, String field, int defaultValue) {
        if (request == null) {
            return defaultValue;
        }
        Object value = request.getValue(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }


    private static double parseDouble(JsonObject request, String field, double defaultValue) {
        if (request == null) {
            return defaultValue;
        }
        Object value = request.getValue(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double resolveDouble(JsonObject request, String field, double defaultValue) {
        if (request == null || !request.containsKey(field) || request.getValue(field) == null) {
            return defaultValue;
        }
        return parseDouble(request, field, defaultValue);
    }

    private static MarketDefinition marketDefinition(String region, String marketCode) {
        for (MarketDefinition marketDefinition : MARKETS) {
            if (marketDefinition.region().equals(region) && marketDefinition.code().equals(marketCode)) {
                return marketDefinition;
            }
        }
        return MARKETS.stream().filter(market -> market.code().equals(marketCode)).findFirst().orElse(null);
    }

    private static MarketDefinition exactMarketDefinition(String region, String marketCode) {
        return MARKETS.stream()
                .filter(market -> market.region().equals(region) && market.code().equals(marketCode))
                .findFirst()
                .orElse(null);
    }

    private static String defaultSymbolForMarket(MarketDefinition marketDefinition) {
        return marketDefinition == null || marketDefinition.symbolHints().isEmpty() ? "AAPL" : marketDefinition.symbolHints().getFirst();
    }

    private static OrderTypeDefinition orderTypeDefinition(String code) {
        for (OrderTypeDefinition definition : ORDER_TYPES) {
            if (definition.code().equals(code)) {
                return definition;
            }
        }
        return null;
    }

    private static String formatNotional(BigDecimal notional, String currencyCode) {
        try {
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
            format.setCurrency(Currency.getInstance(currencyCode));
            return format.format(notional);
        } catch (IllegalArgumentException exception) {
            return currencyCode + ' ' + CURRENCY_FORMAT.format(notional);
        }
    }

    private static Set<Integer> reservedCoreTags(TheFixMessageType messageType) {
        Set<Integer> reserved = new LinkedHashSet<>(Set.of(11, 15, 21, 38, 40, 41, 44, 54, 55, 59, 60, 99, 207, 423));
        if (messageType == TheFixMessageType.ORDER_CANCEL_REQUEST) {
            reserved.remove(21);
            reserved.remove(40);
            reserved.remove(44);
            reserved.remove(59);
            reserved.remove(99);
            reserved.remove(423);
        }
        if (messageType == TheFixMessageType.NEW_ORDER_SINGLE) {
            reserved.remove(41);
        }
        return reserved;
    }

    private record OrderPreview(
            String messageType,
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
            String status,
            String summary,
            String notional,
            String recommendation,
            String routeSummary,
            List<TheFixTagEntry> additionalTags,
            List<String> warnings
    ) {
        JsonObject toJson(boolean connected) {
            JsonArray tagArray = new JsonArray();
            additionalTags.forEach(tag -> tagArray.add(tag.toJson()));
            return new JsonObject()
                    .put("messageType", messageType)
                    .put("clOrdId", clOrdId)
                    .put("origClOrdId", origClOrdId)
                    .put("status", status)
                    .put("summary", summary)
                    .put("notional", notional)
                    .put("recommendation", recommendation)
                    .put("routeSummary", routeSummary)
                    .put("connected", connected)
                    .put("orderType", orderType)
                    .put("priceType", priceType)
                    .put("region", region)
                    .put("market", market)
                    .put("currency", currency)
                    .put("side", side)
                    .put("quantity", quantity)
                    .put("price", price)
                    .put("stopPrice", stopPrice)
                    .put("timeInForce", timeInForce)
                    .put("additionalTags", tagArray)
                    .put("warnings", new JsonArray(warnings));
        }

        TheFixOrderRequest toOrderRequest() {
            return new TheFixOrderRequest(messageType, clOrdId, origClOrdId, symbol, side, quantity, price, stopPrice,
                    timeInForce, orderType, priceType, region, market, currency, additionalTags);
        }
    }

    private record OptionDefinition(String code, String label, String description) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("code", code)
                    .put("label", label)
                    .put("description", description);
        }
    }

    private record OrderTypeDefinition(String code, String label, String description, boolean requiresLimitPrice, boolean requiresStopPrice) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("code", code)
                    .put("label", label)
                    .put("description", description)
                    .put("requiresLimitPrice", requiresLimitPrice)
                    .put("requiresStopPrice", requiresStopPrice);
        }
    }

    synchronized JsonObject recentFixMessages(int limit, int offset, String profileName) {
        TheFixClientFixService service = fixServices.get(profileForName(profileName).name());
        return service == null
                ? new JsonObject().put("items", new JsonArray()).put("total", 0).put("limit", Math.max(1, Math.min(limit, 100))).put("offset", Math.max(0, offset))
                : service.recentFixMessagesJson(limit, offset);
    }

    private record MarketDefinition(String region, String code, String label, String venue, String currency, String assetClass, List<String> symbolHints) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("region", region)
                    .put("code", code)
                    .put("label", label)
                    .put("venue", venue)
                    .put("currency", currency)
                    .put("assetClass", assetClass)
                    .put("symbolHints", new JsonArray(symbolHints));
        }
    }
}
