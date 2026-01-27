package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.Trace

@Trace(name = "SimpleExample")
private fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates basic function tracing using Tracy.
 *
 * This example shows how:
 * - Initializing tracing with [TracingManager] and [ConsoleExporterConfig].
 * - Annotating a function with [Trace] to generate spans automatically.
 * - Traces are automatically flushed based on [ExporterCommonSettings][ai.jetbrains.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * Running this example creates a single span named **SimpleExample**
 * representing the execution of the [printName] function.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    printName("Bob")
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}