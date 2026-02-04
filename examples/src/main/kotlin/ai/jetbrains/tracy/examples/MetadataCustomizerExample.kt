package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.Trace
import ai.jetbrains.tracy.core.fluent.customizers.PlatformMethod
import ai.jetbrains.tracy.core.fluent.customizers.SpanMetadataCustomizer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Trace(metadataCustomizer = ExampleWithMetadataCustomizer::class)
fun greetUser(name: String): String {
    println("Hello, $name!")
    return "Greeting sent to $name"
}

object ExampleWithMetadataCustomizer : SpanMetadataCustomizer {
    override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String {
        return buildJsonObject {
            put("input_name", args.getOrNull(0)?.toString() ?: "unknown")
            put("method_name", method.name)
        }.toString()
    }

    override fun formatOutputAttribute(result: Any?): String {
        return buildJsonObject {
            put("output_message", result?.toString() ?: "null")
            put("status", "success")
        }.toString()
    }

    override fun resolveSpanName(method: PlatformMethod, args: Array<Any?>): String {
        val name = args.getOrNull(0)?.toString()?.replaceFirstChar { it.uppercase() } ?: "User"
        return "GreetSpan::$name"
    }
}

/**
 * Demonstrates how to use a custom [SpanMetadataCustomizer] with [Trace]
 * to enrich spans with additional metadata.
 *
 * This example shows how:
 * - You can provide a custom [SpanMetadataCustomizer] implementation to control span names and attributes.
 * - Input and output data can be formatted into structured JSON metadata for each traced function.
 * - The tracing plugin automatically invokes the metadata customizer at runtime for annotated methods.
 *
 * When you run this example, you will see a span named "GreetSpan::Alice" created for the `greetUser` function.
 * The span metadata includes custom JSON attributes such as
 * - `"input_name"` and `"method_name"` for the function parameters.
 * - `"output_message"` and `"status"` describing the function result.
 *
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    greetUser("Alice")
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
