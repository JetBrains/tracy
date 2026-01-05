package ai.jetbrains.tracy.examples.backends

import ai.jetbrains.tracy.core.exporters.otlp.LangfuseExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace

@KotlinFlowTrace(name = "SimpleExample")
fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates how to use [LangfuseExporterConfig] with [KotlinFlowTrace] to export traces to [Langfuse](https://langfuse.com).
 *
 * This example shows how:
 * - [LangfuseExporterConfig] initializes the tracing backend for Langfuse.
 *
 * To run this example provide your Langfuse credentials either:
 * - Explicitly in code via [LangfuseExporterConfig] constructor parameters, or
 * - Through the environment variables `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY`.
 *
 * Run the example. Spans will be exported to Langfuse.
 *
 * @see LangfuseExporterConfig
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(LangfuseExporterConfig()))
    printName("Bob")
    println("See trace details in Langfuse.")
    TracingManager.flushTraces()
}