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

import fr.valyra.gs1.generated.Gs1GeneratedAiCatalog

/**
 * Core engine for parsing GS1 strings.
 *
 * It uses a set of [AiSpec] to identify and validate Application Identifiers and their values.
 * Use [Gs1Parser.default] to get a parser pre-configured with the official GS1 Application Identifiers.
 */
public class Gs1Parser private constructor(private val specs: Map<String, AiSpec>) {
    /**
     * Parses a raw input string into a structured [Gs1ParseResult].
     *
     * @param rawInput The input string to parse.
     * @param mode The [ParseMode] to use.
     * @return The result of the parsing operation.
     */
    public fun parse(rawInput: String, mode: ParseMode): Gs1ParseResult {
        val normalized = Gs1Normalizer.normalize(rawInput)
        val fields = mutableListOf<Gs1Field>()
        val errors = mutableListOf<Gs1Error>()
        val warnings = mutableListOf<Gs1Warning>()
        var index = 0
        while (index < normalized.length) {
            if (normalized[index] == GS) {
                warnings += Gs1Warning("UNEXPECTED_GS", "Ignored separator", index)
                index++
                continue
            }
            val ai = findAi(normalized, index) ?: return Gs1ParseResult(
                rawInput,
                normalized,
                fields,
                errors + Gs1Error("UNKNOWN_AI", "Unknown AI at index $index", index),
                warnings,
                normalized.substring(index)
            )
            val field = parseField(normalized, index, ai, mode, warnings) ?: return Gs1ParseResult(
                rawInput,
                normalized,
                fields,
                errors + Gs1Error("INVALID_FIELD", "Cannot parse AI $ai", index),
                warnings,
                normalized.substring(index)
            )
            fields += field
            index = field.endIndexExclusive
        }
        validate(fields, errors, warnings)
        return Gs1ParseResult(rawInput, normalized, fields, errors, warnings, "")
    }

    private fun parseField(
        input: String,
        aiStart: Int,
        ai: String,
        mode: ParseMode,
        warnings: MutableList<Gs1Warning>
    ): Gs1Field? {
        val spec = specs[ai] ?: return null
        val bodyStart = aiStart + ai.length
        if (spec.isFixedLength) {
            val bodyEnd = bodyStart + spec.fixedDataLength!!
            if (bodyEnd > input.length) return null
            val value = input.substring(bodyStart, bodyEnd)
            if (!spec.compiledRegex.matches(value)) return null
            val end = if (bodyEnd < input.length && input[bodyEnd] == GS) bodyEnd + 1 else bodyEnd
            return Gs1Field(
                ai,
                spec,
                value,
                input.substring(aiStart, end),
                aiStart,
                end,
                formatField(ai, spec, value)
            )
        }
        val maxEnd = minOf(input.length, bodyStart + spec.maxDataLength)
        val gsIndex = input.indexOf(GS, bodyStart).takeIf { it != -1 && it <= maxEnd }
        if (gsIndex != null) {
            val value = input.substring(bodyStart, gsIndex)
            if (value.length !in spec.minDataLength..spec.maxDataLength) return null
            if (!spec.compiledRegex.matches(value)) return null
            return Gs1Field(
                ai,
                spec,
                value,
                input.substring(aiStart, gsIndex + 1),
                aiStart,
                gsIndex + 1,
                formatField(ai, spec, value)
            )
        }

        // Handle variable length AI at the end of string or without GS
        // To avoid premature match of an AI inside the data (e.g., "17" in "F2LK272"),
        // we should favor the LARGEST match even in LENIENT mode IF it leads to a valid parsing of the rest.
        // But since we can't easily backtrack fully without a complex recursive parser,
        // we follow the specified preference.
        val range = if (mode == ParseMode.LENIENT)
            maxEnd downTo (bodyStart + spec.minDataLength)
        else (bodyStart + spec.minDataLength)..maxEnd

        for (candidateEnd in range) {
            val value = input.substring(bodyStart, candidateEnd)
            if (!spec.compiledRegex.matches(value)) continue

            val isAtEnd = candidateEnd == input.length
            val nextAi = if (!isAtEnd) findAi(input, candidateEnd) else null

            if (isAtEnd || nextAi != null) {
                if (spec.separatorRequired && !isAtEnd && input[candidateEnd] != GS) {
                    if (mode == ParseMode.STRICT) return null // Fail if separator required but missing
                    warnings += Gs1Warning(
                        "MISSING_SEPARATOR",
                        "AI $ai should be terminated by GS before next AI",
                        candidateEnd
                    )
                }
                return Gs1Field(
                    ai,
                    spec,
                    value,
                    input.substring(aiStart, candidateEnd),
                    aiStart,
                    candidateEnd,
                    formatField(ai, spec, value)
                )
            }
        }
        return null
    }

    private fun findAi(input: String, offset: Int): String? {
        for (len in 4 downTo 2) {
            if (offset + len > input.length) continue
            val candidate = input.substring(offset, offset + len)
            if (specs.containsKey(candidate)) return candidate
        }
        return null
    }

    private fun validate(fields: List<Gs1Field>, errors: MutableList<Gs1Error>, warnings: MutableList<Gs1Warning>) {
        val present = fields.map { it.ai }.toSet()
        fields.forEach { field ->
            val requiresOk =
                field.spec.requiresAnyOf.isEmpty() || field.spec.requiresAnyOf.any { group -> group.all { it in present } }
            if (!requiresOk) errors += Gs1Error(
                "REQUIRES",
                "AI ${field.ai} requires one of ${field.spec.requiresAnyOf}",
                field.startIndex
            )
            val excludedHit = field.spec.excludes.firstOrNull { it in present }
            if (excludedHit != null) errors += Gs1Error(
                "EXCLUDES",
                "AI ${field.ai} excludes AI $excludedHit",
                field.startIndex
            )
            validateCheckDigits(field, errors)
        }
        if (fields.count { it.ai == "01" } > 1) warnings += Gs1Warning(
            "MULTIPLE_GTIN",
            "Multiple GTIN fields found",
            fields.first { it.ai == "01" }.startIndex
        )
    }

    private fun validateCheckDigits(field: Gs1Field, errors: MutableList<Gs1Error>) {
        var cursor = 0
        for (component in field.spec.components) {
            if (!component.fixedLength) continue
            if (cursor + component.length > field.value.length) break
            val chunk = field.value.substring(
                cursor,
                cursor + component.length
            )
            if (component.checkDigit && chunk.isNumeric() && !hasValidGs1CheckDigit(chunk)) errors += Gs1Error(
                "CHECK_DIGIT",
                "Invalid check digit for AI ${field.ai}",
                field.startIndex
            )
            cursor += component.length
        }
    }

    public companion object {
        /**
         * Creates a [Gs1Parser] instance initialized with the default GS1 Application Identifier catalog.
         */
        public fun default(): Gs1Parser = Gs1Parser(Gs1GeneratedAiCatalog.specs.associateBy { it.ai })
    }
}
