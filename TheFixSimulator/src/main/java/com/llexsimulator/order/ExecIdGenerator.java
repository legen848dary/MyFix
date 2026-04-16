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

package com.llexsimulator.order;

/** Zero-GC execution-report ID generator — same mechanics as {@link OrderIdGenerator}. */
public final class ExecIdGenerator {

    private final OrderIdGenerator delegate;

    public ExecIdGenerator() {
        this.delegate = new OrderIdGenerator("E", System.currentTimeMillis() + 1_000_000L);
    }

    public long nextId() { return delegate.nextId(); }

    public int nextId(byte[] dest, int offset) { return delegate.nextId(dest, offset); }

    public int nextId(char[] dest, int offset) { return delegate.nextId(dest, offset); }
}
