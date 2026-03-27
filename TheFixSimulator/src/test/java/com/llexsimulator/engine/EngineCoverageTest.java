package com.llexsimulator.engine;

import com.llexsimulator.config.SimulatorConfig;
import com.llexsimulator.disruptor.DisruptorPipeline;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.session.CompositeKey;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.session.SessionWriter;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineCoverageTest {

    @Test
    void fixConnectionTracksSessionStateDiagnosticsAndOutboundCounters() {
        Session session = session("FIX.4.4", "LLEXSIM", "CLIENT1", 77L, 3, 10, 11, true, true);
        SessionWriter writer = mock(SessionWriter.class);
        FixConnection connection = new FixConnection(7L, session, writer, false);

        assertEquals(7L, connection.connectionId());
        assertEquals(77L, connection.sessionId());
        assertEquals("FIX.4.4", connection.beginString());
        assertEquals("LLEXSIM", connection.senderCompId());
        assertEquals("CLIENT1", connection.targetCompId());
        assertTrue(connection.loggedOn());
        assertFalse(connection.slowConsumer());
        assertTrue(connection.connectedAtEpochMs() > 0L);
        assertTrue(connection.recentEventsSnapshot().stream().anyMatch(s -> s.contains("SESSION_ACQUIRED")));

        connection.sequenceIndex(9);
        verify(writer).sequenceIndex(9);

        connection.onInboundMessage(session, 5, 'D', 55L);
        assertEquals(1L, connection.inboundMessageCount());
        assertEquals(55L, connection.lastLibraryPosition());
        assertEquals("D", connection.lastInboundMsgType());
        assertTrue(connection.lastInboundAtEpochMs() > 0L);

        connection.onTimeout(session);
        connection.onSlowStatus(session, true);
        connection.onOutboundSendSuccess(session, "FILL");
        connection.onOutboundBackpressure(session, "FILL", -1L);
        connection.onOutboundSendFailure(session, "FILL", -2L);
        connection.onDisconnect(session, "CLIENT_DISCONNECT");
        connection.markDisconnected();

        assertEquals(1L, connection.timeoutCount());
        assertEquals(1L, connection.slowStatusChangeCount());
        assertEquals(1L, connection.outboundSendSuccessCount());
        assertEquals(1L, connection.outboundBackpressureCount());
        assertEquals(1L, connection.outboundSendFailureCount());
        assertEquals(1L, connection.disconnectCount());
        assertFalse(connection.loggedOn());
        assertEquals("FILL", connection.lastOutboundEvent());
        assertEquals("CLIENT_DISCONNECT", connection.lastDisconnectReason());
        assertTrue(connection.recentEventsSnapshot().stream().anyMatch(s -> s.contains("DISCONNECT reason=CLIENT_DISCONNECT")));

        SessionDiagnosticsSnapshot snapshot = connection.snapshot();
        assertEquals(connection.connectionId(), snapshot.connectionId());
        assertEquals(connection.sessionId(), snapshot.artioSessionId());
        assertEquals(connection.lastDisconnectReason(), snapshot.lastDisconnectReason());
        assertEquals(connection.lastOutboundEvent(), snapshot.lastOutboundEvent());
        assertEquals(connection.recentEventsSnapshot(), snapshot.recentEvents());

        int claimed = connection.claimNextOutboundMsgSeqNum();
        assertEquals(12, claimed);
        connection.rollbackOutboundMsgSeqNum(claimed);
        assertEquals(11, connection.lastSentMsgSeqNum());
        connection.rollbackOutboundMsgSeqNum(999);
        assertEquals(11, connection.lastSentMsgSeqNum());
    }

    @Test
    void fixConnectionBenchmarkModeSkipsWallClockRefreshesAndSupportsUnknownTypes() {
        Session session = session("FIXT.1.1", "SENDER", "TARGET", 88L, 4, 1, 2, false, false);
        SessionWriter writer = mock(SessionWriter.class);
        FixConnection connection = new FixConnection(8L, session, writer, true);

        connection.onInboundMessage(session, 4, 1L, 9L);
        connection.onOutboundSendSuccess(session, "NONE");
        connection.updateSession(session, writer);

        assertEquals(0L, connection.lastInboundAtEpochMs());
        assertEquals(0L, connection.lastOutboundAtEpochMs());
        assertEquals("1", connection.lastInboundMsgType());
        assertTrue(connection.recentEventsSnapshot().stream().anyMatch(s -> s.contains("SESSION_STARTED")));
    }

    @Test
    void fixEngineManagerUsesLowLatencyPollingOutsideBenchmarkModeWhenBusySpinIsConfigured() {
        SimulatorConfig config = new SimulatorConfig(
                "0.0.0.0",
                9880,
                "logs/quickfixj",
                false,
                8080,
                "/tmp/aeron",
                "aeron:ipc",
                "aeron:ipc",
                131072,
                "BUSY_SPIN",
                "BUSY_SPIN",
                "BUSY_SPIN",
                "BUSY_SPIN",
                "DEDICATED",
                "busy_spin",
                "noop",
                "noop",
                "backoff",
                "backoff",
                131072,
                500,
                false,
                true
        );

        assertTrue(FixEngineManager.usesLowLatencyPolling(config));
        assertEquals(32, FixEngineManager.resolvePollFragmentLimit(config));
        assertEquals(1, FixEngineManager.resolveMaxPollBatchesPerCycle(config));
    }

    @Test
    void fixEngineManagerUsesAdaptivePollingWhenGuiRunsWithSleepingStrategy() {
        SimulatorConfig config = new SimulatorConfig(
                "0.0.0.0",
                9880,
                "logs/quickfixj",
                false,
                8080,
                "/tmp/aeron",
                "aeron:ipc",
                "aeron:ipc",
                131072,
                "SLEEPING",
                "SLEEPING",
                "SLEEPING",
                "SLEEPING",
                "DEDICATED",
                "busy_spin",
                "noop",
                "noop",
                "backoff",
                "backoff",
                131072,
                500,
                false,
                true
        );

        assertFalse(FixEngineManager.usesLowLatencyPolling(config));
        assertEquals(256, FixEngineManager.resolvePollFragmentLimit(config));
        assertEquals(8, FixEngineManager.resolveMaxPollBatchesPerCycle(config));
    }

    @Test
    void orderSessionRegistryRegistersRemovesAndArchivesDisconnects() {
        OrderSessionRegistry registry = new OrderSessionRegistry(false);
        Session session = session("FIX.4.4", "LLEXSIM", "CLIENT1", 100L, 1, 1, 1, true, true);
        SessionWriter writer = mock(SessionWriter.class);

        FixConnection connection = registry.register(session, writer);
        assertNotNull(connection);
        assertEquals(connection, registry.get(connection.connectionId()));
        assertEquals(1, registry.activeCount());
        assertEquals(1, registry.getAllConnections().size());

        FixConnection removed = registry.remove(connection.connectionId());
        assertEquals(connection, removed);
        assertNull(registry.get(connection.connectionId()));
        assertEquals(0, registry.activeCount());

        FixConnection archived = registry.register(session, writer);
        archived.onDisconnect(session, "MANUAL");
        assertEquals(archived, registry.archiveAndRemove(archived.connectionId()));
        List<SessionDiagnosticsSnapshot> recent = registry.getRecentDisconnects(5);
        assertEquals(1, recent.size());
        assertEquals("MANUAL", recent.getFirst().lastDisconnectReason());
        assertTrue(registry.getRecentDisconnects(-1).isEmpty());
        assertEquals(1, registry.getRecentDisconnects(100).size());
        assertNull(registry.archiveAndRemove(999L));
    }

    @Test
    void orderSessionRegistryCapsArchivedDisconnectHistory() {
        OrderSessionRegistry registry = new OrderSessionRegistry(false);
        SessionWriter writer = mock(SessionWriter.class);

        for (int i = 0; i < 33; i++) {
            Session session = session("FIX.4.4", "LLEXSIM", "CLIENT" + i, 1_000L + i, 1, 1, 1, true, true);
            FixConnection connection = registry.register(session, writer);
            connection.onDisconnect(session, "R" + i);
            registry.archiveAndRemove(connection.connectionId());
        }

        List<SessionDiagnosticsSnapshot> recent = registry.getRecentDisconnects(100);
        assertEquals(32, recent.size());
        assertEquals("R32", recent.getFirst().lastDisconnectReason());
        assertEquals("R1", recent.getLast().lastDisconnectReason());
    }

    @Test
    void fixSessionApplicationCanDisableCancelAndAmendIngress() {
        OrderSessionRegistry registry = mock(OrderSessionRegistry.class);
        DisruptorPipeline pipeline = mock(DisruptorPipeline.class);
        FixLibrary library = mock(FixLibrary.class);
        Session session = session("FIX.4.4", "LLEXSIM", "CLIENT1", 120L, 1, 1, 1, true, true);
        SessionWriter writer = mock(SessionWriter.class);
        FixConnection connection = mock(FixConnection.class);
        OnMessageInfo messageInfo = mock(OnMessageInfo.class);
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(8));

        when(session.connectionId()).thenReturn(444L);
        when(library.sessionWriter(120L, 444L, 1)).thenReturn(writer);
        when(connection.sessionKey()).thenReturn("FIX.4.4:LLEXSIM->CLIENT1#120");
        when(connection.connectionId()).thenReturn(444L);
        when(connection.sequenceIndex()).thenReturn(1);
        when(connection.lastReceivedMsgSeqNum()).thenReturn(1);
        when(connection.lastSentMsgSeqNum()).thenReturn(1);
        when(messageInfo.isValid()).thenReturn(true);
        when(registry.register(session, writer)).thenReturn(connection);

        FixSessionApplication app = new FixSessionApplication(registry, pipeline, false);
        app.attachLibrary(library);
        SessionHandler handler = app.onSessionAcquired(session, mock(uk.co.real_logic.artio.library.SessionAcquiredInfo.class));

        assertEquals(Action.CONTINUE, handler.onMessage(buffer, 0, 0, 1, session, 1, 'F', 0L, 0L, messageInfo));
        assertEquals(Action.CONTINUE, handler.onMessage(buffer, 0, 0, 1, session, 1, 'G', 0L, 0L, messageInfo));

        verify(pipeline, never()).publish(any(), any(), anyLong());
        verify(connection, never()).onInboundMessage(any(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void noOpLogFactoryPrivateConstructorIsReachableForCoverage() throws Exception {
        Constructor<NoOpLogFactory> constructor = NoOpLogFactory.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        NoOpLogFactory instance = constructor.newInstance();
        assertNotNull(instance);
    }

    private static Session session(String beginString,
                                   String localCompId,
                                   String remoteCompId,
                                   long sessionId,
                                   int sequenceIndex,
                                   int receivedSeq,
                                   int sentSeq,
                                   boolean connected,
                                   boolean active) {
        Session session = mock(Session.class);
        CompositeKey compositeKey = mock(CompositeKey.class);
        when(compositeKey.localCompId()).thenReturn(localCompId);
        when(compositeKey.remoteCompId()).thenReturn(remoteCompId);
        when(session.compositeKey()).thenReturn(compositeKey);
        when(session.beginString()).thenReturn(beginString);
        when(session.id()).thenReturn(sessionId);
        when(session.sequenceIndex()).thenReturn(sequenceIndex);
        when(session.lastReceivedMsgSeqNum()).thenReturn(receivedSeq);
        when(session.lastSentMsgSeqNum()).thenReturn(sentSeq);
        when(session.isConnected()).thenReturn(connected);
        when(session.isActive()).thenReturn(active);
        return session;
    }
}
