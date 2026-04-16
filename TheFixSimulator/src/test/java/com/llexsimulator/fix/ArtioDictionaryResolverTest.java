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

package com.llexsimulator.fix;

import org.junit.jupiter.api.Test;
import uk.co.real_logic.artio.dictionary.FixDictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArtioDictionaryResolverTest {

    @Test
    void resolvesGeneratedFix44Dictionary() throws Exception {
        Class<? extends FixDictionary> dictionaryClass = ArtioDictionaryResolver.resolve();

        assertNotNull(dictionaryClass);
        assertEquals("uk.co.real_logic.artio.FixDictionaryImpl", dictionaryClass.getName());
        assertEquals("FIX.4.4", dictionaryClass.getDeclaredConstructor().newInstance().beginString());
    }
}
