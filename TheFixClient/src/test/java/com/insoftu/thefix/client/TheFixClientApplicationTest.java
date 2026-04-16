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

package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientApplicationTest {

    @Test
    void startupMessagePointsToTheWebWorkstationUrl() {
        assertEquals(
                "TheFixClient trader workstation is available at http://localhost:8081",
                TheFixClientApplication.startupMessage(new TheFixClientConfig(
                        "0.0.0.0",
                        8081,
                        "localhost",
                        9880,
                        "FIX.4.4",
                        "THEFIX_TRDR01",
                        "LLEXSIM",
                        "FIX.4.4",
                        30,
                        5,
                        25,
                        "logs/thefixclient/test-quickfixj",
                        false,
                        480,
                        1
                ))
        );
    }
}
