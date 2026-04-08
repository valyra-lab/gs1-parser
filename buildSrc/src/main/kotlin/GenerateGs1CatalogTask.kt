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

import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI

abstract class GenerateGs1CatalogTask : DefaultTask() {
    @get:Input
    abstract val datasetUrl: Property<String>

    @get:Input
    abstract val chunkSize: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val raw = URI(datasetUrl.get()).toURL().readText()
        val root = Json.parseToJsonElement(raw).jsonObject

        val items = root["applicationIdentifiers"]
            ?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it["applicationIdentifier"] != null }
            ?.sortedWith(
                compareBy(
                    { it["applicationIdentifier"]!!.jsonPrimitive.content.length },
                    { it["applicationIdentifier"]!!.jsonPrimitive.content }
                )
            )
            ?: emptyList()

        val sourceRoot = outputDir.get().asFile
        val packageDir = sourceRoot.resolve("fr/valyra/gs1/generated")
        packageDir.mkdirs()

        packageDir.listFiles()
            ?.filter { it.name.startsWith("Gs1GeneratedAiCatalog") && it.extension == "kt" }
            ?.forEach { it.delete() }

        val parts = items.chunked(chunkSize.get())
        val rootCalls = mutableListOf<String>()

        parts.forEachIndexed { index, chunk ->
            val partNumber = index + 1
            val funcName = "gs1GeneratedAiCatalogPart%02d".format(partNumber)
            val fileName = packageDir.resolve("Gs1GeneratedAiCatalogPart%02d.kt".format(partNumber))

            val content = buildString {
                appendLine("package fr.valyra.gs1.generated")
                appendLine()
                appendLine("import fr.valyra.gs1.AiComponent")
                appendLine("import fr.valyra.gs1.AiSpec")
                appendLine()
                appendLine("internal fun $funcName(): List<AiSpec> = listOf(")
                chunk.forEachIndexed { i, item ->
                    append(aiSpecBlock(item, isLast = i == chunk.lastIndex))
                    appendLine()
                }
                appendLine(")")
            }

            fileName.writeText(content)
            rootCalls += "            addAll($funcName())"
        }

        val rootFile = packageDir.resolve("Gs1GeneratedAiCatalog.kt")
        rootFile.writeText(
            buildString {
                appendLine("package fr.valyra.gs1.generated")
                appendLine()
                appendLine("import fr.valyra.gs1.AiSpec")
                appendLine()
                appendLine("public object Gs1GeneratedAiCatalog {")
                appendLine("    public val specs: List<AiSpec> by lazy(LazyThreadSafetyMode.NONE) {")
                appendLine("        buildList {")
                rootCalls.forEach { appendLine(it) }
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            }
        )

        logger.lifecycle(
            "Generated ${items.size} AIs into ${parts.size} Kotlin files in ${packageDir.absolutePath}"
        )
    }

    private fun aiSpecBlock(item: JsonObject, isLast: Boolean): String {
        val ai = item.stringField("applicationIdentifier")
        val comps = item["components"]?.jsonArray ?: JsonArray(emptyList())

        val compEntries = comps.map { component ->
            val c = component.jsonObject
            val formatCode = nullableStringCode(c["format"])

            "AiComponent(" +
                    "optional=${c.booleanField("optional")}, " +
                    "type=${kstr(c.stringField("type"))}, " +
                    "fixedLength=${c.booleanField("fixedLength")}, " +
                    "length=${c.intField("length")}, " +
                    "checkDigit=${c.booleanField("checkDigit")}, " +
                    "key=${c.booleanField("key")}, " +
                    "format=$formatCode" +
                    ")"
        }

        val compCode = if (compEntries.isEmpty()) {
            "listOf()"
        } else {
            "listOf(${compEntries.joinToString(", ")})"
        }

        val reqCode = requiresCode(item["requires"])
        val exCode = excludesCode(item["excludes"])
        val fixed = fixedLen(comps)
        val suffix = if (isLast) "" else ","

        return buildString {
            appendLine("    AiSpec(")
            appendLine("        ai = ${kstr(ai)},")
            appendLine("        title = ${kstr(item.stringField("title"))},")
            appendLine("        description = ${kstr(item.stringField("description"))},")
            appendLine("        formatString = ${kstr(item.stringField("formatString"))},")
            appendLine("        bodyRegex = ${kstr(item.stringField("regex"))},")
            appendLine("        separatorRequired = ${item.booleanField("separatorRequired")},")
            appendLine("        fixedDataLength = ${fixed?.toString() ?: "null"},")
            appendLine("        minDataLength = ${minLen(comps)},")
            appendLine("        maxDataLength = ${maxLen(comps)},")
            appendLine("        components = $compCode,")
            appendLine("        requiresAnyOf = $reqCode,")
            appendLine("        excludes = $exCode,")
            appendLine("        gs1DigitalLinkPrimaryKey = ${item.booleanField("gs1DigitalLinkPrimaryKey")},")
            appendLine("        validAsDataAttribute = ${item.booleanField("validAsDataAttribute")},")
            appendLine("        note = ${kstr(item.stringField("note"))}")
            append("    )$suffix")
        }
    }

    private fun requiresCode(element: JsonElement?): String {
        if (element == null || element is JsonNull) return "listOf()"

        val groups = mutableListOf<List<String>>()

        when (element) {
            is JsonArray -> {
                element.forEach { entry ->
                    when (entry) {
                        is JsonArray -> {
                            val values = entry.flatMap { collectStringValues(it) }.distinct()
                            if (values.isNotEmpty()) groups += values
                        }

                        else -> {
                            val values = collectStringValues(entry)
                            if (values.isNotEmpty()) groups += listOf(values.first())
                        }
                    }
                }
            }

            else -> {
                val values = collectStringValues(element)
                if (values.isNotEmpty()) groups += listOf(values.first())
            }
        }

        return if (groups.isEmpty()) {
            "listOf()"
        } else {
            "listOf(" + groups.joinToString(", ") { group ->
                "listOf(" + group.joinToString(", ") { kstr(it) } + ")"
            } + ")"
        }
    }

    private fun excludesCode(element: JsonElement?): String {
        if (element == null || element is JsonNull) return "emptySet()"

        val values = linkedSetOf<String>()
        collectStringValues(element).forEach(values::add)

        return if (values.isEmpty()) {
            "emptySet()"
        } else {
            "setOf(" + values.sorted().joinToString(", ") { kstr(it) } + ")"
        }
    }

    private fun collectStringValues(element: JsonElement): List<String> {
        val output = mutableListOf<String>()

        fun visit(current: JsonElement) {
            when (current) {
                is JsonPrimitive -> {
                    current.contentOrNull?.let(output::add)
                }

                is JsonObject -> {
                    val startStr = current["start"]?.primitiveContent()
                    val endStr = current["end"]?.primitiveContent()
                    val directValue = current["@value"]?.primitiveContent()
                        ?: current["value"]?.primitiveContent()
                        ?: current["applicationIdentifier"]?.primitiveContent()

                    if (startStr != null && endStr != null) {
                        val start = startStr.toIntOrNull()
                        val end = endStr.toIntOrNull()
                        if (start != null && end != null) {
                            val width = startStr.length
                            for (n in start..end) {
                                output += n.toString().padStart(width, '0')
                            }
                        }
                    } else if (directValue != null) {
                        output += directValue
                    }
                }

                is JsonArray -> current.forEach(::visit)
            }
        }

        visit(element)
        return output
    }

    private fun nullableStringCode(element: JsonElement?): String {
        val value = element?.let { collectStringValues(it).firstOrNull() }
        return if (value == null) "null" else kstr(value)
    }

    private fun fixedLen(components: JsonArray): Int? =
        if (components.all { it.jsonObject.booleanField("fixedLength") }) {
            components.sumOf { it.jsonObject.intField("length") }
        } else {
            null
        }

    private fun minLen(components: JsonArray): Int =
        components.sumOf {
            val c = it.jsonObject
            when {
                c.booleanField("fixedLength") -> c.intField("length")
                c.booleanField("optional") -> 0
                else -> 1
            }
        }

    private fun maxLen(components: JsonArray): Int =
        components.sumOf { it.jsonObject.intField("length") }

    private fun kstr(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""

    private fun JsonElement.primitiveContent(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringField(name: String): String =
        this[name]?.let { collectStringValues(it).firstOrNull() } ?: ""

    private fun JsonObject.intField(name: String): Int =
        stringField(name).toIntOrNull() ?: 0

    private fun JsonObject.booleanField(name: String): Boolean =
        stringField(name).toBooleanStrictOrNull() ?: false
}
