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

package com.llexsimulator.aeron;

import com.llexsimulator.config.SimulatorConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeronRuntimeTuningTest {

    @Test
    void fallsBackFromDevShmWhenUsableSpaceIsTooSmall() {
        SimulatorConfig base = new SimulatorConfig(
                "0.0.0.0",
                9880,
                "logs/quickfixj",
                false,
                8080,
                "/dev/shm/aeron-llexsim",
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
                true);

        SimulatorConfig resolved = AeronRuntimeTuning.resolve(base, 64L * 1024L * 1024L);

        assertEquals("/tmp/aeron-llexsim", resolved.aeronDir());
        assertEquals("aeron:ipc?term-length=8388608", resolved.artioLibraryAeronChannel());
        assertEquals("aeron:ipc?term-length=65536", resolved.metricsAeronChannel());
    }

    @Test
    void preservesConfiguredDirWhenSharedMemoryIsLargeEnough() {
        SimulatorConfig base = new SimulatorConfig(
                "0.0.0.0",
                9880,
                "logs/quickfixj",
                false,
                8080,
                "/dev/shm/aeron-llexsim",
                "aeron:ipc?term-length=16777216",
                "aeron:ipc?term-length=131072",
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
                true);

        SimulatorConfig resolved = AeronRuntimeTuning.resolve(base, 512L * 1024L * 1024L);

        String expectedDir = Files.isDirectory(Path.of("/dev/shm"))
                ? "/dev/shm/aeron-llexsim"
                : "/tmp/aeron-llexsim";
        assertEquals(expectedDir, resolved.aeronDir());
        assertEquals("aeron:ipc?term-length=16777216", resolved.artioLibraryAeronChannel());
        assertEquals("aeron:ipc?term-length=131072", resolved.metricsAeronChannel());
    }

    @Test
    void appendsTermLengthToExistingIpcUriParameters() {
        assertEquals(
                "aeron:ipc?alias=metrics|term-length=65536",
                AeronRuntimeTuning.ensureIpcTermLength("aeron:ipc?alias=metrics", 65536));
    }

    @Test
    void fallsBackWhenDevShmMountIsUnavailable() {
        String resolved = AeronRuntimeTuning.resolveAeronDir("/dev/shm/aeron-llexsim", Long.MAX_VALUE);
        String expected = Files.isDirectory(Path.of("/dev/shm"))
                ? "/dev/shm/aeron-llexsim"
                : "/tmp/aeron-llexsim";
        assertEquals(expected, resolved);
    }
}
