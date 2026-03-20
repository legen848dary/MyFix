package com.insoftu.thefix.client;

import com.llexsimulator.client.FixDemoClientConfig;
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
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.HandlInst;
import quickfix.field.LeavesQty;
import quickfix.field.MsgType;
import quickfix.field.OrderQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.fix44.NewOrderSingle;

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
import java.util.Map;
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
    private OrderRequest autoFlowTemplate;
    private int autoFlowRate;

    TheFixClientFixService(TheFixClientConfig config) {
        this.config = config;
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
            ensureQuickFixDirectories();
            FixDemoClientConfig demoConfig = config.toFixDemoClientConfig();
            SessionSettings settings = demoConfig.toSessionSettings();
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = config.rawMessageLoggingEnabled()
                    ? new FileLogFactory(settings)
                    : new NoOpQuickFixLogFactory();
            MessageFactory messageFactory = new DefaultMessageFactory();

            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            connectionRequested = true;
            sessionStatus = "Connecting";
            initiator.start();
            addEvent("INFO", "Connection requested", "Opening QuickFIX/J initiator session to " + config.fixHost() + ':' + config.fixPort() + '.');
        } catch (Exception exception) {
            sessionStatus = "Error";
            connectionRequested = false;
            initiator = null;
            addEvent("WARN", "Connection failed", rootMessage(exception));
            log.warn("Unable to start TheFixClient FIX initiator", exception);
        }
    }

    synchronized void disconnect() {
        stopAutoFlowInternal(false);
        connectionRequested = false;
        loggedOn = false;
        connectedAt = null;
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
                : "FIX initiator heartbeat check completed while awaiting logon.");
    }

    synchronized void startAutoFlow(OrderRequest template, int requestedRate) {
        int effectiveRate = requestedRate > 0 ? requestedRate : config.defaultRatePerSecond();
        autoFlowTemplate = template;
        autoFlowRate = effectiveRate;
        autoFlowActive = true;
        if (loggedOn) {
            sessionStatus = "Connected · auto flow live";
        }

        if (autoFlowFuture != null) {
            autoFlowFuture.cancel(false);
            autoFlowFuture = null;
        }

        long periodNanos = Math.max(1L, 1_000_000_000L / effectiveRate);
        autoFlowFuture = autoFlowExecutor.scheduleAtFixedRate(() -> sendAutoFlowOrder(), 0L, periodNanos, TimeUnit.NANOSECONDS);
        addEvent("SUCCESS", "Auto flow started", "Streaming demo orders at " + effectiveRate + " msg/s using the active order template.");
    }

    synchronized void stopAutoFlow() {
        stopAutoFlowInternal(true);
    }

    synchronized boolean sendOrder(OrderRequest request) {
        return sendOrderInternal(request, false, true);
    }

    synchronized JsonObject sessionSnapshot() {
        return new JsonObject()
                .put("connected", loggedOn)
                .put("status", sessionStatus)
                .put("host", config.fixHost())
                .put("port", config.fixPort())
                .put("beginString", config.beginString())
                .put("senderCompId", config.senderCompId())
                .put("targetCompId", config.targetCompId())
                .put("mode", "QuickFIX/J initiator")
                .put("uptime", formatUptime())
                .put("autoFlowActive", autoFlowActive)
                .put("autoFlowRate", autoFlowRate)
                .put("rawMessageLoggingEnabled", config.rawMessageLoggingEnabled());
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
        sessionStatus = autoFlowActive ? "Connected · auto flow live" : "Connected";
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

    private void sendAutoFlowOrder() {
        OrderRequest template;
        synchronized (this) {
            template = autoFlowTemplate;
        }
        if (template == null) {
            return;
        }
        synchronized (this) {
            sendOrderInternal(template, true, false);
        }
    }

    private boolean sendOrderInternal(OrderRequest request, boolean autoFlowOrder, boolean addManualEvent) {
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
        execReportCount.incrementAndGet();
        String clOrdId = safeString(message, ClOrdID.FIELD, "UNKNOWN");
        OrderView orderView = recentOrders.computeIfAbsent(clOrdId, ignored -> OrderView.fromExecution(message));
        orderView.updateFromExecutionReport(message);
        trimOrders();
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
        autoFlowRate = 0;
        if (autoFlowFuture != null) {
            autoFlowFuture.cancel(false);
            autoFlowFuture = null;
        }
        if (addEvent) {
            addEvent("INFO", "Auto flow stopped", "The repeating demo order stream has been paused.");
        }
        if (loggedOn) {
            sessionStatus = "Connected";
        }
    }

    private void ensureQuickFixDirectories() throws IOException {
        FixDemoClientConfig demoConfig = config.toFixDemoClientConfig();
        Files.createDirectories(demoConfig.storeDir());
        Files.createDirectories(demoConfig.rawLogDir());
    }

    private NewOrderSingle buildNewOrderSingle(String clOrdId, OrderRequest request) {
        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(clOrdId),
                new Side(request.fixSide()),
                new TransactTime(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)),
                new OrdType(OrdType.LIMIT)
        );
        order.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_NO_INTERVENTION));
        order.set(new Symbol(request.symbol()));
        order.set(new OrderQty(request.quantity()));
        order.set(new Price(request.price()));
        order.set(new TimeInForce(request.fixTimeInForce()));
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

    record OrderRequest(String symbol, String side, int quantity, double price, String timeInForce) {
        String summary() {
            return side + ' ' + quantity + ' ' + symbol + " @ " + String.format(Locale.US, "%.2f", price) + ' ' + timeInForce;
        }

        char fixSide() {
            return "SELL".equalsIgnoreCase(side) ? Side.SELL : Side.BUY;
        }

        char fixTimeInForce() {
            return switch (timeInForce.toUpperCase(Locale.US)) {
                case "IOC" -> TimeInForce.IMMEDIATE_OR_CANCEL;
                case "FOK" -> TimeInForce.FILL_OR_KILL;
                default -> TimeInForce.DAY;
            };
        }
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

        static OrderView submitted(String clOrdId, OrderRequest request, boolean autoFlow) {
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

