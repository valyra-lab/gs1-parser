---
name: Bug report
about: Report an issue with parsing, validation, or the AI catalog
title: ''
labels: bug
assignees: ''

---

**Describe the bug**
A clear and concise description of what the bug is (wrong parsing result, validation false positive/negative, crash, etc.).

**Input data**
The raw barcode string that triggers the issue (redact any real/sensitive GTIN, batch, or serial data if needed).

```
01034123456789081521123110ABC123
```

- Symbology: [e.g. GS1-128, GS1 DataMatrix, GS1 QR Code, raw string with no symbology identifier]
- Parse mode: [`STRICT` or `LENIENT`]

**To Reproduce**
Minimal code snippet that reproduces the behavior:

```kotlin
val result = Gs1.parse("01034123456789081521123110ABC123", ParseMode.LENIENT)
```

**Expected behavior**
What you expected `Gs1ParseResult` to contain (fields, values, `formatted`, etc.).

**Actual behavior**
What actually happened: incorrect field(s), unexpected `errors`/`warnings`, unexpected `remainder`, or an exception (include stack trace if any).

```
Error [CODE]: message
```

**Environment (please complete the following information):**

- `gs1-parser` version: [e.g. 1.0.2]
- Target platform: [e.g. JVM, Android, iOS, JS, Native]
- Kotlin version: [e.g. 2.4.20]
- OS: [e.g. macOS 15]

**Additional context**
Reference to the relevant Application Identifier(s) (e.g. AI 01, AI 17) or to the GS1 General Specifications section, if applicable.
