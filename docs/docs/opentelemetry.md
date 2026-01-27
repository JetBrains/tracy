# OpenTelemetry

Tracy is built on [OpenTelemetry](https://opentelemetry.io/) (OTel), the industry-standard framework for observability. This means your traces are compatible with a wide ecosystem of backends and tools.

## Why OpenTelemetry?

**Vendor-neutral and open source.** OpenTelemetry is a [CNCF](https://www.cncf.io/) project backed by major cloud providers and observability vendors. You're not locked into any specific backend — switch between Jaeger, Zipkin, Datadog, or any other OTel-compatible tool without changing your instrumentation code.

**Unified observability.** OTel provides a single set of [APIs and SDKs](https://opentelemetry.io/docs/languages/) for traces, metrics, and logs. This consistency simplifies your observability stack and reduces the cognitive overhead of working with multiple libraries.

**Industry adoption.** OpenTelemetry is rapidly becoming the standard for observability. Most modern observability platforms support OTel natively, and the ecosystem of integrations continues to grow.

**Rich context propagation.** OTel handles distributed context propagation across service boundaries, making it easy to trace requests as they flow through microservices, message queues, and async workflows.

**Semantic conventions.** Standardized attribute names and span structures mean your telemetry data is consistent and interoperable. Tools can understand your data without custom configuration.

## Learn more

- [OpenTelemetry Docs](https://opentelemetry.io/docs/) — Official documentation and getting started guides
- [OpenTelemetry Java SDK](https://github.com/open-telemetry/opentelemetry-java) — The SDK that Tracy uses under the hood
- [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/) — Standardized naming for spans, attributes, and metrics
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) — A vendor-agnostic proxy for receiving, processing, and exporting telemetry
- [OTel Registry](https://opentelemetry.io/ecosystem/registry/) — Browse instrumentation libraries, exporters, and integrations
- [CNCF OpenTelemetry Project](https://www.cncf.io/projects/opentelemetry/) — Learn about the project's governance and community
