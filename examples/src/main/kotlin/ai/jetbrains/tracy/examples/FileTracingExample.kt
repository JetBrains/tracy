package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.FileExporterConfig
import ai.jetbrains.tracy.core.exporters.OutputFormat
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.Trace
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.readText

@Trace(name = "FileTracingExample")
private fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates basic function tracing using Tracy.
 *
 * This example shows how:
 * - Initializing tracing with [TracingManager] and [FileExporterConfig].
 * - Annotating a function with [Trace] to generate spans automatically.
 * - Traces are automatically flushed based on [ExporterCommonSettings][ai.jetbrains.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * Running this example creates a single span named **FileTracingExample**
 * representing the execution of the [printName] function.
 */
fun main() {
    val tempFile = createTempFile()
    val config = FileExporterConfig(
        filepath = tempFile.absolutePathString(),
        append = false,
        format = OutputFormat.JSON,
    )
    TracingManager.setSdk(configureOpenTelemetrySdk(config))
    printName("Bob")
    println("See trace details read from the file in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()

    val details = tempFile.readText()
    println("Tracing data from the file ${tempFile.absolutePathString()}:")
    println(details)
}