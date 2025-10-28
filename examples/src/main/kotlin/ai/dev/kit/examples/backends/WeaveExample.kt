package ai.dev.kit.examples.backends

import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.WeaveConfig
import ai.dev.kit.tracing.configureOpenTelemetrySdk

/**
 * Example of exporting tracing data to [W&B Weave](https://wandb.ai/site/weave) using [WeaveConfig].
 *
 * This example demonstrates how to:
 * - [WeaveConfig] initializes the tracing backend for Langfuse.
 *
 * To run this example, provide your Weave credentials either:
 * - Explicitly in code via [WeaveConfig] constructor parameters, or
 * - Through the environment variables `WEAVE_API_KEY`, `WEAVE_ENTITY`, and `WEAVE_PROJECT_NAME`.
 *
 * Run the example. Spans and tags will be exported to [W&B Weave](https://wandb.ai/site/weave).
 *
 * @see WeaveConfig
 */

fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(WeaveConfig()))
    printName("Bob")
    println("See trace details in the console.")
    TracingManager.flushTraces()
}