package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.langfuse.LangfuseExporterConfig
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.addLangfuseTagsToCurrentTrace
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.Trace

@Trace(name = "GreetUserTrace")
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
 * Demonstrates how to use [LangfuseExporterConfig] with [Trace]
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
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
