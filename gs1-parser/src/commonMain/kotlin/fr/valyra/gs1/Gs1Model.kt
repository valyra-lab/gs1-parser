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
 * Parsing behavior configuration.
 */
public enum class ParseMode {
    /**
     * Fails on missing separators or other non-critical issues.
     */
    STRICT,

    /**
     * Tries to continue even if some GS1 rules are violated (e.g., missing GS1 separators).
     */
    LENIENT
}

/**
 * Represents a component within a GS1 Application Identifier data structure.
 *
 * @property optional Whether this component is optional.
 * @property type Data type of the component.
 * @property fixedLength Whether the component has a fixed length.
 * @property length The length of the component.
 * @property checkDigit Whether this component includes a GS1 check digit.
 * @property key Whether this component is a key field.
 * @property format Optional format specification for the component.
 */
public data class AiComponent(
    val optional: Boolean,
    val type: String,
    val fixedLength: Boolean,
    val length: Int,
    val checkDigit: Boolean = false,
    val key: Boolean = false,
    val format: String? = null
)

/**
 * Specification for a GS1 Application Identifier (AI).
 *
 * @property ai The Application Identifier digits (e.g., "01", "10").
 * @property title Human-readable title of the AI.
 * @property description Detailed description of the AI.
 * @property formatString Symbolic representation of the AI format (e.g., "n2+n14").
 * @property bodyRegex Regex pattern for the data part of the AI.
 * @property separatorRequired Whether a GS (Group Separator) is required after this AI if it's not the last one.
 * @property fixedDataLength The fixed length of the data if applicable, null otherwise.
 * @property minDataLength Minimum allowed length for the data.
 * @property maxDataLength Maximum allowed length for the data.
 * @property components Breakdown of the AI into its sub-components.
 * @property requiresAnyOf List of AI groups that are required alongside this AI.
 * @property excludes Set of AIs that cannot be present with this AI.
 * @property gs1DigitalLinkPrimaryKey Whether this AI acts as a primary key in GS1 Digital Link.
 * @property validAsDataAttribute Whether this AI can be used as a data attribute in GS1 Digital Link.
 * @property note Additional notes or usage instructions.
 */
public data class AiSpec(
    val ai: String,
    val title: String,
    val description: String,
    val formatString: String,
    val bodyRegex: String,
    val separatorRequired: Boolean,
    val fixedDataLength: Int?,
    val minDataLength: Int,
    val maxDataLength: Int,
    val components: List<AiComponent>,
    val requiresAnyOf: List<List<String>>,
    val excludes: Set<String>,
    val gs1DigitalLinkPrimaryKey: Boolean,
    val validAsDataAttribute: Boolean,
    val note: String
) {
    /**
     * Compiled regular expression for validating the AI's data body.
     */
    public val compiledRegex: Regex by lazy { Regex("^$bodyRegex$") }

    /**
     * True if the data associated with this AI has a fixed length.
     */
    public val isFixedLength: Boolean get() = fixedDataLength != null
}

/**
 * A successfully parsed GS1 field.
 *
 * @property ai The Application Identifier.
 * @property spec The specification for this AI.
 * @property value The extracted value (excluding the AI prefix).
 * @property rawSegment The raw string segment including the AI and potential separators.
 * @property startIndex Start index in the normalized input string.
 * @property endIndexExclusive End index (exclusive) in the normalized input string.
 * @property formatted Optional human-readable formatted version of the value.
 */
public data class Gs1Field(
    val ai: String,
    val spec: AiSpec,
    val value: String,
    val rawSegment: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val formatted: String? = null
)

/**
 * Error encountered during GS1 parsing.
 *
 * @property code Error code identifying the issue.
 * @property message Human-readable error message.
 * @property index Index in the input string where the error was detected.
 */
public data class Gs1Error(val code: String, val message: String, val index: Int)

/**
 * Non-fatal warning encountered during GS1 parsing.
 *
 * @property code Warning code identifying the issue.
 * @property message Human-readable warning message.
 * @property index Index in the input string where the warning was detected.
 */
public data class Gs1Warning(val code: String, val message: String, val index: Int)

/**
 * Result of a GS1 parsing operation.
 *
 * @property raw The original input string.
 * @property normalized The normalized version of the input string.
 * @property fields List of successfully parsed [Gs1Field]s.
 * @property errors List of [Gs1Error]s encountered.
 * @property warnings List of [Gs1Warning]s encountered.
 * @property remainder Any unparsed part of the input string.
 */
public data class Gs1ParseResult(
    val raw: String,
    val normalized: String,
    val fields: List<Gs1Field>,
    val errors: List<Gs1Error>,
    val warnings: List<Gs1Warning>,
    val remainder: String
) {
    /**
     * True if at least one GS1 field was successfully parsed.
     */
    public val isGs1: Boolean get() = fields.isNotEmpty()

    /**
     * True if any errors were encountered during parsing.
     */
    public val hasErrors: Boolean get() = errors.isNotEmpty()

    /**
     * Retrieves the first field matching the given [ai].
     */
    public fun get(ai: String): Gs1Field? = fields.firstOrNull { it.ai == ai }

    /**
     * Retrieves all fields matching the given [ai].
     */
    public fun getAll(ai: String): List<Gs1Field> = fields.filter { it.ai == ai }
}
