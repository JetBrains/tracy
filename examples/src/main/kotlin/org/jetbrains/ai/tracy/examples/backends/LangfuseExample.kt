/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.backends

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.langfuse.LangfuseExporterConfig
import org.jetbrains.ai.tracy.core.instrumentation.Trace

@Trace(name = "SimpleExample")
fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates how to use [LangfuseExporterConfig] with [Trace] to export traces to [Langfuse](https://langfuse.com).
 *
 * This example shows how:
 * - [LangfuseExporterConfig] initializes the tracing backend for Langfuse.
 *
 * To run this example, provide your Langfuse credentials either:
 * - Explicitly in code via [LangfuseExporterConfig] constructor parameters, or
 * - Through the environment variables `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY`.
 *
 * Run the example. Spans will be exported to Langfuse.
 *
 * @see LangfuseExporterConfig
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(LangfuseExporterConfig()))
    printName("Bob")
    println("See trace details in Langfuse.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}