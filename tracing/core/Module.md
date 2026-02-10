# Module core

Core tracing infrastructure.

Provides the foundational components for OpenTelemetry-based tracing of AI applications:

- **TracingManager**: Central manager for configuring and controlling the OpenTelemetry SDK
- **Exporter Configurations**: Built-in support for Langfuse, W&B Weave, OTLP HTTP/gRPC, console, and file exporters
- **LLMTracingAdapter**: Base class for implementing provider-specific tracing adapters
- **Content Capture Policies**: Controls for capturing or redacting sensitive input/output content
- **HTTP Instrumentation**: OkHttp interceptor and protocol utilities for tracing HTTP calls
- **Annotation-Based Tracing**: `@Trace` annotation for declarative function tracing

This module is required by all other tracing modules and should be included as a dependency when using any AI client
instrumentation.

## Using in your project

To use the core module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.ai.tracy:tracy-core:$version")
}
```