# OpenTelemetry Configuration and Exporters

## Overview
OpenTelemetry backends collect, process, and store telemetry data – such as traces, metrics, and logs – providing visualization and analysis to help developers monitor performance, diagnose issues, and optimize systems.

The library supports:

1. [Langfuse](https://langfuse.com/) — LLM observability platform
2. [W&B Weave](https://wandb.ai/site/weave/) — Weights & Biases tracing
3. [OTLP HTTP](#otlp-http-configuration) — Any OpenTelemetry-compatible backend (Jaeger, etc.)
4. [File](#file-configuration) — Export traces to a file
5. [Console](#console-configuration) — Export traces to console for debugging

## Quick Start

Create an SDK by providing the desired configuration, then initialize tracing with [`TracingManager.setSdk(sdk)`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/set-sdk.html):

<!--- INCLUDE
import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.exporters.FileExporterConfig
import ai.dev.kit.exporters.OutputFormat
import ai.dev.kit.exporters.otlp.LangfuseExporterConfig
import ai.dev.kit.exporters.otlp.WeaveExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

fun main() {
val myFile = createTempFile()
-->
<!--- SUFFIX
}
-->
```kotlin
// Langfuse
val langfuseSdk: OpenTelemetrySdk = configureOpenTelemetrySdk(LangfuseExporterConfig())

// Weave
val weaveSdk: OpenTelemetrySdk = configureOpenTelemetrySdk(WeaveExporterConfig())

// Console-Only
val consoleSdk: OpenTelemetrySdk = configureOpenTelemetrySdk(ConsoleExporterConfig())

// File (plain text and JSON formats supported)
val fileSdk: OpenTelemetrySdk = configureOpenTelemetrySdk(
    FileExporterConfig(
        filepath = myFile.absolutePathString(),
        append = true,
        format = OutputFormat.JSON, // default is OutputFormat.PLAIN_TEXT
    )
)

// Initialize tracing with chosen SDK
TracingManager.setSdk(langfuseSdk)
```
<!--- KNIT example-otel-exporters-01.kt -->

Once the SDK is configured and set, all instrumented clients and annotated functions will emit traces to the configured backend.


## Configuring the OpenTelemetry SDK

The [`configureOpenTelemetrySdk()`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/configure-open-telemetry-sdk.html) function initializes the OpenTelemetry SDK with the provided exporter configuration.

| Parameter | Type                                                                                                                   | Required | Default | Description |
|-----------|------------------------------------------------------------------------------------------------------------------------|----------|---------|-------------|
| `exporterConfig` | [`BaseExporterConfig`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.exporters/-base-exporter-config/index.html) | Yes | - | The exporter configuration (Langfuse, Weave, Console, File, or OTLP HTTP) |
| `additionalResource` | [`Resource`](https://opentelemetry.io/docs/specs/otel/overview/#resources)                                                                                                         | No | `service.name = "unknown-service"` | Additional OpenTelemetry resource attributes |

### Setting Service Name

You can customize the [service name](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/#service-name), [service version](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/#service-version), and other [resource attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/):
<!--- INCLUDE
import ai.dev.kit.exporters.otlp.LangfuseExporterConfig
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
val sdk = configureOpenTelemetrySdk(
    exporterConfig = LangfuseExporterConfig(),
    additionalResource = Resource.create(
        Attributes.of(
            AttributeKey.stringKey("service.name"), "my-ai-application",
            AttributeKey.stringKey("service.version"), "1.0.0"
        )
    )
)
```
<!--- KNIT example-otel-exporters-02.kt -->


## Common Exporter Settings

All exporter configurations accept an [`ExporterCommonSettings`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.exporters/-exporter-common-settings/index.html) object that controls common behavior:

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `traceToConsole` | `Boolean` | `false` | - | Additionally log spans to console for debugging |
| `flushIntervalMs` | `Long` | `5` | - | Delay between batch export attempts (in milliseconds) |
| `flushThreshold` | `Int` | `512` | - | Maximum number of spans per export batch |
| `flushOnShutdown` | `Boolean` | `true` | - | Flush pending spans when JVM shuts down |
| `maxNumberOfSpanAttributes` | `Int?` | `256` | `MAX_NUMBER_OF_SPAN_ATTRIBUTES` | Maximum number of attributes per span |
| `maxSpanAttributeValueLength` | `Int?` | `Int.MAX_VALUE` | `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` | Maximum length for attribute values |

### Example with Custom Settings

<!--- INCLUDE
import ai.dev.kit.exporters.ExporterCommonSettings
import ai.dev.kit.exporters.otlp.LangfuseExporterConfig
import ai.dev.kit.tracing.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
val sdk = configureOpenTelemetrySdk(
    LangfuseExporterConfig(
        settings = ExporterCommonSettings(
            traceToConsole = true,           // Also log to console
            flushIntervalMs = 10,             // Flush every 10 milliseconds
            flushThreshold = 256,            // Batch size of 256 spans
            flushOnShutdown = true,          // Flush on JVM shutdown
            maxNumberOfSpanAttributes = 512, // Allow more attributes
        )
    )
)
```
<!--- KNIT example-otel-exporters-03.kt -->

## Output Format

The [`OutputFormat`]({{ api_docs_url }}tracing/core/ai.jetbrains.tracy.core.exporters/-output-format/index.html) enum controls the format for Console and File exporters:

| Value | Description |
|-------|-------------|
| `OutputFormat.PLAIN_TEXT` | Human-readable plain text format (default) |
| `OutputFormat.JSON` | OTLP JSON format for structured logging |


---


## Langfuse Configuration

[Langfuse](https://langfuse.com/) is an open-source LLM engineering platform for observability, metrics, evaluations, and prompt management.

| Property | Type | Required | Default | Environment Variable | Description |
|----------|------|----------|---------|---------------------|-------------|
| `langfuseUrl` | `String?` | No | `https://cloud.langfuse.com` | `LANGFUSE_URL` | Langfuse server URL |
| `langfusePublicKey` | `String?` | Yes | - | `LANGFUSE_PUBLIC_KEY` | Langfuse public API key |
| `langfuseSecretKey` | `String?` | Yes | - | `LANGFUSE_SECRET_KEY` | Langfuse secret API key |
| `exporterTimeoutSeconds` | `Long` | No | `10` | - | Timeout for span export (seconds) |
| `settings` | `ExporterCommonSettings` | No | `ExporterCommonSettings()` | - | Common exporter settings |

### Langfuse Example

<!--- INCLUDE
import ai.dev.kit.exporters.ExporterCommonSettings
import ai.dev.kit.exporters.otlp.LangfuseExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

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

---


## Weave Configuration

[W&B Weave](https://wandb.ai/site/weave/) is the Weights & Biases platform for LLM application tracing and evaluation.

| Property | Type | Required | Default | Environment Variable | Description |
|----------|------|----------|---------|---------------------|-------------|
| `weaveUrl` | `String?` | No | `https://trace.wandb.ai` | `WEAVE_URL` | Weave OTLP endpoint URL |
| `weaveEntity` | `String?` | Yes | - | `WEAVE_ENTITY` | W&B entity (team/org) name |
| `weaveProjectName` | `String?` | Yes | - | `WEAVE_PROJECT_NAME` | W&B project name |
| `weaveApiKey` | `String?` | Yes | - | `WEAVE_API_KEY` | W&B API key |
| `exporterTimeoutSeconds` | `Long` | No | `10` | - | Timeout for span export (seconds) |
| `settings` | `ExporterCommonSettings` | No | `ExporterCommonSettings()` | - | Common exporter settings |

### Weave Example

<!--- INCLUDE
import ai.dev.kit.exporters.ExporterCommonSettings
import ai.dev.kit.exporters.otlp.WeaveExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
// Using environment variables
// (don't forget to set `WEAVE_URL`, `WEAVE_ENTITY`, `WEAVE_PROJECT_NAME`, `WEAVE_API_KEY`  env variables)
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


---


## OTLP HTTP Configuration

Export spans to any OTLP HTTP-compatible collector such as Jaeger, Grafana Tempo, or other OpenTelemetry backends.

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `url` | `String` | Yes | - | Base URL of the OTLP HTTP collector endpoint |
| `exporterTimeoutSeconds` | `Long` | No | `10` | Timeout for span export (seconds) |
| `settings` | `ExporterCommonSettings` | No | `ExporterCommonSettings()` | Common exporter settings |

The exporter automatically appends `/v1/traces` to the provided URL.

### Jaeger Example

<!--- INCLUDE
import ai.dev.kit.exporters.ExporterCommonSettings
import ai.dev.kit.exporters.otlp.OtlpHttpExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

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


---


## Console Configuration

Export traces to the console for local development and debugging.

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `format` | `OutputFormat` | No | `OutputFormat.PLAIN_TEXT` | Output format (`PLAIN_TEXT` or `JSON`) |
| `settings` | `ExporterCommonSettings` | No | `ExporterCommonSettings()` | Common exporter settings |

### Console Example

<!--- INCLUDE
import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.exporters.OutputFormat
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

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


## File Configuration

Export traces to a file for offline analysis or log aggregation.

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `filepath` | `String` | Yes | - | File path where traces will be written |
| `append` | `Boolean` | Yes | - | `true` to append to existing file, `false` to overwrite |
| `format` | `OutputFormat` | No | `OutputFormat.PLAIN_TEXT` | Output format (`PLAIN_TEXT` or `JSON`) |
| `settings` | `ExporterCommonSettings` | No | `ExporterCommonSettings()` | Common exporter settings |

### File Example

<!--- INCLUDE
import ai.dev.kit.exporters.FileExporterConfig
import ai.dev.kit.exporters.OutputFormat
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

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


---


## SDK lifecycle management

The [`TracingManager`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/index.html) object manages the OpenTelemetry SDK lifecycle:

| Method/Property | Description |
|-----------------|-------------|
| `setSdk(sdk: OpenTelemetrySdk)` | Sets the OpenTelemetry SDK instance |
| `isTracingEnabled: Boolean` | Runtime toggle for tracing (defaults to `IS_TRACY_ENABLED` env var) |
| `flushTraces(timeoutSeconds: Long = 5)` | Forces flushing of pending spans |
| `shutdownTracing(timeoutSeconds: Long = 5)` | Shuts down the OpenTelemetry SDK |
| `traceSensitiveContent()` | Enables capturing of inputs and outputs |
| `withCapturingPolicy(policy: ContentCapturePolicy)` | Sets custom content capture policy |

### Content Capture Policy
By default, Tracy **redacts sensitive content** (LLM inputs and outputs) in span attributes, showing `"REDACTED"` instead of actual values. This follows [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#full-buffered-content) guidance for handling potentially sensitive data.

| Property | Type | Default | Environment Variable | Description |
|----------|------|---------|---------------------|-------------|
| `captureInputs` | `Boolean` | `false` | `TRACY_CAPTURE_INPUT` | Whether to capture input content in spans |
| `captureOutputs` | `Boolean` | `false` | `TRACY_CAPTURE_OUTPUT` | Whether to capture output content in spans |

### Example

<!--- INCLUDE
import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
// Initialize
TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
TracingManager.isTracingEnabled = true

// Enable sensitive content tracing
// Option 1: Enable capture of all sensitive content (inputs and outputs)
TracingManager.traceSensitiveContent()

// Option 2: Fine-grained control with custom policy
TracingManager.withCapturingPolicy(
    ContentCapturePolicy(
        captureInputs = true,
        captureOutputs = false
    )
)

// ... your traced code ...

// Cleanup
TracingManager.flushTraces()
TracingManager.shutdownTracing()
```
<!--- KNIT example-otel-exporters-09.kt -->