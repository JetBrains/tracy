# Configuration & Sensitivity

Tracy offers flexible configuration options to control how traces are collected and exported, and how sensitive data is handled.

## TracingManager

The `TracingManager` is the central configuration point for the library.

### Enabling/Disabling Tracing

You can enable or disable tracing at runtime:

````kotlin
TracingManager.isTracingEnabled = true // Enable
TracingManager.isTracingEnabled = false // Disable
````

By default, it checks the `IS_TRACY_ENABLED` environment variable.

### Setting the SDK

Before any spans can be exported, you must set an OpenTelemetry SDK instance:

````kotlin
val sdk = configureOpenTelemetrySdk(ConsoleExporterConfig())
TracingManager.setSdk(sdk)
````

## Sensitive Content (Redaction)

According to OpenTelemetry [Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#full-buffered-content), capturing sensitive data like user messages or assistant replies should be **disabled by default**.

Tracy follows this convention and replaces sensitive content with the placeholder `"REDACTED"` by default.

### Enabling Content Capture

You can enable capturing of inputs and outputs in two ways:

#### 1. Environment Variables

Set the following environment variables:

````bash
TRACY_CAPTURE_INPUT=true
TRACY_CAPTURE_OUTPUT=true
````

#### 2. Programmatically

Use the helper method to enable both:

````kotlin
TracingManager.traceSensitiveContent()
````

Or configure the policy more granularly:

<!--- INCLUDE
import ai.jetbrains.tracy.core.tracing.policy.ContentCapturePolicy
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk

fun main() {
-->
```kotlin
TracingManager.withCapturingPolicy(
    ContentCapturePolicy(
        captureInputs = true,
        captureOutputs = false // Only capture inputs
    )
)
```
<!--- SUFFIX
}
-->
<!--- KNIT example-configuration-01.kt -->

## Exporters

Tracy supports multiple backends out of the box. For detailed configuration of each backend, please refer to the [Supported Backends](../supported-backends.md) page.

Common exporters include:

- **Console**: Great for development.
- **Langfuse**: Dedicated LLM observability.
- **Weave (Weights & Biases)**: For experiment tracking and evaluation.
- **File**: Export traces to a local JSON or plain text file.
- **OTLP**: Export to any OpenTelemetry-compliant backend (e.g., Jaeger, Honeycomb).

Example of configuring a File exporter:

<!--- INCLUDE
import ai.jetbrains.tracy.core.exporters.FileExporterConfig
import ai.jetbrains.tracy.core.exporters.OutputFormat
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
-->
```kotlin
val config = FileExporterConfig(
    filepath = "traces.json",
    append = true,
    format = OutputFormat.JSON
)
val sdk = configureOpenTelemetrySdk(config)
```
<!--- KNIT example-configuration-02.kt -->

## Flushing Traces

Since many exporters use batching to improve performance, it's important to flush remaining traces before the application exits:

```kotlin
TracingManager.flushTraces()
```
