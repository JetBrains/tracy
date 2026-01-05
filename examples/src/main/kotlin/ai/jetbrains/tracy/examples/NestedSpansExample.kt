package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace

@KotlinFlowTrace(name = "ChildOperation")
fun childOperation(): String {
    println("Running child operation...")
    return "Child operation complete"
}

@KotlinFlowTrace(name = "ParentOperation")
fun parentOperation(): String {
    println("Starting parent operation...")
    val result = childOperation()
    println("Parent operation finished: $result")
    return "Parent complete"
}

/**
 * Demonstrates how nested tracing spans are automatically created
 * when one annotated function calls another.
 *
 * This example shows how:
 * - Each function annotated with [KotlinFlowTrace] generates its own tracing span.
 * - When [parentOperation] calls [childOperation], a **nested span structure** is produced,
 *   representing the parent-child relationship between operations.
 *
 * Running this example produces two spans in the trace output:
 * - A parent span named **ParentOperation**.
 * - A child span named **ChildOperation**, nested inside the parent.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    parentOperation()
    println("See trace details in the console.")
    TracingManager.flushTraces()
}