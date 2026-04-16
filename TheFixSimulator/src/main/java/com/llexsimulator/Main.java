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

package com.llexsimulator;

import com.llexsimulator.aeron.AeronRuntimeTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for LLExSimulator.
 *
 * <p>JVM flags for production (applied via JAVA_OPTS or Dockerfile ENV):
 * <pre>
 *   -XX:+UseZGC -XX:+ZGenerational   ← Java 21 Generational ZGC; sub-ms pauses
 *   -Xms512m -Xmx512m                 ← fixed heap; eliminates resize safepoints
 *   -XX:+AlwaysPreTouch               ← pre-fault heap pages at startup
 *   -XX:+DisableExplicitGC            ← prevent library System.gc() calls
 *   -XX:+PerfDisableSharedMem         ← disable JMX perf shared memory
 *   -Daeron.threading.mode=DEDICATED  ← optional override; config file can set it too
 *   -Daeron.sender.idle.strategy=noop ← optional override; config file can set it too
 *   -Daeron.receiver.idle.strategy=noop
 *   -Dagrona.disable.bounds.checks=true ← skip UnsafeBuffer bounds checks
 *   --add-opens java.base/sun.nio.ch=ALL-UNNAMED
 *   --add-opens java.base/java.nio=ALL-UNNAMED
 *   --add-opens java.base/java.lang=ALL-UNNAMED
 * </pre>
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Apply system properties required by Aeron/Agrona BEFORE any class loading
        setSystemPropertyIfAbsent("aeron.ipc.term.buffer.length",
                Integer.toString(AeronRuntimeTuning.DEFAULT_ARTIO_IPC_TERM_LENGTH_BYTES));
        setSystemPropertyIfAbsent("agrona.disable.bounds.checks",  "true");

        SimulatorBootstrap bootstrap = new SimulatorBootstrap();

        Runtime.getRuntime().addShutdownHook(
            Thread.ofPlatform().name("shutdown-hook").unstarted(bootstrap::stop)
        );

        try {
            bootstrap.start();
        } catch (Exception e) {
            log.error("Fatal: LLExSimulator failed to start", e);
            System.exit(1);
        }
    }

    private static void setSystemPropertyIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
