# Configuration & Sensitivity

Tracy offers flexible configuration options to control how traces are collected and exported, and how sensitive data is handled.

## TracingManager

The [`TracingManager`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/index.html?query=object%20TracingManager) is the central configuration point for the library.

| Method/Property | Description |
|-----------------|-------------|
| `setSdk(sdk: OpenTelemetrySdk)` | Sets the OpenTelemetry SDK instance |
| `isTracingEnabled: Boolean` | Runtime toggle for tracing (defaults to `IS_TRACY_ENABLED` env var) |
| `flushTraces(timeoutSeconds: Long = 5)` | Forces flushing of pending spans |
| `shutdownTracing(timeoutSeconds: Long = 5)` | Shuts down the OpenTelemetry SDK |
| `traceSensitiveContent()` | Enables capturing of inputs and outputs |
| `withCapturingPolicy(policy: ContentCapturePolicy)` | Sets custom content capture policy |

### Enabling/Disabling Tracing

You can enable or disable tracing at runtime:

````kotlin
TracingManager.isTracingEnabled = true // Enable
TracingManager.isTracingEnabled = false // Disable
````

By default, it checks the **`IS_TRACY_ENABLED`** environment variable.

### Setting the SDK

Before any spans can be exported, you must set an OpenTelemetry SDK instance. For detailed SDK configuration options, see [SDK Configuration](../otel-config/sdk-configuration.md).

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

Tracy supports multiple backends out of the box. For detailed configuration, see [Exporters](../otel-config/exporters.md).

- [**Langfuse**](../otel-config/exporters.md#langfuse): Dedicated LLM observability platform.
- [**Weave (Weights & Biases)**](../otel-config/exporters.md#weave-weights--biases): For experiment tracking and evaluation.
- [**OTLP HTTP**](../otel-config/exporters.md#otlp-http): Export to any OpenTelemetry-compliant backend (Jaeger, Grafana Tempo, etc.).
- [**Console**](../otel-config/exporters.md#console): Great for development and debugging.
- [**File**](../otel-config/exporters.md#file): Export traces to a local JSON or plain text file.

## Flushing and Shutdown

Since many exporters use batching to improve performance, it's important to flush remaining traces before the application exits using [`flushTraces()`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/flush-traces.html) or [`shutdownTracing()`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/shutdown-tracing.html):

```kotlin
// Flush pending spans (waits up to 5 seconds by default)
TracingManager.flushTraces()

// Or with custom timeout
TracingManager.flushTraces(timeoutSeconds = 10)

// For graceful shutdown (flushes and releases resources)
TracingManager.shutdownTracing()
```
