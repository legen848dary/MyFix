package com.insoftu.thefix.client;

import com.llexsimulator.client.NoOpQuickFixLogFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.LogFactory;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.UnsupportedMessageType;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.field.AvgPx;
import quickfix.field.BusinessRejectReason;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.Currency;
import quickfix.field.ExecType;
import quickfix.field.HandlInst;
import quickfix.field.LeavesQty;
import quickfix.field.MsgType;
import quickfix.field.OrdRejReason;
import quickfix.field.OrderQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.Price;
import quickfix.field.PriceType;
import quickfix.field.SecurityExchange;
import quickfix.field.Side;
import quickfix.field.StopPx;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class TheFixClientFixService implements Application, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TheFixClientFixService.class);
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());
    private static final int MAX_EVENTS = 30;
    private static final int MAX_ORDERS = 40;

    private final TheFixClientConfig config;
    private final TheFixSessionProfileStore profileStore;
    private final AtomicReference<SessionID> activeSessionId = new AtomicReference<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong sentCount = new AtomicLong();
    private final AtomicLong execReportCount = new AtomicLong();
    private final AtomicLong rejectCount = new AtomicLong();
    private final AtomicLong sendFailureCount = new AtomicLong();
    private final ScheduledExecutorService autoFlowExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "thefixclient-auto-flow");
        thread.setDaemon(true);
        return thread;
    });
    private final Deque<EventItem> recentEvents = new ArrayDeque<>();
    private final LinkedHashMap<String, OrderView> recentOrders = new LinkedHashMap<>();

    private SocketInitiator initiator;
    private ScheduledFuture<?> autoFlowFuture;
    private boolean loggedOn;
    private boolean connectionRequested;
    private boolean autoFlowActive;
    private Instant connectedAt;
    private String sessionStatus = "Standby";
    private TheFixSessionProfile connectedProfile;
    private TheFixOrderRequest autoFlowTemplate;
    private TheFixBulkOptions autoFlowOptions;
    private long autoFlowRemaining;

    TheFixClientFixService(TheFixClientConfig config, TheFixSessionProfileStore profileStore) {
        this.config = config;
        this.profileStore = profileStore;
        addEvent("INFO", "FIX service ready", "QuickFIX/J initiator wiring is ready for operator commands.");
    }

    synchronized void connect() {
        if (initiator != null) {
            if (loggedOn) {
                addEvent("WARN", "Session already connected", "The FIX initiator is already logged on to the simulator.");
            } else {
                addEvent("INFO", "Connection already in progress", "Waiting for the FIX initiator to complete logon.");
            }
            return;
        }

        try {
            TheFixSessionProfile activeProfile = profileStore.activeProfile();
            ensureQuickFixDirectories(activeProfile);
            SessionSettings settings = buildSessionSettings(activeProfile);
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = activeProfile.rawMessageLoggingEnabled()
                    ? new FileLogFactory(settings)
                    : new NoOpQuickFixLogFactory();
            MessageFactory messageFactory = new DefaultMessageFactory();

            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            connectionRequested = true;
            connectedProfile = activeProfile;
            sessionStatus = "Connecting";
            initiator.start();
            addEvent("INFO", "Connection requested", "Opening QuickFIX/J initiator session to " + activeProfile.fixHost() + ':' + activeProfile.fixPort() + " using profile " + activeProfile.name() + '.');
        } catch (Exception exception) {
            sessionStatus = "Error";
            connectionRequested = false;
            initiator = null;
            connectedProfile = null;
            addEvent("WARN", "Connection failed", rootMessage(exception));
            log.warn("Unable to start TheFixClient FIX initiator", exception);
        }
    }

    synchronized void disconnect() {
        stopAutoFlowInternal(false);
        connectionRequested = false;
        loggedOn = false;
        connectedAt = null;
        connectedProfile = null;
        activeSessionId.set(null);
        sessionStatus = "Standby";

        if (initiator == null) {
            addEvent("INFO", "Session already idle", "Disconnect requested while no FIX initiator was running.");
            return;
        }

        try {
            initiator.stop(true);
        } catch (Exception exception) {
            log.warn("Error while stopping FIX initiator", exception);
        } finally {
            initiator = null;
        }
        addEvent("INFO", "Session disconnected", "The FIX workstation returned to standby mode.");
    }

    synchronized void pulseTest() {
        addEvent("INFO", "Pulse check executed", loggedOn
                ? "FIX initiator is logged on and ready for order flow."
                : "No live FIX session is connected yet. Use Prime session or Start demo flow to establish logon.");
    }

    synchronized void startAutoFlow(TheFixOrderRequest template, TheFixBulkOptions requestedOptions) {
        TheFixBulkOptions effectiveOptions = requestedOptions == null
                ? new TheFixBulkOptions("FIXED_RATE", config.defaultRatePerSecond(), 10, 1_000, 0)
                : requestedOptions.normalized(config.defaultRatePerSecond());
        autoFlowTemplate = template;
        autoFlowOptions = effectiveOptions;
        autoFlowRemaining = effectiveOptions.totalOrders() > 0 ? effectiveOptions.totalOrders() : -1L;
        autoFlowActive = true;
        if (loggedOn) {
            sessionStatus = "Connected · bulk flow live";
        } else if (initiator == null) {
            connect();
            if (initiator != null && !loggedOn) {
                sessionStatus = "Connecting · bulk flow armed";
            }
        } else {
            sessionStatus = "Connecting · bulk flow armed";
        }

        if (autoFlowFuture != null) {
            autoFlowFuture.cancel(false);
            autoFlowFuture = null;
        }

        if (effectiveOptions.isBurstMode()) {
            autoFlowFuture = autoFlowExecutor.scheduleAtFixedRate(this::sendAutoFlowOrders, 0L,
                    effectiveOptions.burstIntervalMs(), TimeUnit.MILLISECONDS);
        } else {
            long periodNanos = Math.max(1L, 1_000_000_000L / effectiveOptions.ratePerSecond());
            autoFlowFuture = autoFlowExecutor.scheduleAtFixedRate(this::sendAutoFlowOrders, 0L, periodNanos, TimeUnit.NANOSECONDS);
        }
        if (loggedOn) {
            addEvent("SUCCESS", "Bulk flow started", "Running " + effectiveOptions.describe() + " using the active order template.");
        } else {
            addEvent("INFO", "Bulk flow armed", "Bulk routing will begin using " + effectiveOptions.describe() + " as soon as the FIX session logs on.");
        }
    }

    synchronized void stopAutoFlow() {
        stopAutoFlowInternal(true);
    }

    synchronized boolean sendOrder(TheFixOrderRequest request) {
        return sendOrderInternal(request, false, true);
    }

    synchronized JsonObject sessionSnapshot() {
        TheFixSessionProfile configuredProfile = profileStore.activeProfile();
        TheFixSessionProfile effectiveProfile = connectedProfile != null ? connectedProfile : configuredProfile;
        return new JsonObject()
                .put("connected", loggedOn)
                .put("status", sessionStatus)
                .put("host", effectiveProfile.fixHost())
                .put("port", effectiveProfile.fixPort())
                .put("beginString", effectiveProfile.beginString())
                .put("senderCompId", effectiveProfile.senderCompId())
                .put("targetCompId", effectiveProfile.targetCompId())
                .put("profileName", effectiveProfile.name())
                .put("activeProfileName", configuredProfile.name())
                .put("pendingProfileChange", connectedProfile != null && !connectedProfile.name().equals(configuredProfile.name()))
                .put("fixVersionCode", effectiveProfile.fixVersionCode())
                .put("fixVersionLabel", effectiveProfile.fixVersion().label())
                .put("mode", "QuickFIX/J initiator")
                .put("uptime", formatUptime())
                .put("autoFlowActive", autoFlowActive)
                .put("autoFlowRate", autoFlowOptions == null ? 0 : autoFlowOptions.ratePerSecond())
                .put("autoFlowMode", autoFlowOptions == null ? "OFF" : autoFlowOptions.mode())
                .put("autoFlowBurstSize", autoFlowOptions == null ? 0 : autoFlowOptions.burstSize())
                .put("autoFlowBurstIntervalMs", autoFlowOptions == null ? 0 : autoFlowOptions.burstIntervalMs())
                .put("autoFlowTotalOrders", autoFlowOptions == null ? 0 : autoFlowOptions.totalOrders())
                .put("autoFlowRemaining", autoFlowRemaining)
                .put("autoFlowDescriptor", autoFlowOptions == null ? "Inactive" : autoFlowOptions.describe())
                .put("rawMessageLoggingEnabled", effectiveProfile.rawMessageLoggingEnabled());
    }

    synchronized JsonObject kpiSnapshot() {
        return new JsonObject()
                .put("readyState", loggedOn ? "FIX live" : initiator != null ? "Connecting" : "UI ready")
                .put("openOrders", pendingOrders())
                .put("sentOrders", sentCount.get())
                .put("executionReports", execReportCount.get())
                .put("rejects", rejectCount.get())
                .put("sendFailures", sendFailureCount.get())
                .put("sessionUptime", formatUptime());
    }

    synchronized JsonArray recentEventsJson() {
        JsonArray events = new JsonArray();
        for (EventItem eventItem : recentEvents) {
            events.add(eventItem.toJson());
        }
        return events;
    }

    synchronized JsonArray recentOrdersJson() {
        JsonArray items = new JsonArray();
        List<OrderView> orderViews = new ArrayList<>(recentOrders.values());
        for (int i = orderViews.size() - 1; i >= 0; i--) {
            items.add(orderViews.get(i).toJson());
        }
        return items;
    }

    synchronized boolean isLoggedOn() {
        return loggedOn;
    }

    synchronized boolean awaitLogon(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (loggedOn) {
                return true;
            }
            Thread.sleep(100L);
        }
        return loggedOn;
    }

    synchronized long execReportCountValue() {
        return execReportCount.get();
    }

    synchronized long sentCountValue() {
        return sentCount.get();
    }

    @Override
    public synchronized void onCreate(SessionID sessionId) {
        addEvent("INFO", "Session created", sessionId.toString());
    }

    @Override
    public synchronized void onLogon(SessionID sessionId) {
        activeSessionId.set(sessionId);
        loggedOn = true;
        connectedAt = Instant.now();
        sessionStatus = autoFlowActive ? "Connected · bulk flow live" : "Connected";
        addEvent("SUCCESS", "Logon complete", "FIX session established: " + sessionId + '.');
    }

    @Override
    public synchronized void onLogout(SessionID sessionId) {
        activeSessionId.compareAndSet(sessionId, null);
        loggedOn = false;
        connectedAt = null;
        sessionStatus = connectionRequested ? "Reconnecting" : "Standby";
        addEvent("WARN", "Session logged out", "FIX session closed: " + sessionId + '.');
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("toAdmin session={} type={} raw={}", sessionId, safeMessageType(message), printable(message));
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
        log.debug("fromAdmin session={} type={} raw={}", sessionId, safeMessageType(message), printable(message));
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("toApp session={} type={} raw={}", sessionId, safeMessageType(message), printable(message));
    }

    @Override
    public synchronized void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        String messageType = message.getHeader().getString(MsgType.FIELD);
        switch (messageType) {
            case MsgType.EXECUTION_REPORT -> handleExecutionReport(message);
            case MsgType.REJECT, MsgType.BUSINESS_MESSAGE_REJECT -> handleReject(message, messageType);
            default -> log.debug("Ignoring application message type={} session={}", messageType, sessionId);
        }
    }

    @Override
    public synchronized void close() {
        disconnect();
        autoFlowExecutor.shutdownNow();
    }

    private void sendAutoFlowOrders() {
        TheFixOrderRequest template;
        TheFixBulkOptions options;
        synchronized (this) {
            template = autoFlowTemplate;
            options = autoFlowOptions;
        }
        if (template == null || options == null) {
            return;
        }
        synchronized (this) {
            int ordersPerTick = options.isBurstMode() ? options.burstSize() : 1;
            for (int i = 0; i < ordersPerTick; i++) {
                if (!autoFlowActive || autoFlowTemplate == null) {
                    return;
                }
                if (autoFlowRemaining == 0L) {
                    completeAutoFlow();
                    return;
                }
                boolean sent = sendOrderInternal(template, true, false);
                if (sent && autoFlowRemaining > 0L) {
                    autoFlowRemaining--;
                    if (autoFlowRemaining == 0L) {
                        completeAutoFlow();
                        return;
                    }
                }
                if (!sent && !loggedOn) {
                    return;
                }
            }
        }
    }

    private boolean sendOrderInternal(TheFixOrderRequest request, boolean autoFlowOrder, boolean addManualEvent) {
        SessionID sessionId = activeSessionId.get();
        if (!loggedOn || sessionId == null) {
            if (autoFlowOrder) {
                return false;
            }
            sendFailureCount.incrementAndGet();
            if (!autoFlowOrder) {
                addEvent("WARN", "Order blocked", "No active FIX session is logged on. Prime the session first.");
            }
            return false;
        }

        Session session = Session.lookupSession(sessionId);
        if (session == null || !session.isLoggedOn()) {
            if (autoFlowOrder) {
                return false;
            }
            sendFailureCount.incrementAndGet();
            if (!autoFlowOrder) {
                addEvent("WARN", "Order blocked", "QuickFIX/J session is not logged on yet.");
            }
            return false;
        }

        String clOrdId = nextClOrdId(autoFlowOrder ? "AUTO" : "WEB");
        OrderView orderView = OrderView.submitted(clOrdId, request, autoFlowOrder);
        rememberOrder(orderView);

        try {
            boolean sent = Session.sendToTarget(buildNewOrderSingle(clOrdId, request), sessionId);
            if (!sent) {
                sendFailureCount.incrementAndGet();
                orderView.markFailure("SEND_FAILED", "QuickFIX/J returned false while sending to target.");
                if (!autoFlowOrder) {
                    addEvent("WARN", "Order send failed", "The simulator session rejected the outbound send attempt for " + clOrdId + '.');
                }
                return false;
            }
            sentCount.incrementAndGet();
            orderView.markSent();
            if (addManualEvent) {
                addEvent("SUCCESS", "Order sent", "Submitted " + request.summary() + " as " + clOrdId + '.');
            }
            return true;
        } catch (SessionNotFound exception) {
            sendFailureCount.incrementAndGet();
            orderView.markFailure("SESSION_NOT_FOUND", rootMessage(exception));
            addEvent("WARN", "Session unavailable", "Unable to route order because the FIX session could not be found.");
            return false;
        } catch (Exception exception) {
            sendFailureCount.incrementAndGet();
            orderView.markFailure("ERROR", rootMessage(exception));
            addEvent("WARN", "Unexpected send error", rootMessage(exception));
            log.warn("Unexpected order send failure", exception);
            return false;
        }
    }

    private void handleExecutionReport(Message message) {
        boolean rejectedExecutionReport = isRejectedExecutionReport(message);
        if (rejectedExecutionReport) {
            rejectCount.incrementAndGet();
        } else {
            execReportCount.incrementAndGet();
        }
        String clOrdId = safeString(message, ClOrdID.FIELD, "UNKNOWN");
        OrderView orderView = recentOrders.computeIfAbsent(clOrdId, ignored -> OrderView.fromExecution(message));
        orderView.updateFromExecutionReport(message);
        trimOrders();
        if (rejectedExecutionReport) {
            addEvent("WARN", "Order rejected", executionReportRejectReason(message));
        }
    }

    private void handleReject(Message message, String messageType) {
        rejectCount.incrementAndGet();
        String clOrdId = safeString(message, ClOrdID.FIELD, "REJECT-" + sequence.incrementAndGet());
        OrderView orderView = recentOrders.computeIfAbsent(clOrdId, ignored -> OrderView.rejected(clOrdId));
        String reason = safeString(message, Text.FIELD, messageType.equals(MsgType.BUSINESS_MESSAGE_REJECT)
                ? "Business message reject"
                : "Session reject");
        orderView.markRejected(reason, safeString(message, BusinessRejectReason.FIELD, messageType));
        trimOrders();
        addEvent("WARN", "Order rejected", reason);
    }

    private static boolean isRejectedExecutionReport(Message message) {
        return "8".equals(safeString(message, ExecType.FIELD, ""))
                || "8".equals(safeString(message, OrdStatus.FIELD, ""));
    }

    private static String executionReportRejectReason(Message message) {
        String text = safeString(message, Text.FIELD, "").trim();
        if (!text.isBlank()) {
            return text;
        }
        String code = safeString(message, OrdRejReason.FIELD, "").trim();
        if (!code.isBlank()) {
            return "Execution report rejected (OrdRejReason=" + code + ")";
        }
        return "Execution report rejected";
    }

    private void rememberOrder(OrderView orderView) {
        recentOrders.put(orderView.clOrdId, orderView);
        trimOrders();
    }

    private void trimOrders() {
        while (recentOrders.size() > MAX_ORDERS) {
            String eldest = recentOrders.keySet().iterator().next();
            recentOrders.remove(eldest);
        }
    }

    private void stopAutoFlowInternal(boolean addEvent) {
        autoFlowActive = false;
        autoFlowTemplate = null;
        autoFlowOptions = null;
        autoFlowRemaining = 0L;
        if (autoFlowFuture != null) {
            autoFlowFuture.cancel(false);
            autoFlowFuture = null;
        }
        if (addEvent) {
            addEvent("INFO", "Bulk flow stopped", "The repeating bulk order stream has been paused.");
        }
        if (loggedOn) {
            sessionStatus = "Connected";
        }
    }

    private void completeAutoFlow() {
        TheFixBulkOptions completedOptions = autoFlowOptions;
        stopAutoFlowInternal(false);
        if (loggedOn) {
            sessionStatus = "Connected";
        }
        if (completedOptions != null) {
            addEvent("SUCCESS", "Bulk flow complete", "Completed " + completedOptions.describe() + " with the current template.");
        }
    }

    synchronized void onProfileActivated() {
        TheFixSessionProfile activeProfile = profileStore.activeProfile();
        if (initiator != null) {
            addEvent("INFO", "Profile updated", "Active profile is now " + activeProfile.name() + ". Reconnect the FIX session to apply the new settings.");
        } else {
            addEvent("INFO", "Profile updated", "Active profile is now " + activeProfile.name() + '.');
        }
    }

    private void ensureQuickFixDirectories(TheFixSessionProfile profile) throws IOException {
        Files.createDirectories(profile.storeDir());
        Files.createDirectories(profile.rawLogDir());
    }

    private SessionSettings buildSessionSettings(TheFixSessionProfile profile) {
        SessionSettings settings = new SessionSettings();
        SessionID sessionId = new SessionID(profile.beginString(), profile.senderCompId(), profile.targetCompId());

        settings.setString(sessionId, "ConnectionType", "initiator");
        settings.setString(sessionId, "BeginString", profile.beginString());
        settings.setString(sessionId, "SenderCompID", profile.senderCompId());
        settings.setString(sessionId, "TargetCompID", profile.targetCompId());
        settings.setString(sessionId, "SocketConnectHost", profile.fixHost());
        settings.setString(sessionId, "SocketConnectPort", Integer.toString(profile.fixPort()));
        settings.setString(sessionId, "HeartBtInt", Integer.toString(profile.heartBtIntSec()));
        settings.setString(sessionId, "ReconnectInterval", Integer.toString(profile.reconnectIntervalSec()));
        settings.setString(sessionId, "StartTime", profile.sessionStartTime());
        settings.setString(sessionId, "EndTime", profile.sessionEndTime());
        settings.setString(sessionId, "TimeZone", "UTC");
        settings.setString(sessionId, "SocketNodelay", "Y");
        settings.setString(sessionId, "ResetOnLogon", profile.resetOnLogon() ? "Y" : "N");
        settings.setString(sessionId, "ResetOnLogout", "Y");
        settings.setString(sessionId, "ResetOnDisconnect", "Y");
        settings.setString(sessionId, "UseDataDictionary", "N");
        settings.setString(sessionId, "ValidateIncomingMessage", "N");
        settings.setString(sessionId, "ValidateUserDefinedFields", "N");
        settings.setString(sessionId, "PersistMessages", "N");
        settings.setString(sessionId, "FileStorePath", profile.storeDir().toString());
        settings.setString(sessionId, "FileLogPath", profile.rawLogDir().toString());
        if ("FIXT.1.1".equals(profile.beginString())) {
            settings.setString(sessionId, "DefaultApplVerID", profile.defaultApplVerId());
        }
        return settings;
    }

    private Message buildNewOrderSingle(String clOrdId, TheFixOrderRequest request) {
        Message order = new Message();
        order.getHeader().setString(MsgType.FIELD, MsgType.NEW_ORDER_SINGLE);
        order.setField(new ClOrdID(clOrdId));
        order.setField(new Side(request.fixSide()));
        order.setField(new TransactTime(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)));
        order.setField(new OrdType(request.fixOrdType()));
        order.setField(new HandlInst(HandlInst.AUTOMATED_EXECUTION_NO_INTERVENTION));
        order.setField(new Symbol(request.symbol()));
        order.setField(new OrderQty(request.quantity()));
        order.setField(new TimeInForce(request.fixTimeInForce()));
        order.setField(new PriceType(request.fixPriceType()));
        if (!request.market().isBlank()) {
            order.setField(new SecurityExchange(request.market()));
        }
        if (!request.currency().isBlank()) {
            order.setField(new Currency(request.currency()));
        }
        if (request.requiresLimitPrice()) {
            order.setField(new Price(request.price()));
        }
        if (request.requiresStopPrice()) {
            order.setField(new StopPx(request.stopPrice()));
        }
        return order;
    }

    private String nextClOrdId(String prefix) {
        return prefix + '-' + System.currentTimeMillis() + '-' + sequence.incrementAndGet();
    }

    private int pendingOrders() {
        int count = 0;
        for (OrderView orderView : recentOrders.values()) {
            if (!orderView.terminal()) {
                count++;
            }
        }
        return count;
    }

    private String formatUptime() {
        if (!loggedOn || connectedAt == null) {
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
        return hours + "h " + (minutes % 60) + "m";
    }

    private void addEvent(String level, String title, String detail) {
        recentEvents.addFirst(new EventItem(Instant.now(), level, title, detail));
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }
    }

    private static String safeMessageType(Message message) {
        try {
            return message.getHeader().getString(MsgType.FIELD);
        } catch (FieldNotFound exception) {
            return "UNKNOWN";
        }
    }

    private static String printable(Message message) {
        return message.toString().replace('\u0001', '|');
    }

    private static String safeString(Message message, int field, String fallback) {
        try {
            return message.isSetField(field) ? message.getString(field) : fallback;
        } catch (FieldNotFound exception) {
            return fallback;
        }
    }

    private static double safeDouble(Message message, int field, double fallback) {
        try {
            return message.isSetField(field) ? message.getDouble(field) : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return Objects.toString(cursor.getMessage(), cursor.getClass().getSimpleName());
    }

    private record EventItem(Instant timestamp, String level, String title, String detail) {
        JsonObject toJson() {
            return new JsonObject()
                    .put("time", EVENT_TIME_FORMAT.format(timestamp))
                    .put("level", level)
                    .put("title", title)
                    .put("detail", detail);
        }
    }

    private static final class OrderView {
        private final String clOrdId;
        private final String symbol;
        private final String side;
        private final boolean autoFlow;
        private final Instant createdAt;
        private final int quantity;
        private final double limitPrice;

        private String status;
        private String execType;
        private String note;
        private double cumQty;
        private double leavesQty;
        private double avgPx;

        private OrderView(String clOrdId, String symbol, String side, int quantity, double limitPrice, boolean autoFlow) {
            this.clOrdId = clOrdId;
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.limitPrice = limitPrice;
            this.autoFlow = autoFlow;
            this.createdAt = Instant.now();
            this.status = "Pending";
            this.execType = "Pending";
            this.note = autoFlow ? "Auto flow" : "Manual ticket";
            this.leavesQty = quantity;
        }

        static OrderView submitted(String clOrdId, TheFixOrderRequest request, boolean autoFlow) {
            return new OrderView(clOrdId, request.symbol(), request.side(), request.quantity(), request.price(), autoFlow);
        }

        static OrderView fromExecution(Message message) {
            return new OrderView(
                    safeString(message, ClOrdID.FIELD, "UNKNOWN"),
                    safeString(message, Symbol.FIELD, "UNKNOWN"),
                    sideLabel(safeString(message, Side.FIELD, "1")),
                    (int) Math.round(safeDouble(message, OrderQty.FIELD, 0d)),
                    safeDouble(message, Price.FIELD, 0d),
                    false
            );
        }

        static OrderView rejected(String clOrdId) {
            return new OrderView(clOrdId, "UNKNOWN", "BUY", 0, 0d, false);
        }

        void markSent() {
            this.status = "Sent";
            this.execType = "Pending";
            this.note = autoFlow ? "Awaiting simulator fill" : "Manually submitted";
        }

        void markFailure(String status, String reason) {
            this.status = status;
            this.execType = "ERROR";
            this.note = reason;
        }

        void markRejected(String reason, String code) {
            this.status = "Rejected";
            this.execType = code;
            this.note = reason;
            this.leavesQty = quantity;
        }

        void updateFromExecutionReport(Message message) {
            this.status = ordStatusLabel(safeString(message, OrdStatus.FIELD, this.status));
            this.execType = execTypeLabel(safeString(message, ExecType.FIELD, this.execType));
            this.note = safeString(message, Text.FIELD, note);
            this.cumQty = safeDouble(message, CumQty.FIELD, cumQty);
            this.leavesQty = safeDouble(message, LeavesQty.FIELD, leavesQty);
            this.avgPx = safeDouble(message, AvgPx.FIELD, avgPx);
        }

        boolean terminal() {
            return List.of("Filled", "Cancelled", "Rejected").contains(status);
        }

        JsonObject toJson() {
            return new JsonObject()
                    .put("time", EVENT_TIME_FORMAT.format(createdAt))
                    .put("clOrdId", clOrdId)
                    .put("symbol", symbol)
                    .put("side", side)
                    .put("quantity", quantity)
                    .put("limitPrice", String.format(Locale.US, "%.2f", limitPrice))
                    .put("market", "")
                    .put("currency", "")
                    .put("status", status)
                    .put("execType", execType)
                    .put("cumQty", String.format(Locale.US, "%.0f", cumQty))
                    .put("leavesQty", String.format(Locale.US, "%.0f", leavesQty))
                    .put("avgPx", avgPx > 0d ? String.format(Locale.US, "%.2f", avgPx) : "—")
                    .put("note", note)
                    .put("source", autoFlow ? "Auto flow" : "Manual");
        }

        private static String ordStatusLabel(String raw) {
            return switch (raw) {
                case "0" -> "New";
                case "1" -> "Partially Filled";
                case "2" -> "Filled";
                case "4" -> "Cancelled";
                case "8" -> "Rejected";
                default -> raw;
            };
        }

        private static String execTypeLabel(String raw) {
            return switch (raw) {
                case "0" -> "New";
                case "1" -> "Partial Fill";
                case "2" -> "Fill";
                case "4" -> "Cancelled";
                case "8" -> "Rejected";
                default -> raw;
            };
        }

        private static String sideLabel(String raw) {
            return "2".equals(raw) ? "SELL" : "BUY";
        }
    }
}

