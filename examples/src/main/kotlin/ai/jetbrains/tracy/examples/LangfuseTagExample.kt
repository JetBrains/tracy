package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.otlp.LangfuseExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.addLangfuseTagsToCurrentTrace
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace

@KotlinFlowTrace(name = "GreetUserTrace")
fun greetUser(name: String, isPremium: Boolean): String {
    println("Hello, $name!")
    if (isPremium) {
        addLangfuseTagsToCurrentTrace(listOf("user:premium"))
    } else {
        addLangfuseTagsToCurrentTrace(listOf("user:standard"))
    }
    return "Greeting sent to $name"
}

/**
 * Demonstrates how to use [LangfuseExporterConfig] with [KotlinFlowTrace]
 * to export traces to [Langfuse](https://langfuse.com) and enrich them with custom tags.
 *
 * This example shows how:
 * - [LangfuseExporterConfig] initializes the tracing backend for Langfuse.
 * - [addLangfuseTagsToCurrentTrace] dynamically attaches user-related tags to the span.
 *
 * To run this example provide your Langfuse credentials either:
 * - Explicitly in code via [LangfuseExporterConfig] constructor parameters, or
 * - Through the environment variables `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY`.
 *
 * Run the example. Spans and tags will be exported to Langfuse.
 *
 * @see LangfuseExporterConfig
 * @see addLangfuseTagsToCurrentTrace
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(LangfuseExporterConfig()))
    greetUser("Alice", isPremium = true)
    greetUser("Bob", isPremium = false)
    println("See trace details with tags in the console.")
    TracingManager.flushTraces()
}
