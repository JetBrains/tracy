package ai.dev.kit.examples.backends

import ai.dev.kit.tracing.LangfuseConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.KotlinFlowTrace

@KotlinFlowTrace(name = "SimpleExample")
fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates how to use [LangfuseConfig] with [KotlinFlowTrace] to export traces to [Langfuse](https://langfuse.com).
 *
 * This example shows how:
 * - [LangfuseConfig] initializes the tracing backend for Langfuse.
 *
 * To run this example provide your Langfuse credentials either:
 * - Explicitly in code via [LangfuseConfig] constructor parameters, or
 * - Through the environment variables `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY`.
 *
 * Run the example. Spans will be exported to Langfuse.
 *
 * @see LangfuseConfig
 */
fun main() {
    TracingManager.setup(LangfuseConfig())
    printName("Bob")
    println("See trace details in Langfuse.")
    TracingManager.flushTraces()
}