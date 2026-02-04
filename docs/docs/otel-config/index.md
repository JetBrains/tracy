# OpenTelemetry Configuration

Tracy uses [OpenTelemetry](https://opentelemetry.io/) to export traces to various observability backends. This section
covers how to configure the SDK and choose where your traces are sent.

OpenTelemetry configuration in Tracy involves two main parts:

1. **[SDK Configuration](sdk-configuration.md)**: Initialize the OpenTelemetry SDK using [
   `configureOpenTelemetrySdk()`]({{ api_docs_url
   }}/tracing/core/ai.jetbrains.tracy.core/configure-open-telemetry-sdk.html), which sets up the trace pipeline with
   batching, resource attributes, and export settings.

2. **[Exporters](exporters.md)**: Choose where traces are sent — LLM-focused platforms (Langfuse, Weave), generic OTLP
   backends (Jaeger, Grafana Tempo), or local outputs (Console, File).