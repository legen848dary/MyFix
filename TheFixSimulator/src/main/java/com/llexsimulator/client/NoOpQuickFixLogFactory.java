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

package com.llexsimulator.client;

import quickfix.Log;
import quickfix.LogFactory;
import quickfix.SessionID;

/**
 * Suppresses QuickFIX/J raw session/message logging when the demo client is run
 * in high-rate mode and file logs are not desired.
 */
public final class NoOpQuickFixLogFactory implements LogFactory {

    private static final Log NO_OP_LOG = new Log() {
        @Override
        public void clear() {
        }

        @Override
        public void onIncoming(String message) {
        }

        @Override
        public void onOutgoing(String message) {
        }

        @Override
        public void onEvent(String text) {
        }

        @Override
        public void onErrorEvent(String text) {
        }
    };


    @Override
    public Log create(SessionID sessionID) {
        return NO_OP_LOG;
    }
}
