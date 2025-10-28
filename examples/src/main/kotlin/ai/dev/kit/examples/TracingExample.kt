package ai.dev.kit.examples

import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.KotlinFlowTrace

@KotlinFlowTrace(name = "SimpleExample")
private fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates basic function tracing using the AI Dev Kit.
 *
 * This example shows how:
 * - Initializing tracing with [TracingManager] and [ConsoleExporterConfig].
 * - Annotating a function with [KotlinFlowTrace] to generate spans automatically.
 * - Call [TracingManager.flushTraces] before exiting to ensure all trace data is exported.
 *
 * Running this example creates a single span named **SimpleExample**
 * representing the execution of the [printName] function.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    printName("Bob")
    println("See trace details in the console.")
    TracingManager.flushTraces()
}