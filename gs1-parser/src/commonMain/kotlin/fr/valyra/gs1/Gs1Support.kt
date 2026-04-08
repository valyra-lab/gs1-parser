/*
 * Copyright 2026 Cyril Ponce
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.valyra.gs1

internal const val GS: Char = '\u001D'

/**
 * Utility to normalize GS1 input strings by handling symbology identifiers,
 * common separators, and encoding variants.
 */
public object Gs1Normalizer {
    /**
     * Normalizes the given raw GS1 input string.
     *
     * It performs the following steps:
     * 1. Removes GS1 Symbology Identifiers (e.g., `]C1`).
     * 2. Replaces common separator characters (`|`, `\u001C`, etc.) with the standard GS character (`\u001D`).
     * 3. Trims leading and trailing whitespace and leading GS characters.
     *
     * @param raw The raw input string to normalize.
     * @return The normalized GS1 string.
     */
    public fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("]") && s.length >= 3) {
            val prefix = s.substring(0, 3)
            if (prefix == "]C1" || prefix == "]d2" || prefix == "]Q3" || prefix == "]e0" || prefix == "]J1") s = s.drop(3)
        }
        s = s.replace("\\u00E8", GS.toString())
            .replace("\u00E8", GS.toString())
            .replace("\u001C", GS.toString())
            .replace("|", GS.toString())
        while (s.startsWith(GS)) s = s.drop(1)
        return s
    }
}

internal fun String.isNumeric(): Boolean = isNotEmpty() && all(Char::isDigit)
internal fun computeGs1CheckDigit(numberWithoutCheckDigit: String): Int {
    var sum = 0
    val reversed = numberWithoutCheckDigit.reversed()
    for (i in reversed.indices) {
        val n = reversed[i].digitToInt()
        sum += if (i % 2 == 0) n * 3 else n
    }
    return (10 - (sum % 10)) % 10
}

internal fun hasValidGs1CheckDigit(number: String): Boolean =
    number.length >= 2 && number.all(Char::isDigit) && computeGs1CheckDigit(number.dropLast(1)) == number.last()
        .digitToInt()

internal fun formatField(ai: String, spec: AiSpec, value: String): String = value
