# Supported Backends

OpenTelemetry backends collect, process, and store telemetry data – such as traces, metrics, and logs – providing visualization and analysis to help developers monitor performance, diagnose issues, and optimize systems.

The library supports:

1. [Langfuse](https://langfuse.com/)
2. [W&B Weave](https://wandb.ai/site/weave/)
3. Any other OpenTelemetry-compatible backend: Jaeger, Prometheus, etc.
4. File
5. Console

_Note: console and file exporters may export in JSON or plain text formats._

For backend configuration see:

1. `ai.dev.kit.exporters.LangfuseKt#createLangfuseExporter`
2. `ai.dev.kit.exporters.WeaveKt#createWeaveExporter`

LLM agent trace on Langfuse:

![Example of an agent race on Langfuse](./assets/img/supported-backends/langfuse-trace-example.jpg)


## Tracing with Koog

The library can be used together with Koog. The example to be added.