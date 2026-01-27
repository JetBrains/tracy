# Tracy

[Tracy](https://github.com/JetBrains/tracy/) is an open-source JetBrains Kotlin library that adds OpenTelemetry observability to JVM applications. It provides APIs that help define what needs to be traced at a high level, while hiding implementation details such as span structures and attribute names. The library also supports multiple OpenTelemetry backends out of the box.

<div class="grid cards" markdown>

-   :material-rocket-launch:{ .lg .middle } [**Get Started**](./get-started.md)

    ---

    Quick introduction to Tracy and how to set it up

-   :material-open-in-new:{ .lg .middle } [**OpenTelemetry**](./opentelemetry.md)

    ---

    Tracy is built on OpenTelemetry, the industry-standard framework for observability

</div>

## Features

<div class="grid cards" markdown>

-   :material-code-tags:{ .lg .middle } [**Tracing API**](./tracing)

    ---

    Learn how to instrument your code with Tracy's tracing primitives

-   :material-export:{ .lg .middle } [**OpenTelemetry Exporters**](./otel-exporters.md)

    ---

    Configure backends like Langfuse, Jaeger, and more

-   :material-robot:{ .lg .middle } [**LLM Tracing API**](#)

    ---

    Trace LLM calls with dedicated primitives for prompts, completions, and token usage

-   :material-water:{ .lg .middle } [**KCP**](./compiler-plugin/index.md)

    ---

    Kotlin Compiler Plugin that enables annotation-based tracing with zero boilerplate
</div>

## Troubleshooting

<div class="grid cards" markdown>

-   :material-bug:{ .lg .middle } [**Debugging**](./compiler-plugin/debugging.md)

    ---

    Tips for troubleshooting when tracing isn't working as expected

-   :material-alert-circle:{ .lg .middle } [**Limitations**](./limitations.md)

    ---

    Known limitations and recommended workarounds for context propagation, local functions, and more

</div>
