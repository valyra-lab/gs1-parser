# GS1 Parser for Kotlin Multiplatform

[![Maven Central](https://img.shields.io/maven-central/v/fr.valyra/gs1-parser.svg)](https://search.maven.org/artifact/fr.valyra/gs1-parser)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

`gs1-parser` is a modern Kotlin Multiplatform (KMP) library designed for parsing and validating GS1 barcodes (GS1-128, GS1 DataMatrix, GS1 QR Code). It uses an Application Identifier (AI) catalog generated directly from the official GS1 specifications.

## 🚀 Features

- **Kotlin Multiplatform**: Supports JVM, Android, iOS, JS, and more.
- **Up-to-date Catalog**: AI specifications are generated directly from the official GS1 JSON-LD (`ref.gs1.org`).
- **Strict Validation**: Checks for fixed/variable lengths, regular expressions (regex), check digits, and AI dependencies (exclusions, prerequisites).
- **Smart Normalization**: Automatically handles symbology identifiers (e.g., `]C1`, `]d2`) and various group separators (GS).
- **Parsing Modes**: Choose between `STRICT` mode (rejects minor formatting issues) or `LENIENT` mode (attempts to recover data despite formatting errors).

## 📦 Installation

Add the dependency to your `build.gradle.kts` file:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("fr.valyra:gs1-parser:1.0.1")
            }
        }
    }
}
```

## 📖 Usage

### Simple Parsing

The `Gs1` object provides a simplified entry point for parsing a raw string from a scanner.

```kotlin
import fr.valyra.gs1.Gs1
import fr.valyra.gs1.ParseMode

val rawInput = "01034123456789081521123110ABC123"
val result = Gs1.parse(rawInput, ParseMode.LENIENT)

if (result.isGs1) {
    val gtin = result.get("01")?.value      // "03412345678908"
    val expiry = result.get("15")?.value    // "211231"
    val batch = result.get("10")?.value     // "ABC123"
    
    println("Product: ${result.get("01")?.spec?.title}")
}
```

### Understanding the Result (`Gs1ParseResult`)

The parsing result provides detailed information:

- `fields`: A list of `Gs1Field` containing the AI, its raw value, formatted value, and full specification.
- `errors`: Fatal errors that prevented full parsing (unknown AI, invalid format, incorrect check digit).
- `warnings`: Non-blocking warnings (e.g., missing separator in `LENIENT` mode).
- `remainder`: The part of the string that could not be parsed.

### Advanced Validation Example

```kotlin
val result = Gs1.parse("0104046241110150", ParseMode.STRICT)

if (result.hasErrors) {
    result.errors.forEach { error ->
        // e.g., "CHECK_DIGIT" -> "Invalid check digit for AI 01"
        println("Error [${error.code}]: ${error.message}")
    }
}
```

## 🔍 FNC1 Character and Separators

GS1 barcodes use a special character called **FNC1** to delimit variable-length fields. In data transmitted by a scanner, this character is often represented by:
- The ASCII **GS** character (Group Separator, ASCII 29, `\u001D`).
- Symbology identifiers (e.g., `]C1` at the beginning of the barcode).
- Alternative characters such as `|` or `\u00E8` depending on hardware configuration.

**`gs1-parser` natively handles these cases** by normalizing the input before parsing. It removes symbology identifiers and converts common separators into the standard `\u001D` character.

## 🏗️ GS1 Field Structure

Each extracted field (`Gs1Field`) provides access to its official definition:

| Property | Description |
|-----------|-------------|
| `ai`      | The Application Identifier (e.g., "01", "10", "17") |
| `value`   | The extracted data without the AI |
| `spec`    | `AiSpec` object containing the title, description, and validation rules |
| `formatted`| (Optional) Formatted version of the data |

## 🛠️ Catalog Generation

Unlike other libraries, `gs1-parser` does not use a static list. A Gradle task (`GenerateGs1CatalogTask`) downloads the latest definitions from `ref.gs1.org`. This ensures your project remains compliant even when GS1 adds new Application Identifiers.

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.

## ✍️ Author

Developed by [Cyril Ponce](https://github.com/cyril-ponce).
