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

import uk.co.real_logic.artio.dictionary.FixDictionary;

/**
 * Resolves the generated Artio FIX dictionary without creating a direct source
 * dependency on generated code at IDE import time.
 */
public final class ArtioDictionaryResolver {

    static final String GENERATED_DICTIONARY_CLASS = "uk.co.real_logic.artio.FixDictionaryImpl";

    private ArtioDictionaryResolver() {}

    @SuppressWarnings("unchecked")
    public static Class<? extends FixDictionary> resolve() {
        try {
            Class<?> dictionaryClass = Class.forName(
                    GENERATED_DICTIONARY_CLASS,
                    true,
                    ArtioDictionaryResolver.class.getClassLoader());
            if (!FixDictionary.class.isAssignableFrom(dictionaryClass)) {
                throw new IllegalStateException(
                        "Resolved Artio dictionary class '" + GENERATED_DICTIONARY_CLASS
                                + "' does not implement FixDictionary");
            }
            return (Class<? extends FixDictionary>)dictionaryClass;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Generated Artio FIX dictionary class '" + GENERATED_DICTIONARY_CLASS
                            + "' is missing. Run './gradlew generateArtioSources' or './gradlew build' first.",
                    e);
        }
    }
}
