# Tracing API

Tracy provides a comprehensive tracing API designed to capture the execution flow of AI-powered applications. It
implements the [OpenTelemetry Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/),
ensuring that your traces are compatible with industry standards and various observability backends.

## Core Concepts

The Tracing API is divided into three main categories:

1. **LLM Client Autotracing**: Automatically capture spans for all calls made via supported LLM clients (OpenAI,
   Anthropic, Gemini, etc.).
2. **Function Tracing (Annotation-based)**: Use the `@Trace` annotation to trace any Kotlin function, capturing its
   inputs, outputs, and duration.
3. **Manual Tracing**: Manually create and manage spans using the `withSpan` function for fine-grained control or for
   use in Java.

## Key Components

- [**`TracingManager`**]({{ api_docs_url
  }}/tracing/core/ai.jetbrains.tracy.core/-tracing-manager/index.html): The
  central point for configuring and controlling tracing at runtime.
- **`instrument()`**: A function used to wrap LLM clients with tracing capabilities (_multiple overloads for different
  LLM clients, e.g., see [`instrument`]({{ api_docs_url
  }}/tracing/openai/ai.jetbrains.tracy.openai.clients/instrument.html) for
  the [`OpenAIClient`](https://javadoc.io/doc/com.openai/openai-java/4.5.0/com/openai/client/OpenAIClient.html)_).
- [**`@Trace`**]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.instrumentation/-trace/index.html): An
  annotation for automatic instrumentation of Kotlin functions.
- [**`withSpan`**]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.instrumentation.processor/with-span.html): A
  block-based API for manual span management.

See the following sections for more details:

- [Getting Started](../get-started.md)
- [Configuration & Sensitivity](configuration.md)
- [LLM Client Autotracing](autotracing.md)
- [Function Tracing with Annotations](annotations.md)
- [Manual Tracing](manual.md)
- [Advanced Topics (Context Propagation, Custom Tags)](advanced.md)
