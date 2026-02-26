# SDK Configuration

This page explains how to configure the OpenTelemetry SDK for Tracy.

## Configuring the OpenTelemetry SDK

The [`configureOpenTelemetrySdk()`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.tracing/configure-open-telemetry-sdk.html) function initializes the OpenTelemetry SDK with the provided exporter configuration.

| Parameter | Type                                                                                                                   | Required | Default | Description |
|-----------|------------------------------------------------------------------------------------------------------------------------|----------|---------|-------------|
| `exporterConfig` | [`BaseExporterConfig`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.exporters/-base-exporter-config/index.html) | Yes | - | The exporter configuration (Langfuse, Weave, Console, File, or OTLP HTTP) |
| `additionalResource` | [`Resource`](https://opentelemetry.io/docs/specs/otel/overview/#resources)                                                                                                         | No | `service.name = "unknown-service"` | Additional OpenTelemetry resource attributes |

### Setting Service Name

You can customize the [service name](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/#service-name), [service version](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/#service-version), and other [resource attributes](https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/):
<!--- INCLUDE
import org.jetbrains.ai.tracy.core.exporters.langfuse.LangfuseExporterConfig
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
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
<!--- KNIT example-otel-exporters-01.kt -->


## Common Exporter Settings

All exporter configurations accept an [`ExporterCommonSettings`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.exporters/-exporter-common-settings/index.html) object that controls common behavior:

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
import org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings
import org.jetbrains.ai.tracy.core.exporters.langfuse.LangfuseExporterConfig
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk

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
            flushIntervalMs = 10,            // Flush every 10 milliseconds
            flushThreshold = 256,            // Batch size of 256 spans
            flushOnShutdown = true,          // Flush on JVM shutdown
            maxNumberOfSpanAttributes = 512, // Allow more attributes
        )
    )
)
```
<!--- KNIT example-otel-exporters-02.kt -->

## Using an Existing OpenTelemetry SDK

If your project already has an `OpenTelemetrySdk` configured, you can pass it directly to Tracy via
[`TracingManager.setSdk()`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core/-tracing-manager/index.html)
without calling `configureOpenTelemetrySdk()`:

```kotlin
val sdk: OpenTelemetrySdk = initializeOpenTelemetry() // your existing setup
TracingManager.setSdk(sdk)
```

Tracy respects the active OTel context, so any spans you create using your own tracer will automatically become
parents of Tracy spans as long as the context is active when Tracy-instrumented code runs.

!!! note
    When passing a custom SDK, you are responsible for its lifecycle — flushing, shutdown, and span processor
    configuration.

See the [OpenTelemetry Integration Example](https://github.com/JetBrains/tracy/tree/main/examples/src/main/kotlin/org/jetbrains/ai/tracy/examples/OpenTelemetryIntegrationExample.kt)
for a complete, runnable demonstration.
