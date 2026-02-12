# Exporters

## Langfuse

[Langfuse](https://langfuse.com/) is an open-source LLM engineering platform for observability, metrics, evaluations,
and prompt management.

| Property                 | Type                     | Required | Default                      | Environment Variable  | Description                                                               |
|--------------------------|--------------------------|----------|------------------------------|-----------------------|---------------------------------------------------------------------------|
| `langfuseUrl`            | `String?`                | No       | `https://cloud.langfuse.com` | `LANGFUSE_URL`        | Langfuse server URL                                                       |
| `langfusePublicKey`      | `String?`                | Yes      | -                            | `LANGFUSE_PUBLIC_KEY` | Langfuse public API key                                                   |
| `langfuseSecretKey`      | `String?`                | Yes      | -                            | `LANGFUSE_SECRET_KEY` | Langfuse secret API key                                                   |
| `exporterTimeoutSeconds` | `Long`                   | No       | `10`                         | -                     | Timeout for span export (seconds)                                         |
| `settings`               | `ExporterCommonSettings` | No       | `ExporterCommonSettings()`   | -                     | [Common exporter settings](sdk-configuration.md#common-exporter-settings) |

### Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import ai.jetbrains.tracy.core.exporters.langfuse.LangfuseExporterConfig
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->

```kotlin
// Using environment variables
// (don't forget to set `LANGFUSE_URL`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` env variables)
val sdk = configureOpenTelemetrySdk(LangfuseExporterConfig())

// Or with explicit configuration
val sdkExplicit = configureOpenTelemetrySdk(
    LangfuseExporterConfig(
        langfuseUrl = "https://cloud.langfuse.com",
        langfusePublicKey = "pk-lf-...",
        langfuseSecretKey = "sk-lf-...",
        exporterTimeoutSeconds = 15,
        settings = ExporterCommonSettings(
            traceToConsole = true // Also log to console for debugging
        )
    )
)

TracingManager.setSdk(sdk)
```

<!--- KNIT example-otel-exporters-04.kt -->

See the full
example: [LangfuseExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/backends/LangfuseExample.kt)

---

## Weave (Weights & Biases) { #weave }

[W&B Weave](https://wandb.ai/site/weave/) is the Weights & Biases platform for LLM application tracing and evaluation.

| Property                 | Type                     | Required | Default                    | Environment Variable | Description                                                               |
|--------------------------|--------------------------|----------|----------------------------|----------------------|---------------------------------------------------------------------------|
| `weaveUrl`               | `String?`                | No       | `https://trace.wandb.ai`   | `WEAVE_URL`          | Weave OTLP endpoint URL                                                   |
| `weaveEntity`            | `String?`                | Yes      | -                          | `WEAVE_ENTITY`       | W&B entity (team/org) name                                                |
| `weaveProjectName`       | `String?`                | Yes      | -                          | `WEAVE_PROJECT_NAME` | W&B project name                                                          |
| `weaveApiKey`            | `String?`                | Yes      | -                          | `WEAVE_API_KEY`      | W&B API key                                                               |
| `exporterTimeoutSeconds` | `Long`                   | No       | `10`                       | -                    | Timeout for span export (seconds)                                         |
| `settings`               | `ExporterCommonSettings` | No       | `ExporterCommonSettings()` | -                    | [Common exporter settings](sdk-configuration.md#common-exporter-settings) |

### Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import ai.jetbrains.tracy.core.exporters.otlp.WeaveExporterConfig
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->

```kotlin
// Using environment variables
// (don't forget to set `WEAVE_URL`, `WEAVE_ENTITY`, `WEAVE_PROJECT_NAME`, `WEAVE_API_KEY` env variables)
val sdk = configureOpenTelemetrySdk(WeaveExporterConfig())

// Or with explicit configuration
val sdkExplicit = configureOpenTelemetrySdk(
    WeaveExporterConfig(
        weaveUrl = "https://trace.wandb.ai",
        weaveEntity = "my-team",
        weaveProjectName = "my-ai-project",
        weaveApiKey = "your-wandb-api-key",
        exporterTimeoutSeconds = 15,
        settings = ExporterCommonSettings(
            traceToConsole = true
        )
    )
)

TracingManager.setSdk(sdk)
```

<!--- KNIT example-otel-exporters-05.kt -->

See the full
example: [WeaveExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/backends/WeaveExample.kt)

---

## OTLP HTTP

Export spans to any OTLP HTTP-compatible collector such as Jaeger, Grafana Tempo, or other OpenTelemetry backends.

| Property                 | Type                     | Required | Default                    | Description                                                               |
|--------------------------|--------------------------|----------|----------------------------|---------------------------------------------------------------------------|
| `url`                    | `String`                 | Yes      | -                          | Base URL of the OTLP HTTP collector endpoint                              |
| `exporterTimeoutSeconds` | `Long`                   | No       | `10`                       | Timeout for span export (seconds)                                         |
| `settings`               | `ExporterCommonSettings` | No       | `ExporterCommonSettings()` | [Common exporter settings](sdk-configuration.md#common-exporter-settings) |

The exporter automatically appends `/v1/traces` to the provided URL.

### Example (Jaeger)

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import ai.jetbrains.tracy.core.exporters.otlp.OtlpHttpExporterConfig
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->

```kotlin
// Export to Jaeger running locally
val sdk = configureOpenTelemetrySdk(
    OtlpHttpExporterConfig(
        url = "http://localhost:4318",
        exporterTimeoutSeconds = 10,
        settings = ExporterCommonSettings(
            traceToConsole = true
        )
    )
)

TracingManager.setSdk(sdk)
```

<!--- KNIT example-otel-exporters-06.kt -->

See the full
example: [JaegerExporterExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/backends/JaegerExporterExample.kt)

---

## Console

Export traces to the console for local development and debugging.

| Property   | Type                     | Required | Default                    | Description                                                               |
|------------|--------------------------|----------|----------------------------|---------------------------------------------------------------------------|
| `format`   | `OutputFormat`           | No       | `OutputFormat.PLAIN_TEXT`  | Output format (`PLAIN_TEXT` or `JSON`)                                    |
| `settings` | `ExporterCommonSettings` | No       | `ExporterCommonSettings()` | [Common exporter settings](sdk-configuration.md#common-exporter-settings) |

### Output Format

The [`OutputFormat`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.exporters/-output-format/index.html) enum
controls the format for Console and File exporters:

| Value                     | Description                                |
|---------------------------|--------------------------------------------|
| `OutputFormat.PLAIN_TEXT` | Human-readable plain text format (default) |
| `OutputFormat.JSON`       | OTLP JSON format for structured logging    |

Use JSON if you will analyze your traces programmatically; otherwise, use plain text (the default).

### Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.exporters.OutputFormat
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->

```kotlin
// Plain text output (default)
val sdk = configureOpenTelemetrySdk(ConsoleExporterConfig())

// JSON output
val jsonSdk = configureOpenTelemetrySdk(
    ConsoleExporterConfig(format = OutputFormat.JSON)
)

TracingManager.setSdk(sdk)
```

<!--- KNIT example-otel-exporters-07.kt -->

---

## File

Export traces to a file for offline analysis or log aggregation.

| Property   | Type                     | Required | Default                    | Description                                                               |
|------------|--------------------------|----------|----------------------------|---------------------------------------------------------------------------|
| `filepath` | `String`                 | Yes      | -                          | File path where traces will be written                                    |
| `append`   | `Boolean`                | Yes      | -                          | `true` to append to existing file, `false` to overwrite                   |
| `format`   | `OutputFormat`           | No       | `OutputFormat.PLAIN_TEXT`  | Output format (`PLAIN_TEXT` or `JSON`)                                    |
| `settings` | `ExporterCommonSettings` | No       | `ExporterCommonSettings()` | [Common exporter settings](sdk-configuration.md#common-exporter-settings) |

### Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.FileExporterConfig
import ai.jetbrains.tracy.core.exporters.OutputFormat
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->

```kotlin
// Plain text file
val sdk = configureOpenTelemetrySdk(
    FileExporterConfig(
        filepath = "/var/log/traces.log",
        append = true
    )
)

// JSON Lines file (one JSON object per line)
val jsonSdk = configureOpenTelemetrySdk(
    FileExporterConfig(
        filepath = "/var/log/traces.jsonl",
        append = true,
        format = OutputFormat.JSON
    )
)

TracingManager.setSdk(sdk)
```

<!--- KNIT example-otel-exporters-08.kt -->

See the full
example: [FileTracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/FileTracingExample.kt)
