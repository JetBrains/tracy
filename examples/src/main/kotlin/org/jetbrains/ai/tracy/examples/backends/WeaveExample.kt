/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.backends

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.otlp.WeaveExporterConfig

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
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(WeaveExporterConfig()))
    printName("Bob")
    println("See trace details in W&B.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}