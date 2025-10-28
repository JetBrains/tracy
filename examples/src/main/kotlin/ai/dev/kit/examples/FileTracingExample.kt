package ai.dev.kit.examples

import ai.dev.kit.exporters.FileExporterConfig
import ai.dev.kit.exporters.OutputFormat
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.readText

@KotlinFlowTrace(name = "FileTracingExample")
private fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates basic function tracing using the AI Dev Kit.
 *
 * This example shows how:
 * - Initializing tracing with [TracingManager] and [FileExporterConfig].
 * - Annotating a function with [KotlinFlowTrace] to generate spans automatically.
 * - Call [TracingManager.flushTraces] before exiting to ensure all trace data is exported.
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
    TracingManager.flushTraces()

    val details = tempFile.readText()
    println("Tracing data from the file ${tempFile.absolutePathString()}:")
    println(details)
}