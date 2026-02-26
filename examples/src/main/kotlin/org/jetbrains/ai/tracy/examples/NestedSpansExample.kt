/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.core.instrumentation.Trace

@Trace(name = "ChildOperation")
fun childOperation(): String {
    println("Running child operation...")
    return "Child operation complete"
}

@Trace(name = "ParentOperation")
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
 * - Each function annotated with [Trace] generates its own tracing span.
 * - When [parentOperation] calls [childOperation], a **nested span structure** is produced,
 *   representing the parent-child relationship between operations.
 *
 * Running this example produces two spans in the trace output:
 * - A parent span named **ParentOperation**.
 * - A child span named **ChildOperation**, nested inside the parent.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    parentOperation()
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}