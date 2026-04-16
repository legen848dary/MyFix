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

import java.util.List;

/**
 * Immutable diagnostics snapshot for active or recently disconnected FIX sessions.
 */
public record SessionDiagnosticsSnapshot(
        long connectionId,
        long artioSessionId,
        String sessionKey,
        String beginString,
        String senderCompId,
        String targetCompId,
        boolean loggedOn,
        long msgCount,
        int sequenceIndex,
        int lastReceivedMsgSeqNum,
        int lastSentMsgSeqNum,
        long inboundMessageCount,
        long outboundSendSuccessCount,
        long outboundBackpressureCount,
        long outboundSendFailureCount,
        long timeoutCount,
        long disconnectCount,
        long slowStatusChangeCount,
        boolean slowConsumer,
        String lastInboundMsgType,
        String lastOutboundEvent,
        String lastDisconnectReason,
        long lastLibraryPosition,
        long connectedAtEpochMs,
        long lastLogonAtEpochMs,
        long lastInboundAtEpochMs,
        long lastOutboundAtEpochMs,
        long lastTimeoutAtEpochMs,
        long lastDisconnectAtEpochMs,
        List<String> recentEvents
) {
}
