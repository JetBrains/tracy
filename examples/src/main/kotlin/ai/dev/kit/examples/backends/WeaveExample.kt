package ai.dev.kit.examples.backends

import ai.dev.kit.exporters.otlp.WeaveExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk

/**
 * Example of exporting tracing data to [W&B Weave](https://wandb.ai/site/weave) using [WeaveExporterConfig].
 *
 * This example demonstrates how to:
 * - [WeaveExporterConfig] initializes the tracing backend for Langfuse.
 *
 * To run this example, provide your Weave credentials either:
 * - Explicitly in code via [WeaveExporterConfig] constructor parameters, or
 * - Through the environment variables `WEAVE_API_KEY`, `WEAVE_ENTITY`, and `WEAVE_PROJECT_NAME`.
 *
 * Run the example. Spans and tags will be exported to [W&B Weave](https://wandb.ai/site/weave).
 *
 * @see WeaveExporterConfig
 */

fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(WeaveExporterConfig()))
    printName("Bob")
    println("See trace details in W&B.")
    TracingManager.flushTraces()
}