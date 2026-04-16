/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.llexsimulator.engine;

import org.agrona.collections.Long2ObjectHashMap;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.session.SessionWriter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maps numeric session connection IDs (used on the hot path) to Artio-backed
 * {@link FixConnection} objects used for outbound execution reports and the UI.
 *
 * <p>Write operations (logon/logout) happen rarely — synchronization overhead
 * is acceptable. Reads on the hot path use the lock-free Agrona map.
 */
public final class OrderSessionRegistry {

    private static final int RECENT_DISCONNECT_LIMIT = 32;

    private final Long2ObjectHashMap<FixConnection> idToConnection = new Long2ObjectHashMap<>();
    private final AtomicLong                        counter        = new AtomicLong(0L);
    private final Deque<SessionDiagnosticsSnapshot> recentDisconnects = new ArrayDeque<>(RECENT_DISCONNECT_LIMIT);
    private final boolean benchmarkModeEnabled;

    public OrderSessionRegistry(boolean benchmarkModeEnabled) {
        this.benchmarkModeEnabled = benchmarkModeEnabled;
    }

    /** Assigns a new numeric ID and registers the Artio session. */
    public synchronized FixConnection register(Session session, SessionWriter writer) {
        long id = counter.incrementAndGet();
        FixConnection connection = new FixConnection(id, session, writer, benchmarkModeEnabled);
        idToConnection.put(id, connection);
        return connection;
    }

    public synchronized FixConnection remove(long connectionId) {
        return idToConnection.remove(connectionId);
    }

    public synchronized FixConnection archiveAndRemove(long connectionId) {
        FixConnection removed = idToConnection.remove(connectionId);
        if (removed != null) {
            if (recentDisconnects.size() == RECENT_DISCONNECT_LIMIT) {
                recentDisconnects.removeLast();
            }
            recentDisconnects.addFirst(removed.snapshot());
        }
        return removed;
    }

    /** Hot-path read — returns {@code null} if not found. */
    public FixConnection get(long connectionId) {
        return idToConnection.get(connectionId);
    }

    public synchronized int activeCount() {
        return idToConnection.size();
    }

    public synchronized List<FixConnection> getAllConnections() {
        return new ArrayList<>(idToConnection.values());
    }

    public synchronized List<SessionDiagnosticsSnapshot> getRecentDisconnects(int limit) {
        int effectiveLimit = Math.max(0, Math.min(limit, RECENT_DISCONNECT_LIMIT));
        List<SessionDiagnosticsSnapshot> snapshots = new ArrayList<>(effectiveLimit);
        int count = 0;
        for (SessionDiagnosticsSnapshot snapshot : recentDisconnects) {
            if (count++ >= effectiveLimit) {
                break;
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }
}
