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

/**
 * Main entry point for GS1 parsing operations.
 *
 * This object provides a simple API to parse GS1-128 or GS1 DataMatrix strings
 * into structured data according to GS1 Application Identifier specifications.
 */
public object Gs1 {
    private val parser by lazy { Gs1Parser.default() }

    /**
     * Parses a raw GS1 input string.
     *
     * @param rawInput The raw GS1 string (e.g., from a barcode scan).
     * @param mode The parsing mode ([ParseMode.STRICT] or [ParseMode.LENIENT]). Defaults to [ParseMode.LENIENT].
     * @return A [Gs1ParseResult] containing the structured fields, errors, and warnings.
     */
    public fun parse(rawInput: String, mode: ParseMode = ParseMode.LENIENT): Gs1ParseResult = parser.parse(rawInput, mode)
}
